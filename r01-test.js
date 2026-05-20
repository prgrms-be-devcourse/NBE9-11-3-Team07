import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const reservationSuccess = new Counter('reservation_success');
const reservationConflict = new Counter('reservation_conflict');
const reservationUnexpected = new Counter('reservation_unexpected');
const loginFailure = new Counter('login_failure');
const loginDuration = new Trend('login_duration_ms');
const reservationDuration = new Trend('reservation_duration_ms');

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';
const USER_COUNT = Number(__ENV.USER_COUNT ?? 10);
const PASSWORD = __ENV.PASSWORD ?? 'password123';
const TIME_SLOT_ID = Number(__ENV.TIME_SLOT_ID ?? 1);
const SCENARIO_LABEL = (__ENV.SCENARIO_LABEL ?? 'After (Watchdog 적용)').trim();
const AUTH_MODE = (__ENV.AUTH_MODE ?? 'login').toLowerCase();
const AUTH_TOKEN = __ENV.AUTH_TOKEN ?? '';
const ENABLE_STRICT_THRESHOLD = (__ENV.ENABLE_STRICT_THRESHOLD ?? 'false').toLowerCase() === 'true';

const httpFailedThreshold = ENABLE_STRICT_THRESHOLD ? 'rate<0.05' : 'rate<=1';

export const options = {
    scenarios: {
        r01_ttl_optimistic_lock: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: '60s',
        },
    },
    thresholds: {
        http_req_failed: [httpFailedThreshold],
    },
};

function safeJsonParse(raw) {
    if (!raw) {
        return null;
    }

    try {
        return JSON.parse(raw);
    } catch (_) {
        return null;
    }
}

function extractToken(body) {
    if (!body) {
        return null;
    }

    return body.token ?? body.data?.token ?? body.result?.token ?? null;
}

function loginAndGetToken(userId) {
    if (AUTH_MODE === 'token') {
        return AUTH_TOKEN || null;
    }

    if (AUTH_MODE === 'none') {
        return null;
    }

    const loginResponse = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email: `user${userId}@test.com`, password: PASSWORD }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { endpoint: 'login' },
        },
    );

    loginDuration.add(loginResponse.timings.duration);

    if (loginResponse.status !== 200) {
        loginFailure.add(1);
        console.error(`[VU ${userId}] 로그인 실패: status=${loginResponse.status}`);
        return null;
    }

    const body = safeJsonParse(loginResponse.body);
    const token = extractToken(body);

    if (!token) {
        loginFailure.add(1);
        console.error(`[VU ${userId}] 로그인 토큰 추출 실패`);
    }

    return token;
}

function buildHeaders(token) {
    const headers = { 'Content-Type': 'application/json' };

    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    return headers;
}

export default function () {
    const userId = __VU;
    const token = loginAndGetToken(userId);

    if (AUTH_MODE !== 'none' && !token) {
        return;
    }

    const reservationResponse = http.post(
        `${BASE_URL}/api/v1/reservations`,
        JSON.stringify({ timeSlotId: TIME_SLOT_ID, guestCount: 1 }),
        {
            headers: buildHeaders(token),
            tags: { endpoint: 'reservation' },
        },
    );

    reservationDuration.add(reservationResponse.timings.duration);

    const isExpected = check(reservationResponse, {
        '예약 성공/충돌 응답': (r) => [200, 201, 409, 423, 429].includes(r.status),
    });

    if (!isExpected) {
        reservationUnexpected.add(1);
        console.error(`[VU ${userId}] 예상 외 응답: status=${reservationResponse.status}`);
        return;
    }

    if (reservationResponse.status === 200 || reservationResponse.status === 201) {
        reservationSuccess.add(1);
        return;
    }

    reservationConflict.add(1);
}

export function handleSummary(data) {
    const success = data.metrics['reservation_success']?.values?.count ?? 0;
    const conflict = data.metrics['reservation_conflict']?.values?.count ?? 0;
    const unexpected = data.metrics['reservation_unexpected']?.values?.count ?? 0;
    const authFail = data.metrics['login_failure']?.values?.count ?? 0;

    const avgLogin = data.metrics['login_duration_ms']?.values?.avg?.toFixed(1) ?? '-';
    const avgReservation = data.metrics['reservation_duration_ms']?.values?.avg?.toFixed(1) ?? '-';
    const p95Reservation = data.metrics['reservation_duration_ms']?.values?.['p(95)']?.toFixed(1) ?? '-';

    const summary = `
========================================
 R-01 TTL 만료 낙관적 락 충돌 테스트
========================================
 시나리오: ${SCENARIO_LABEL}
 동시 요청 수(VU): ${USER_COUNT}
 timeSlotId: ${TIME_SLOT_ID}
 AUTH_MODE: ${AUTH_MODE}
----------------------------------------
 예약 성공(200/201): ${success}건
 예약 충돌(409/423/429): ${conflict}건
 로그인 실패: ${authFail}건
 예상 외 응답: ${unexpected}건
----------------------------------------
 로그인 평균: ${avgLogin} ms
 예약 평균: ${avgReservation} ms
 예약 p95: ${p95Reservation} ms
========================================
 [로그 확인] OptimisticLockException 카운트
 grep -c "OptimisticLockException" app.log
 또는
 grep -c "OPTIMISTIC_LOCK_FAIL" app.log
========================================
`;

    console.log(summary);

    return {
        stdout: summary,
        'r01_result.txt': summary,
    };
}