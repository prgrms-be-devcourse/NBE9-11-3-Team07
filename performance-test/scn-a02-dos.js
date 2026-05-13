import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 1,            // 악성 유저 1명이
    duration: '30s',   // 30초 동안
};

export default function () {
    const baseUrl = 'http://localhost:8080/api/v1';

    // 수정 API는 DB 락을 잡고 데이터를 쓰기 때문에 부하가 큽니다.
    const url = `${baseUrl}/my/reservations/res-123`;
    const payload = JSON.stringify({ guestCount: 3 });
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer MALICIOUS_USER_TOKEN'
        },
    };

    // 지연 시간 거의 없이 폭격 (초당 100번 이상 유도)
    const res = http.patch(url, payload, params);

    check(res, {
        // 성공 기준: 서버가 일정 횟수 이상부터는 429(Too Many Requests)를 뱉어야 함
        'Rate limit triggered (429)': (r) => r.status === 429,
        'Server is still alive (not 500)': (r) => r.status !== 500,
    });

    // sleep 없이 쏘거나 아주 미세하게 설정
    sleep(0.01);
}