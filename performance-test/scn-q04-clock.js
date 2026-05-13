import http from 'k6/http';
import { check, sleep } from 'k6';

export default function () {
    const baseUrl = 'http://localhost:8080/api/v1';

    // 거의 동시에 두 대의 서버로 요청이 분산되도록 유도
    // 서버 A(정상 시간) vs 서버 B(1초 느린 시간)
    const res = http.post(`${baseUrl}/reservations`, JSON.stringify({
        userId: 777,
        timeSlotId: 'slot-uuid',
        guestCount: 2
    }), { headers: { 'Content-Type': 'application/json' } });

    check(res, {
        'status is 200/201': (r) => r.status === 200 || r.status === 201,
    });
}