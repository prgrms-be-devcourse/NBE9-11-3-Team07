import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 100,          // 인덱스 효율을 보려면 적당한 동시성이 필요합니다.
    duration: '1m',
    thresholds: {
        // 현정님의 Success Criteria: 50ms 미만 (Full Scan 방지)
        http_req_duration: ['p(95)<50'],
    },
};

export default function () {
    // 10만 건 중 무작위 유저와 날짜를 조합하여 조회 요청
    const userId = Math.floor(Math.random() * 100000) + 1;

    // 예약 목록 조회 API (주로 WHERE userId = ? ORDER BY created_at DESC 형태)
    const url = `http://localhost:8080/api/v1/my/reservations?userId=${userId}`;

    const res = http.get(url);

    check(res, {
        'status is 200': (r) => r.status === 200,
        // Full Scan이 발생하면 200ms를 훌쩍 넘깁니다.
        'index working': (r) => r.timings.duration < 50,
    });

    sleep(0.05);
}