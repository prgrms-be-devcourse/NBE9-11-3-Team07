import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '1m', target: 1000 }, // 1분 동안 1,000명까지 서서히 증가
        { duration: '3m', target: 5000 }, // 3분 동안 5,000명까지 폭발적 증가 (Peak 시점)
        { duration: '1m', target: 5000 }, // 1분 동안 5,000명 유지 (버티기)
        { duration: '1m', target: 0 },    // 마무리
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],   // 에러율 1% 미만 (현정님 기준)
        http_req_duration: ['p(95)<100'], // p95 응답속도 100ms 미만 (현정님 기준)
    },
};

export default function () {
    // 10만 명의 시딩 데이터 중 랜덤하게 유저 선택
    const userId = Math.floor(Math.random() * 100000) + 1;

    // 가장 가벼우면서도 인덱스를 타는 조회 API를 타겟으로 잡습니다.
    // (예: 내 예약 목록 조회)
    const url = `http://localhost:8080/api/v1/my/reservations?userId=${userId}`;

    const params = {
        headers: {
            'Content-Type': 'application/json',
            // 인증이 해결되면 여기에 'Authorization': 'Bearer ' + token 추가 예정
        },
    };

    const res = http.get(url, params);

    check(res, {
        'is status 200': (r) => r.status === 200,
    });

    // 5,000 TPS를 내기 위해 sleep을 아주 짧게 가져가거나 제거합니다.
    sleep(0.01);
}