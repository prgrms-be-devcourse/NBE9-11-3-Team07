import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const reservationSuccess = new Counter('reservation_success');
const reservationConflict = new Counter('reservation_conflict');
const reservationError = new Counter('reservation_error');
const loginDuration = new Trend('login_duration_ms');
const reservationDuration = new Trend('reservation_duration_ms');

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';
const USER_COUNT = Number(__ENV.USER_COUNT ?? 10);
const PASSWORD = __ENV.PASSWORD ?? 'password123';
const DEFAULT_TIME_SLOT_ID = Number(__ENV.DEFAULT_TIME_SLOT_ID ?? 1);
const STRICT_MODE = (__ENV.STRICT_MODE ?? 'false').toLowerCase() === 'true';
const STRICT_HTTP_THRESHOLD = STRICT_MODE ? 'rate<0.05' : 'rate<1';

const parsedTimeSlotId = Number(__ENV.TIME_SLOT_ID);
const resolvedTimeSlotId = Number.isFinite(parsedTimeSlotId) && parsedTimeSlotId > 0
    ? parsedTimeSlotId
    : DEFAULT_TIME_SLOT_ID;
const isTimeSlotFallback = !(__ENV.TIME_SLOT_ID && Number.isFinite(parsedTimeSlotId) && parsedTimeSlotId > 0);

export const options = {
    scenarios: {
        concurrent_users: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_failed: [STRICT_HTTP_THRESHOLD],
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

function extractToken(res) {
    const body = safeJsonParse(res.body);

    if (!body) {
        return null;
    }

    return body.token ?? body.data?.token ?? body.result?.token ?? null;
}

function getToken(userId) {
    const res = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email: `user${userId}@test.com`, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } },
    );

    loginDuration.add(res.timings.duration);

    const loginPassed = check(res, {
        '로그인 응답 코드 정상(200)': (r) => r.status === 200,
    });

    if (!loginPassed) {
        console.error(`[VU ${userId}] 로그인 실패: status=${res.status}, body=${res.body}`);
        return null;
    }

    const token = extractToken(res);

    if (!token) {
        console.error(`[VU ${userId}] 로그인 성공했지만 토큰 파싱 실패: body=${res.body}`);
    }

    return token;
}

export default function () {
    const userId = __VU;

    if (isTimeSlotFallback && userId === 1) {
        console.warn(
            `TIME_SLOT_ID가 없거나 잘못되어 DEFAULT_TIME_SLOT_ID(${DEFAULT_TIME_SLOT_ID})를 사용합니다. `
            + '권장: -e TIME_SLOT_ID=<실제 슬롯ID>',
        );
    }

    const token = getToken(userId);

    if (!token) {
        reservationError.add(1);
        return;
    }

    const res = http.post(
        `${BASE_URL}/api/v1/reservations`,
        JSON.stringify({ timeSlotId: resolvedTimeSlotId, guestCount: 1 }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
        },
    );

    reservationDuration.add(res.timings.duration);

    const passed = check(res, {
        '성공 또는 정상 충돌 처리': (r) => r.status === 200 || r.status === 409,
    });

    if (!passed) {
        console.error(`[VU ${userId}] 예상치 못한 응답: status=${res.status}, body=${res.body}`);
        reservationError.add(1);
        return;
    }

    if (res.status === 200) {
        reservationSuccess.add(1);
        console.log(`[VU ${userId}] 예약 성공`);
    }

    if (res.status === 409) {
        reservationConflict.add(1);
        const body = safeJsonParse(res.body);
        console.log(`[VU ${userId}] 예약 충돌/만석: ${body?.message ?? res.body}`);
    }

    sleep(0.1);
}

export function handleSummary(data) {
    const success = data.metrics['reservation_success']?.values?.count ?? 0;
    const conflict = data.metrics['reservation_conflict']?.values?.count ?? 0;
    const error = data.metrics['reservation_error']?.values?.count ?? 0;
    const avgLogin = data.metrics['login_duration_ms']?.values?.avg?.toFixed(1) ?? '-';
    const avgRes = data.metrics['reservation_duration_ms']?.values?.avg?.toFixed(1) ?? '-';
    const p95Res = data.metrics['reservation_duration_ms']?.values?.['p(95)']?.toFixed(1) ?? '-';

    const scenario = __ENV.SCENARIO === 'before' ? 'Before (Watchdog 없음)' : 'After  (Watchdog 적용)';
    const slotInfo = `사용 timeSlotId: ${resolvedTimeSlotId}${isTimeSlotFallback ? ' (fallback)' : ''}`;

    const strictInfo = `STRICT_MODE: ${STRICT_MODE ? 'ON(rate<0.05)' : 'OFF(rate<1)'}`;

    const summary = `
========================================
 R-01 결과 요약 [${scenario}]
========================================
 ${slotInfo}
 ${strictInfo}
 예약 성공  (200): ${success}건
 예약 충돌  (409): ${conflict}건
 예상 외 오류    : ${error}건
----------------------------------------
 로그인 평균 응답: ${avgLogin} ms
 예약  평균 응답: ${avgRes} ms
 예약  p95  응답: ${p95Res} ms
========================================
 [다음 단계] 아래 명령으로 OptimisticLockException 발생 횟수 확인
 grep -c "OPTIMISTIC_LOCK_FAIL" app.log
========================================
`;

    if (success === 0 && conflict === 0 && error > 0) {
        console.warn('로그인 계정/비밀번호 또는 권한(403)을 먼저 확인하세요. 필요 시 -e STRICT_MODE=true 로 엄격 검사하세요.');
    }

    console.log(summary);

    return {
        stdout: summary,
        'r01_result.txt': summary,
    };
}