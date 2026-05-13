import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    // 인스턴스 1대일 때와 3대일 때 동일한 VU로 테스트하여 비교합니다.
    vus: 1000,
    duration: '2m',
    thresholds: {
        http_req_duration: ['p(95)<200'], // 서버가 늘어날수록 이 지표가 개선되는지 확인
    },
};

export default function () {
    const userId = Math.floor(Math.random() * 100000) + 1;

    // 부하 분산(Load Balancing)이 잘 되는지 확인하기 위해 공통 엔드포인트 타격
    const res = http.get(`http://localhost:8080/api/v1/my/reservations?userId=${userId}`);

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(0.05);
}