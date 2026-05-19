import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const reservationSuccess  = new Counter('reservation_success');
const reservationConflict = new Counter('reservation_conflict');
const reservationError    = new Counter('reservation_error');
const loginDuration       = new Trend('login_duration_ms');
const reservationDuration = new Trend('reservation_duration_ms');

const BASE_URL     = 'http://localhost:8080';
const TIME_SLOT_ID = 'YOUR_TIME_SLOT_ID';

export const options = {
    scenarios: {
        concurrent_10: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate==0'],
    },
};

function getToken(userId) {
    const res = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email: `user${userId}@test.com`, password: 'password123' }),
        { headers: { 'Content-Type': 'application/json' } },
    );

    loginDuration.add(res.timings.duration);

    check(res, { '로그인 성공': (r) => r.status === 200 });

    return res.json('token');
}

export default function () {
    const userId = __VU;
    const token  = getToken(userId);

    if (!token) {
        console.error(`[VU ${userId}] 로그인 실패 → 예약 요청 스킵`);
        reservationError.add(1);
        return;
    }

    const res = http.post(
        `${BASE_URL}/api/v1/reservations`,
        JSON.stringify({ timeSlotId: TIME_SLOT_ID, guestCount: 1 }),
        { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } },
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
    } else if (res.status === 409) {
        reservationConflict.add(1);
        console.log(`[VU ${userId}] 예약 충돌/만석: ${res.json('message') ?? res.body}`);
    }
}

export function handleSummary(data) {
    const success  = data.metrics['reservation_success']?.values?.count  ?? 0;
    const conflict = data.metrics['reservation_conflict']?.values?.count ?? 0;
    const error    = data.metrics['reservation_error']?.values?.count    ?? 0;
    const avgLogin = data.metrics['login_duration_ms']?.values?.avg?.toFixed(1) ?? '-';
    const avgRes   = data.metrics['reservation_duration_ms']?.values?.avg?.toFixed(1) ?? '-';
    const p95Res   = data.metrics['reservation_duration_ms']?.values?.['p(95)']?.toFixed(1) ?? '-';

    const scenario = __ENV.SCENARIO === 'before' ? 'Before (Watchdog 없음)' : 'After  (Watchdog 적용)';

    const summary = `
========================================
 R-01 결과 요약 [${scenario}]
========================================
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

    console.log(summary);

    return {
        stdout: summary,
        'r01_result.txt': summary,
    };
}