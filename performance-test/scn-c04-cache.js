import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 500 },  // 동일한 데이터에 부하 집중
        { duration: '1m', target: 2000 }, // 본격적인 캐시 히트 테스트
        { duration: '30s', target: 0 },
    ],
};

export default function () {
    // 캐시 효율을 보려면 10만 명을 다 쓰는 게 아니라,
    // 자주 조회되는 '인기 유저' 몇 명을 집중적으로 타격해야 합니다.
    const userId = Math.floor(Math.random() * 100) + 1; // 1~100번 유저만 반복 조회

    const res = http.get(`http://localhost:8080/api/v1/my/reservations?userId=${userId}`);

    check(res, {
        'status is 200': (r) => r.status === 200,
        // 캐시 히트 시 응답 속도는 10~20ms 이하여야 합니다.
        'is cache hit speed': (r) => r.timings.duration < 30,
    });

    sleep(0.01);
}