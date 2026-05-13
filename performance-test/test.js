import http from 'k6/http';
import { check, sleep } from 'k6';

// k6가 잘 동작하는지 확인용 테스트 케이스
// 1. 테스트 설정 (어떻게 쏠 것인가?)
export const options = {
    vus: 10,          // 가상 유저 10명
    duration: '10s',  // 10초 동안만 짧게 테스트
};

// 2. 메인 로직 (무엇을 쏠 것인가?)
export default function () {
    // 일단 서버가 살아있는지 확인하기 위해 메인 페이지를 찌릅니다.
    // const res = http.get('http://localhost:8080/api/v1/auth/google'); // 1번 API
    const res = http.get('http://localhost:8080/actuator/health'); // auth 없이 테스트

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(1);
}