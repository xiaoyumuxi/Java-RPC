# RPC Performance History

该文档由 CI 持续维护，用于记录 Java-RPC 真实端到端调用链路的性能快照。

> 这些数据来自 GitHub Hosted Runner，适合观察趋势和发现明显回归，不作为绝对性能承诺或硬性合并门槛。测试固定使用 Local Registry + Netty + Kryo，并保持固定 warmup、请求量和并发度以提高不同运行之间的可比性。

## 测试配置

- Warmup：100 次 RPC
- 顺序阶段：200 次 RPC，并发度 1
- 并发阶段：1000 次 RPC，并发度 16
- Registry：`local`
- Protocol：`netty`
- Serializer：`kryo`
- 记录指标：吞吐、Avg、P50、P95、P99、Max，以及 Client / Server 内置 metrics

## 历史记录

### PR #11 CI 基线

- Commit: `281d526ebe6ccd0e970453fc9094b85bf08cfe96`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Requests | Concurrency | Throughput req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 200 | 1 | 482.033 | 2.066 | 1.535 | 4.578 | 6.195 | 9.340 |
| concurrent | 1000 | 16 | 1537.520 | 10.250 | 9.376 | 17.876 | 22.806 | 27.916 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 200 | 200 | 0 | 0 | 0 | 1.832 | 7.988 |
| sequential | SERVER | 200 | 200 | 0 | 0 | 0 | 0.240 | 3.970 |
| concurrent | CLIENT | 1000 | 1000 | 0 | 0 | 0 | 9.692 | 25.216 |
| concurrent | SERVER | 1000 | 1000 | 0 | 0 | 0 | 0.661 | 12.092 |

---

## CI Snapshot — 2026-09-14 09:14:08 UTC — `c525edb`



- Commit: `c525edb58daffb37267b544e5928f0fb4db5ab49`
- Protocol: `netty`
- Serializer: `kryo`
- Registry: `local`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Requests | Concurrency | Throughput req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 200 | 1 | 590.543 | 1.684 | 1.402 | 3.225 | 4.610 | 5.836 |
| concurrent | 1000 | 16 | 1770.056 | 8.788 | 8.113 | 13.603 | 19.342 | 25.953 |

### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 200 | 200 | 0 | 0 | 0 | 1.463 | 4.700 |
| sequential | SERVER | 200 | 200 | 0 | 0 | 0 | 0.190 | 2.493 |
| concurrent | CLIENT | 1000 | 1000 | 0 | 0 | 0 | 8.364 | 20.758 |
| concurrent | SERVER | 1000 | 1000 | 0 | 0 | 0 | 0.791 | 5.687 |

