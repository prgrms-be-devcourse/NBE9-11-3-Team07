import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 100,        // 100명의 유저가
    iterations: 100, // 딱 한 번씩만 시도
};

export default function () {
    const baseUrl = 'http://localhost:8080/api/v1';

    // 모든 VU가 최대한 동시에 쏘게 하기 위해 앞단에서 아주 잠깐 대기할 수 있습니다.
    // k6는 기본적으로 VU를 동시에 출발시키므로 바로 쏴도 무방합니다.

    const payload = JSON.stringify({
        timeSlotId: 'single-seat-slot-uuid', // 좌석이 1개 남은 슬롯 ID
        guestCount: 1
    });

    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    // [핵심] 동시에 POST 요청 투하
    const res = http.post(`${baseUrl}/reservations`, payload, params);

    check(res, {
        'Success (201) or Race Condition Fail (409/429)': (r) =>
            r.status === 201 || r.status === 409 || r.status === 429,
    });
}

/*
이 테스트는 k6 로그보다 DB 결과가 100배 더 중요합니다. 테스트 종료 후 DB를 열어 다음을 확인하세요.

1. 예약 테이블(Reservations): 해당 timeSlotId로 생성된 예약 레코드가 반드시 딱 1개여야 합니다. (2개 이상이면 분산 락 실패!)
2. 재고 테이블(Inventory/TimeSlot): 남은 좌석 수가 0인지 확인하세요. -1이 되어 있다면 원자성(Atomicity)이 깨진 겁니다.
3. HTTP 응답 분포:
    - 201 Created: 딱 1건
    - 409 Conflict 또는 423 Locked: 나머지 99건
*/