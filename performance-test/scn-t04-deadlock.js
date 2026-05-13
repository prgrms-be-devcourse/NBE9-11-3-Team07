import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        creator: { // 예약 생성 팀
            executor: 'constant-vus',
            vus: 50,
            duration: '30s',
            exec: 'createReservation',
        },
        canceler: { // 예약 취소 팀
            executor: 'constant-vus',
            vus: 50,
            duration: '30s',
            exec: 'cancelReservation',
        },
    },
};

const baseUrl = 'http://localhost:8080/api/v1';

export function createReservation() {
    const res = http.post(`${baseUrl}/reservations`, JSON.stringify({
        timeSlotId: 'slot-123',
        guestCount: 2
    }), { headers: { 'Content-Type': 'application/json' } });

    check(res, { 'create status 201': (r) => r.status === 201 });
    sleep(0.1);
}

export function cancelReservation() {
    // 미리 생성된 예약 ID(res-456)를 대상으로 무한 취소 시도
    const res = http.post(`${baseUrl}/my/reservations/res-456/cancel`);

    check(res, { 'cancel status 200': (r) => r.status === 200 });
    sleep(0.1);
}