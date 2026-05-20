import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 100,
    iterations: 100,
};

const BASE_URL = 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;

export default function () {
    const res = http.post(
        `${BASE_URL}/api/v1/reservations/attempts`,
        JSON.stringify({
            date: '2026-05-19',
            time: '21:00:00',
            guestCount: 1,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${TOKEN}`,
            },
        }
    );

    check(res, {
        'HTTP 200': (r) => r.status === 200,
        '3초 이내 응답': (r) => r.timings.duration < 3000,
    });
}