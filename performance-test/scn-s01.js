import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 100,
    duration: '2m',
};

const TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiJmMmI5YWM5MC00OGM4LTQ3NWQtYTU2Ni05NDRlYmViODcxOWIiLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc3OTE2NjAyNywiZXhwIjoxNzc5MTY3ODI3fQ.Td54E9QvhLwlXy5YWarr9hPg2BZOOCssCtB7XBHDYt4';

export default function () {
    const res = http.post(
        'http://localhost:8080/api/v1/reservations/attempts',
        JSON.stringify({
            date: '2026-05-18',
            time: '17:00:00',
            guestCount: 2
        }),
        {
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
                'Content-Type': 'application/json',
            }
        }
    );

    check(res, {
        '정상 응답': (r) => r.status === 202 || r.status === 409,
    });

    sleep(0.5);
}