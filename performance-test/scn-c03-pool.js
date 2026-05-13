import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    // 순간적으로 확 몰아쳐서 커넥션 풀을 고갈시킵니다.
    stages: [
        { duration: '30s', target: 2000 }, // 빠르게 2,000명 투입
        { duration: '1m', target: 2000 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        // Connection Timeout 발생 시 500 에러나 특정 에러 문구가 뜰 수 있음
        http_req_failed: ['rate<0.05'],
    },
};

export default function () {
    const userId = Math.floor(Math.random() * 100000) + 1;

    // DB 부하를 주기 위해 단순 조회가 아닌,
    // 정렬이나 조건이 들어간 쿼리를 타겟으로 잡으면 더 좋습니다.
    const res = http.get(`http://localhost:8080/api/v1/my/reservations?userId=${userId}`);

    check(res, {
        'status is 200': (r) => r.status === 200,
        // 커넥션을 획득하는 데 실패하면 응답 속도가 급격히 늘어납니다.
        'is slowed by pool': (r) => r.timings.duration > 1000,
    });

    // 지연 시간을 거의 주지 않고 계속 DB를 찌릅니다.
    sleep(0.01);
}