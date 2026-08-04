import http from 'k6/http';
import { check, group, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'https://twin.tommy-huang.dev').replace(/\/$/, '');
const profile = (__ENV.PROFILE || 'baseline').toLowerCase();

const profiles = {
  baseline: {
    executor: 'ramping-vus',
    startVUs: 1,
    stages: [
      { duration: '15s', target: 5 },
      { duration: '30s', target: 5 },
      { duration: '15s', target: 10 },
      { duration: '30s', target: 10 },
      { duration: '10s', target: 0 },
    ],
    gracefulRampDown: '10s',
  },
  breakpoint: {
    executor: 'ramping-vus',
    startVUs: 1,
    stages: [
      { duration: '30s', target: 200 },
      { duration: '30s', target: 200 },
      { duration: '30s', target: 500 },
      { duration: '30s', target: 500 },
      { duration: '30s', target: 1000 },
      { duration: '30s', target: 1000 },
      { duration: '30s', target: 2000 },
      { duration: '30s', target: 2000 },
      { duration: '60s', target: 5000 },
      { duration: '30s', target: 5000 },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '10s',
  },
};

if (!profiles[profile]) {
  throw new Error(`Unsupported PROFILE: ${profile}`);
}

const breakpoint = profile === 'breakpoint';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    readonly: profiles[profile],
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: breakpoint
      ? [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' }]
      : [
          { threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '10s' },
          'rate<0.01',
        ],
    http_req_duration: breakpoint
      ? [
          { threshold: 'p(95)<3000', abortOnFail: true, delayAbortEval: '15s' },
          'p(99)<5000',
        ]
      : ['p(95)<1500', 'p(99)<3000'],
    'http_req_duration{name:home}': ['p(95)<1500'],
    'http_req_duration{name:wsinfo}': ['p(95)<1500'],
  },
};

export default function () {
  group('TWIN readonly', () => {
    const endpoints = [
      { name: 'home', path: '/' },
      { name: 'wsinfo', path: '/ws/info' },
    ];

    for (const endpoint of endpoints) {
      const response = http.get(`${baseUrl}${endpoint.path}`, {
        tags: {
          target: 'TWIN',
          name: endpoint.name,
          endpoint: endpoint.path,
        },
        timeout: '10s',
      });

      check(response, {
        [`TWIN ${endpoint.path} returns 200`]: (res) => res.status === 200,
      });
    }
  });

  sleep(1);
}
