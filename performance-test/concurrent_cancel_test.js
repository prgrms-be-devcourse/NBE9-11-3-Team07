import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 1,
    iterations: 1,
};

export default function () {
    const authToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIzNzBiMDM3Yy0zZjBlLTQ4M2UtYTNlOC0zMjRjOWExNTc3NzUiLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc3NjkxNjMwNiwiZXhwIjoxNzc2OTE4MTA2fQ._TgorL8T9SmDym9V0i06lZHABrbgRLP1LzAmueWtyaA";

    const params = {
        headers: {
            'Authorization': `Bearer ${authToken}`,
            'Content-Type': 'application/json',
        },
    };

    const reservationId = 'f42b7730-3eca-11f1-aaed-0242ac1a0002';
    const url = `http://localhost:8080/api/v1/my/reservations/${reservationId}/cancel`;
    const payload = JSON.stringify({ cancelReason: 'TEST' });

    // 50개 동시 취소 요청
    const requests = Array(50).fill(['POST', url, payload, params]);
    const responses = http.batch(requests);

    let successCount = 0;
    responses.forEach((res) => {
        console.log(`status: ${res.status}`);
        if (res.status === 200) successCount++;
    });

    check(successCount, {
        'Only one cancel should succeed': (c) => c === 1,
    });
}