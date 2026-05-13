import http from 'k6/http';
import { check, sleep } from 'k6';

export default function () {
    const baseUrl = 'http://localhost:8080/api/v1';

    // 유저 A: 예약 취소자 (미리 예약된 ID: res-777)
    // 유저 B: 대기 타던 암표상 (매크로 유저)

    // 1. 유저 A가 예약을 취소함
    const cancelRes = http.post(`${baseUrl}/my/reservations/res-777/cancel`, null, {
        headers: { 'Authorization': 'Bearer TOKEN_USER_A' }
    });

    if (cancelRes.status === 200) {
        console.log("취소 완료! 0.1초 뒤 줍줍 시도...");

        // 2. 취소 직후 아주 짧은 간격(0.1초) 후 유저 B가 낚아채기 시도
        sleep(0.1);

        const snatchRes = http.post(`${baseUrl}/reservations`, JSON.stringify({
            timeSlotId: 'slot-uuid',
            guestCount: 2
        }), {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer TOKEN_USER_B'
            }
        });

        // 3. 결과 검증
        check(snatchRes, {
            // 방어 정책에 따라:
            // 1) 즉시 노출 안 됨 (404/403)
            // 2) 랜덤 지연 후 노출
            // 3) 대기열 맨 뒤로 보냄
            'Is snatch prevented?': (r) => r.status !== 201,
        });
    }
}