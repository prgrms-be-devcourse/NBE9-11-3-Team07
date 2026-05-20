import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const errorRate = new Rate('error_rate');
const successCount = new Counter('success_count');

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';
const TARGET_API = __ENV.TARGET_API ?? '/api/v1/holidays';
const SCENARIO_LABEL = __ENV.SCENARIO_LABEL ?? 'Sentinel Test';
const VUS = Number(__ENV.VUS ?? 50);

export const options = {
    scenarios: {
        constant_load: {
            executor: 'constant-vus',
            vus: VUS,
            duration: '2m',
        },
    },
    thresholds: {
        'http_req_failed': ['rate<=1.0'],
    },
};

export default function () {
    const res = http.get(`${BASE_URL}${TARGET_API}`, {
        tags: { scenario: SCENARIO_LABEL }
    });

    const isSuccess = check(res, {
        'status is 200': (r) => r.status === 200,
    });

    if (isSuccess) {
        successCount.add(1);
    } else {
        errorRate.add(1);
    }

    sleep(0.05);
}

export function handleSummary(data) {
    const totalReqs = data.metrics.http_reqs.values.count;
    const tps = data.metrics.http_reqs.values.rate.toFixed(2);
    const errors = data.metrics.error_rate ? (data.metrics.error_rate.values.rate * 100).toFixed(2) : 0;

    const summary = `
========================================
 S-03 Redis Failover 테스트 결과 요약
========================================
 시나리오: ${SCENARIO_LABEL}
 총 요청 수: ${totalReqs}
 평균 TPS: ${tps} req/s
 에러율: ${errors}%
========================================
`;
    console.log(summary);
    return { stdout: summary };
}