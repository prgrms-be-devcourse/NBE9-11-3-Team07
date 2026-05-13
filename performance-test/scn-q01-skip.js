import http from 'k6/http';
import { sleep, check } from 'k6';

export default function () {
    const baseUrl = 'http://localhost:8080/api/v1';

    // 1. 사전 조건: DB나 Redis에 재고를 3개로 설정하는 API가 있다면 호출 (없으면 수동 설정)

    // 2. [1번 팀 - 4명] 먼저 진입
    const res1 = http.post(`${baseUrl}/reservations`, JSON.stringify({
        userId: 101,
        timeSlotId: 'target-slot-uuid',
        guestCount: 4 // 재고(3개)보다 많음
    }), { headers: { 'Content-Type': 'application/json' } });

    console.log(`Team 1 (4명) Status: ${res1.status}`); // 아마 대기열 진입(202) 혹은 대기 상태

    sleep(1); // 1초 뒤에 2번 팀 도착

    // 3. [2번 팀 - 2명] 진입
    const res2 = http.post(`${baseUrl}/reservations`, JSON.stringify({
        userId: 102,
        timeSlotId: 'target-slot-uuid',
        guestCount: 2 // 재고(3개)보다 적음
    }), { headers: { 'Content-Type': 'application/json' } });

    // 4. 결과 검증 (현정님의 Success Criteria)
    check(res2, {
        'Team 2 should succeed (Skip)': (r) => r.status === 201 || r.status === 200,
    });
}