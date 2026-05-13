import http from 'k6/http';
import { sleep, check } from 'k6';

export default function () {
    const baseUrl = 'http://localhost:8080/api/v1';

    // 1. 유저가 대기열에 진입 (번호표 수령)
    const res = http.post(`${baseUrl}/reservations`, JSON.stringify({
        userId: 999,
        timeSlotId: 'slot-uuid',
        guestCount: 2
    }), {
        headers: { 'Content-Type': 'application/json' },
        // k6에서 타임아웃을 짧게 주어 연결을 강제로 끊는 상황 재현
        timeout: '1ms'
    });

    // 연결이 끊겼으므로 에러가 날 것이지만, 서버에는 요청이 도달한 상태입니다.
    console.log("유저가 대기열 진입 직후 앱을 강제 종료했습니다.");

    // 이제 사람이 직접(또는 별도 쿼리로) Redis 상태를 확인해야 합니다.
}