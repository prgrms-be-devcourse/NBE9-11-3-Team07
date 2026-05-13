import http from 'k6/http';
import { sleep, check } from 'k6';

export default function () {
    const baseUrl = 'http://localhost:8080/api/v1';

    // 유저 A: 예약 수정 시도 (4명 -> 2명)
    // 서버 코드에 의도적인 1초 지연(Thread.sleep)이 있다고 가정하고 시뮬레이션
    const updatePayload = JSON.stringify({ guestCount: 2 });

    // 유저 B: 그 틈을 타서 남은 자리를 뺏으려는 공격자
    const attackPayload = JSON.stringify({ timeSlotId: 'slot-uuid', guestCount: 3 });

    // 1. 유저 A가 수정을 시작함 (서버 내부 로직 진행 중...)
    const resA = http.patch(`${baseUrl}/my/reservations/res-123`, updatePayload, {
        headers: { 'Content-Type': 'application/json' },
    });

    // 2. 거의 동시에(또는 0.1초 뒤) 유저 B가 공격
    sleep(0.1);
    const resB = http.post(`${baseUrl}/reservations`, attackPayload, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(resA, {
        'User A update success or rollback': (r) => r.status === 200 || r.status === 409,
    });
}