import http from 'k6/http';
import { check } from 'k6';

export default function () {
    const baseUrl = 'http://localhost:8080/api/v1';

    // 1. 현재 Redis 재고 상태 확인 (테스트 전)
    // 2. 예약 시도 (서버 내부에서 DB 에러 발생 유도)
    const res = http.post(`${baseUrl}/reservations`, JSON.stringify({
        timeSlotId: 'target-slot-uuid',
        guestCount: 2
    }), { headers: { 'Content-Type': 'application/json' } });

    // 3. 서버는 500 에러를 뱉어야 함
    check(res, {
        'DB Error Occurred (500)': (r) => r.status === 500,
    });

    // 4. 이제 사람이 직접 Redis를 확인해야 합니다.
    // Redis의 재고 카운트가 예약 시도 전과 동일하게 복구(INCR)되어 있는지가 핵심!
}