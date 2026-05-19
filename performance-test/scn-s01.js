import http from 'k6/http';
import { check, sleep } from 'k6';

const TOKENS = JSON.parse(open('./tokens.json'));

const TIME_SLOTS = [
    { date: '2026-05-19', time: '17:00:00' },
    { date: '2026-05-19', time: '18:00:00' },
    { date: '2026-05-19', time: '19:00:00' },
    { date: '2026-05-19', time: '20:00:00' },
    { date: '2026-05-19', time: '21:00:00' },
    { date: '2026-05-20', time: '17:00:00' },
    { date: '2026-05-20', time: '18:00:00' },
    { date: '2026-05-20', time: '19:00:00' },
    { date: '2026-05-20', time: '20:00:00' },
    { date: '2026-05-20', time: '21:00:00' },
];

export const options = {
    vus: 100,
    duration: '2m',
};

export default function () {
    const token = TOKENS[__VU % TOKENS.length];
    const slot = { date: '2026-05-19', time: '17:00:00' };

    const res = http.post(
        'http://localhost:8080/api/v1/reservations/attempts',
        JSON.stringify({
            date: slot.date,
            time: slot.time,
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
        '정상 응답': (r) => {
            const body = JSON.parse(r.body);
            return body.resultCode === '202' || body.resultCode === '409-1';
        },
    });

    sleep(0.5);
}