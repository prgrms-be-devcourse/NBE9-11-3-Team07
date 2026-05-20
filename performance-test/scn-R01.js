import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const reservationSuccess = new Counter('reservation_success');
const reservationConflict = new Counter('reservation_conflict');
const reservationDuration = new Trend('reservation_duration_ms');

const TOKENS = JSON.parse(open('./tokens.json'));
const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';
const TIME_SLOT_ID = __ENV.TIME_SLOT_ID ?? '1';
const SCENARIO_LABEL = __ENV.SCENARIO_LABEL ?? 'After (Watchdog 적용)';

export const options = {
    scenarios: {
        r01_ttl_test: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 1,
            maxDuration: '60s',
        },
    },
};

export default function () {
    const token = TOKENS[__VU % TOKENS.length];

    const res = http.post(
        `${BASE_URL}/api/v1/reservations/attempts`,
        JSON.stringify({
            date: '2026-05-18',
            time: '17:00:00',
            guestCount: 1
        }),
        {
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
            }
        }
    );

    reservationDuration.add(res.timings.duration);

    const body = JSON.parse(res.body);

    check(res, {
        '정상 응답': (r) => body.resultCode === '202' || body.resultCode === '409-1',
    });

    if (body.resultCode === '202') {
        reservationSuccess.add(1);
    } else {
        reservationConflict.add(1);
    }
}

export function handleSummary(data) {
    const success = data.metrics['reservation_success']?.values?.count ?? 0;
    const conflict = data.metrics['reservation_conflict']?.values?.count ?? 0;
    const avgReservation = data.metrics['reservation_duration_ms']?.values?.avg?.toFixed(1) ?? '-';
    const p95Reservation = data.metrics['reservation_duration_ms']?.values?.['p(95)']?.toFixed(1) ?? '-';

    const summary = `
========================================
 R-01 TTL 만료 낙관적 락 충돌 테스트
========================================
 시나리오: ${SCENARIO_LABEL}
 동시 요청 수(VU): 10
----------------------------------------
 예약 성공(202): ${success}건
 예약 충돌(409): ${conflict}건
----------------------------------------
 예약 평균: ${avgReservation} ms
 예약 p95: ${p95Reservation} ms
========================================
`;

    console.log(summary);

    return {
        stdout: summary,
        'r01_result.txt': summary,
    };
}