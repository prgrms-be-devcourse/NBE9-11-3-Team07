import http from 'k6/http';
import { sleep, check } from 'k6';

export default function () {
    const baseUrl = 'http://localhost:8080/api/v1';

    // 1. 로그인하여 수명이 짧은(예: 1분) 토큰을 받음
    const loginRes = http.post(`${baseUrl}/auth/login-mock`, { userId: 'user_q02' });
    const token = loginRes.json('accessToken');

    // 2. 대기열 진입 요청
    const waitRes = http.post(`${baseUrl}/reservations`, JSON.stringify({
        timeSlotId: 'slot-uuid',
        guestCount: 2
    }), { headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' } });

    console.log("대기열 진입 성공, 토큰 만료를 기다립니다...");

    // 3. 토큰이 만료될 때까지 대기 (설정한 만료 시간보다 길게 sleep)
    // 예: 토큰 만료가 1분이라면 70초 대기
    sleep(70);

    // 4. 이제 내 차례가 되어 최종 예약 확정 API를 찌름
    const finalRes = http.patch(`${baseUrl}/my/reservations/confirm`, JSON.stringify({
        reservationId: 'res-uuid'
    }), { headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' } });

    // 5. 결과 검증
    check(finalRes, {
        // 이상적인 케이스: 대기열 진입 시점에 이미 검증됐으므로 통과시켜주거나, Refresh Token으로 자동 갱신됨
        'Should handle expired token gracefully': (r) => r.status === 200 || r.status === 201,
        // 나쁜 케이스: 유저를 다시 쫓아냄
        'Is user kicked out?': (r) => r.status === 401,
    });
}