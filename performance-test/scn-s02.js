import http from 'k6/http';
import { check, sleep } from 'k6';

const TOKENS = JSON.parse(open('./tokens.json'));

export const options = {
    vus: 100,
    iterations: 100,
};

export default function () {
    const token = TOKENS[__VU % TOKENS.length];

    const res = http.post(
        'http://localhost:8080/api/v1/reservations/attempts',
        JSON.stringify({
            date: '2026-05-18',
            time: '17:00:00',
            guestCount: 2
        }),
        {
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
            }
        }
    );

    check(res, {
        '대기열 진입 성공 (202)': (r) => {
            const body = JSON.parse(r.body);
            return body.resultCode === '202';
        },
        '중복 차단 (409)': (r) => {
            const body = JSON.parse(r.body);
            return body.resultCode === '409-1';
        },
    });

    sleep(0.5);
}