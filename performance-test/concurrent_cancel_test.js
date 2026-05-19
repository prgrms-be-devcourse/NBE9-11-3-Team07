import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 1,
    iterations: 1,
};

export default function () {
    const authToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiJmMmI5YWM5MC00OGM4LTQ3NWQtYTU2Ni05NDRlYmViODcxOWIiLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc3OTE2MjE3OSwiZXhwIjoxNzc5MTYzOTc5fQ.4n6bFSqKFMtgZIuXiS18J2cYkbTzFuvWKIDC01pSn6A";

    const params = {
        headers: {
            'Authorization': `Bearer ${authToken}`,
            'Content-Type': 'application/json',
        },
    };

    const reservationId = 'f42b7730-3eca-11f1-aaed-0242ac1a00ㅌ02';
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