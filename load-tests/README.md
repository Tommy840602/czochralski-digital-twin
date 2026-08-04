# k6 production load test

Read-only k6 results for `https://twin.tommy-huang.dev`, captured on 2026-08-05
(Asia/Taipei). The workload covered the SPA entry point `/` and the SockJS
handshake information endpoint `/ws/info`.

The test did not log in or call registration, reports, control commands, data
ingest, approval workflows, or other write APIs.

## Results

| Profile | Highest VUs | Requests | Throughput | Error rate | Average | p95 | Max | Result |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| Baseline | 10 | 934 | 9.24 req/s | 0.00% | 192 ms | 200 ms | 207 ms | Passed |
| 200 → 500 → 1,000 → 2,000 → 5,000 | 1,651 | 104,108 | 521.5 req/s | 1.01% | 482 ms | 1.38 s | 10.01 s | Auto-stopped |

The breakpoint run stopped while ramping from 1,000 toward 2,000 VUs. It did
not proceed to the 2,000 or 5,000 VU plateaus after the cumulative HTTP error
rate crossed the 1% stop condition.

### Endpoint detail at breakpoint

| Endpoint | Passed | Failed | p95 | Max |
|---|---:|---:|---:|---:|
| `/` | 51,616 | 842 | 2.01 s | 10.01 s |
| `/ws/info` | 51,432 | 218 | 498 ms | 10.01 s |

The homepage became the primary bottleneck. Timeouts appeared around the
1,000-VU stage, followed by isolated connection resets. The SockJS information
endpoint remained faster at p95 but also accumulated timeout failures as load
continued to rise.

The public site and `/ws/info` both recovered to HTTP 200 after the run.

## Temporary operating guidance

Use **500 concurrent VUs or less** as a conservative ceiling for this exact
unauthenticated read-only workload until server-side Caddy, JVM, connection,
CPU, memory, and datastore metrics are correlated with another sustained test.
The observed 1,651 VUs is a failure point, not a capacity target.

## Reproduce

Install k6 and run from the repository root:

```bash
k6 run \
  -e BASE_URL=https://twin.tommy-huang.dev \
  -e PROFILE=baseline \
  --summary-export load-tests/results/baseline.json \
  load-tests/k6-readonly.js
```

The high-pressure profile is intentionally self-aborting at a 1% HTTP error
rate or a cumulative p95 of 3 seconds:

```bash
k6 run \
  -e BASE_URL=https://twin.tommy-huang.dev \
  -e PROFILE=breakpoint \
  --summary-export load-tests/results/breakpoint.json \
  load-tests/k6-readonly.js
```

Only run the breakpoint profile against infrastructure you are authorized to
load-test.

## Artifacts

- `results/2026-08-05-baseline.json`
- `results/2026-08-05-breakpoint.json`

## Limitations

- One macOS k6 generator was used, so the client and network path are included
  in the measured breakpoint.
- The test did not load JavaScript chunks, WebGL/Three.js rendering, fonts, or
  browser-side chart work after the HTML response.
- `/ws/info` measures SockJS negotiation metadata; it does not establish full
  STOMP subscriptions or hold thousands of concurrent WebSocket sessions.
- Authenticated REST APIs, TimescaleDB queries, Kafka/Flink processing, Redis,
  MongoDB, Elasticsearch, and report generation were not loaded.
- No synchronized server-side metrics were captured, so this identifies the
  external failure point but not its internal root cause.
