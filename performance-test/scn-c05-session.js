import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 50,          // 적은 인원으로도 정합성 체크는 충분합니다.
    duration: '1m',
};

export default function () {
    // 1. 로그인 API 호출 (토큰 발급 과정 재현)
    // 실제로는 현정님 프로젝트의 OAuth2/JWT 발급 경로를 사용해야 합니다.
    const loginRes = http.post('http://localhost:8080/api/v1/auth/login-mock', {
        userId: 'user1'
    });

    const token = loginRes.json('accessToken');

    const params = {
        headers: {
            'Authorization': `Bearer ${token}`,
        },
    };

    // 2. 인증이 필요한 API를 연속으로 타격
    // 로드밸런서(Nginx 등)가 요청을 서버 A, B, C로 번갈아 보낼 때
    // 어느 한 곳이라도 401(Unauthorized)을 뱉으면 실패입니다.
    for (let i = 0; i < 5; i++) {
        const res = http.get('http://localhost:8080/api/v1/auth/me', params);

        check(res, {
            'is status 200 (Authenticated)': (r) => r.status === 200,
            'no 401 error': (r) => r.status !== 401,
        });

        sleep(0.1);
    }
}