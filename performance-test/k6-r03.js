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
            date: '2026-05-20',
            time: '17:00:00',
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
        'HTTP 응답 받음': (r) => r.status >= 200 && r.status < 500,
        '1초 이내 응답': (r) => r.timings.duration < 1000,
    });
}