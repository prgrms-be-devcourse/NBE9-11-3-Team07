import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 1,           // 유저 1명이
    iterations: 50,   // 50번을 광클하는 상황
};

export default function () {
    const baseUrl = 'http://localhost:8080/api/v1';

    // 현정님의 시나리오대로 '유저 토큰 1개'를 사용하여 루프 없이 50개 발사
    // k6의 http.batch를 쓰면 거의 동시에 나갑니다.

    const url = `${baseUrl}/reservations`;
    const payload = JSON.stringify({
        timeSlotId: 'target-slot-uuid',
        guestCount: 2
    });
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer YOUR_USER_TOKEN_HERE',
            // 멱등성 키를 헤더에 포함한다면 추가 (예: 'X-Idempotency-Key': 'unique-key-123')
        },
    };

    // 50개의 요청을 동시에 묶어서 발사!
    const responses = http.batch([
        ['POST', url, payload, params],
        ['POST', url, payload, params],
        // ... 실제로는 반복문으로 배열을 만들어 넣는 것이 좋습니다.
    ]);

    // 결과 분석
    let successCount = 0;
    responses.forEach((res) => {
        if (res.status === 201) successCount++;
    });

    check(successCount, {
        'Only one request should succeed': (c) => c === 1,
    });
}