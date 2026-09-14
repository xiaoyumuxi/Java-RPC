# RPC Performance History

该文档由 CI 持续维护，用于记录 Java-RPC 真实端到端调用链路在不同协议、序列化器、注册中心、payload 和网络条件下的性能数据。

> 数据来自 GitHub Hosted Runner，适合比较实现差异、观察趋势和发现明显回归，不作为绝对性能承诺或硬性合并门槛。CI 使用以生产常见组合为中心的正交矩阵：一次只改变一个主要变量，避免全笛卡尔积既昂贵又难解释。

## CI 性能矩阵

### 生产基线

默认基线不再使用 Local Registry 或裸 loopback，而是：

- Protocol: `grpc`
- Registry: `nacos`（CI 启动真实 `nacos/nacos-server:v2.5.4`）
- Serializer: `protobuf`
- Request payload: `1 KiB`
- Network: Linux `tc netem` 模拟内网：`delay 1ms ± 0.2ms` + `rate 1gbit`

`tc` 只过滤固定 RPC 数据端口 `19090`，不会把 Nacos 的 `8848/9848/9849` 控制面流量一起塑形，因此 RPC 数据面的网络差异不会被注册中心心跳/发现请求混入。

由于请求和响应都会经过 loopback 的 egress qdisc，文档中的 `tc delay` 可理解为单向注入值，RPC 请求/响应增加的 RTT 通常约为其两倍，再叠加框架处理时间。例如 `delay 25ms` 的场景大致增加约 `50ms` RTT。这里的网络档位是稳定、可复现的合成条件，不声称精确代表某个云厂商或运营商 SLA。

### 协议

固定 `Nacos + Protobuf + 1 KiB + tc LAN`：

- `grpc`（baseline）
- `netty`
- `http`
- `http2`

### 序列化

固定 `gRPC + Nacos + 1 KiB + tc LAN`：

- `protobuf`（baseline）
- `kryo`
- `java`
- `json`

### 注册中心

固定 `gRPC + Protobuf + 1 KiB + tc LAN`：

- `nacos`（baseline，真实 Docker）
- `local`（仅作为 control，不再作为默认基线）

### Payload

固定 `gRPC + Nacos + Protobuf + tc LAN`：

- `64 B`
- `1 KiB`（baseline）
- `16 KiB`
- `256 KiB`

这样可以区分协议/序列化固定开销与数据量扩大后的拷贝、编码和带宽成本。

### 网络

固定 `gRPC + Nacos + Protobuf + 1 KiB`，只塑形 RPC 端口：

- 内网 LAN（baseline）：`delay 1ms ± 0.2ms`，约 `2ms` added RTT，`1gbit`
- 跨 AZ / 私网：`delay 3ms ± 1ms`，约 `6ms` added RTT，`500mbit`
- 普通公网：`delay 25ms ± 5ms`，约 `50ms` added RTT，`loss 0.05%`，`100mbit`
- 跨地域公网：`delay 60ms ± 10ms`，约 `120ms` added RTT，`loss 0.1%`，`50mbit`
- 弱网 / 移动网络：`delay 120ms ± 40ms`，约 `240ms` added RTT，`loss 1%`，`5mbit`

公网、跨地域和弱网场景允许出现真实 RPC 失败，不再以“必须 100% 成功”作为采样前提；报告会继续跑完整个阶段并记录成功率、failed、timeout、成功吞吐与成功请求的 P95/P99。至少需要保留成功样本，否则该场景仍判定失败。

## 测量约束

性能 job 与覆盖率 job 分离：性能矩阵显式关闭 JaCoCo，避免 coverage instrumentation 污染延迟与吞吐。性能 JVM 使用低日志配置，避免逐请求 INFO 输出成为热路径瓶颈；普通单元测试和覆盖率仍按原 CI 配置执行。

矩阵采用“围绕生产基线一次只改变一个主要变量”的策略。某个协议如果失败，脚本不会立即停止整个矩阵，而是把该场景标记为 FAIL 后继续运行剩余场景，最后再统一让 job 失败。这样一个坏模式不会遮住其他协议、序列化器、Nacos 或网络档位的数据。

## 记录指标

每个场景都会记录：

- 尝试请求数、成功数、失败数、成功率
- Attempt throughput 与 successful throughput
- 成功 RPC 的 Avg / P50 / P95 / P99 / Max latency
- Client / Server 的 total、success、failed、timeout、active
- Client / Server 内置 metric 的平均与最大耗时
- Registry / Protocol / Serializer / Payload / Network profile
- Java / CPU / Runner 信息

PR CI 会执行完整矩阵作为验证；只有 `main` CI 全绿后才自动把矩阵结果追加到本文档。

## 历史记录

### PR #11 CI 单一基线（矩阵启用前）

- Commit: `281d526ebe6ccd0e970453fc9094b85bf08cfe96`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`
- Registry / Protocol / Serializer: `local / netty / kryo`
- Network: loopback

| Phase | Requests | Concurrency | Throughput req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 200 | 1 | 482.033 | 2.066 | 1.535 | 4.578 | 6.195 | 9.340 |
| concurrent | 1000 | 16 | 1537.520 | 10.250 | 9.376 | 17.876 | 22.806 | 27.916 |

<details><summary>Framework metrics</summary>

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 200 | 200 | 0 | 0 | 0 | 1.832 | 7.988 |
| sequential | SERVER | 200 | 200 | 0 | 0 | 0 | 0.240 | 3.970 |
| concurrent | CLIENT | 1000 | 1000 | 0 | 0 | 0 | 9.692 | 25.216 |
| concurrent | SERVER | 1000 | 1000 | 0 | 0 | 0 | 0.661 | 12.092 |

</details>

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

---

## CI Matrix — 2026-09-14 11:23:30 UTC — `fc639de74af8fbb3c874307b93ac81e7ca81406a`

- Runner: `Linux X64`
- Java: `openjdk version 17.0.20.1 2026-08-18`
- CPU visible: `4`
- Baseline: `gRPC + Nacos + Protobuf + 1 KiB + tc LAN`
- Strategy: orthogonal matrix around the production-like baseline; Local Registry is a control only
- Network simulation: Linux `tc netem` on `lo`, filtered to reserved RPC ports `19091-19120` only
- Nacos isolation: every scenario gets a unique RPC ip:port instance identity
- Coverage instrumentation: disabled for performance scenarios
- Scenario failures: `0`

### `baseline-grpc-protobuf-nacos-lan`

- Status: PASS
- Matrix dimension: `baseline`
- Registry / Protocol / Serializer: `nacos / grpc / protobuf`
- Request payload: `1024 bytes`
- RPC port: `19091`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19091`
- Request payload: `1024 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 40 | 40 | 0 | 100.000 | 1 | 245.311 | 245.311 | 4.055 | 3.879 | 5.224 | 5.285 | 5.285 |
| concurrent | 120 | 120 | 0 | 100.000 | 8 | 1061.807 | 1061.807 | 7.299 | 6.891 | 10.367 | 12.152 | 13.181 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 40 | 40 | 0 | 0 | 0 | 3.876 | 5.083 |
| sequential | SERVER | 40 | 40 | 0 | 0 | 0 | 0.143 | 0.334 |
| concurrent | CLIENT | 120 | 120 | 0 | 0 | 0 | 6.858 | 12.778 |
| concurrent | SERVER | 120 | 120 | 0 | 0 | 0 | 0.386 | 1.853 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `payload-64b`

- Status: PASS
- Matrix dimension: `payload`
- Registry / Protocol / Serializer: `nacos / grpc / protobuf`
- Request payload: `64 bytes`
- RPC port: `19092`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19092`
- Request payload: `64 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 233.029 | 233.029 | 4.270 | 4.121 | 5.013 | 5.217 | 5.217 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 808.614 | 808.614 | 4.818 | 4.584 | 6.522 | 7.729 | 7.729 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 4.112 | 5.056 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.134 | 0.379 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 4.587 | 7.364 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.182 | 0.913 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `payload-16384b`

- Status: PASS
- Matrix dimension: `payload`
- Registry / Protocol / Serializer: `nacos / grpc / protobuf`
- Request payload: `16384 bytes`
- RPC port: `19093`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19093`
- Request payload: `16384 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 206.152 | 206.152 | 4.825 | 4.611 | 5.763 | 6.303 | 6.303 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 587.677 | 587.677 | 6.539 | 6.389 | 8.267 | 8.552 | 8.552 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 4.614 | 6.099 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.224 | 0.587 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 6.222 | 8.331 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.297 | 1.048 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `payload-262144b`

- Status: PASS
- Matrix dimension: `payload`
- Registry / Protocol / Serializer: `nacos / grpc / protobuf`
- Request payload: `262144 bytes`
- RPC port: `19094`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19094`
- Request payload: `262144 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 33.174 | 33.174 | 30.013 | 29.828 | 31.512 | 32.121 | 32.121 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 42.757 | 42.757 | 91.888 | 88.230 | 101.554 | 180.429 | 180.429 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 29.087 | 31.315 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 1.052 | 1.514 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 90.633 | 176.256 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 1.611 | 7.982 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `serializer-kryo`

- Status: PASS
- Matrix dimension: `serializer`
- Registry / Protocol / Serializer: `nacos / grpc / kryo`
- Request payload: `1024 bytes`
- RPC port: `19095`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `kryo`
- Registry: `nacos`
- RPC port: `19095`
- Request payload: `1024 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 215.887 | 215.887 | 4.611 | 4.573 | 5.264 | 5.660 | 5.660 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 701.058 | 701.058 | 5.514 | 5.291 | 7.799 | 8.663 | 8.663 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 4.393 | 5.431 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.271 | 0.514 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 5.105 | 8.467 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.230 | 1.100 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `serializer-java`

- Status: PASS
- Matrix dimension: `serializer`
- Registry / Protocol / Serializer: `nacos / grpc / java`
- Request payload: `1024 bytes`
- RPC port: `19096`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `java`
- Registry: `nacos`
- RPC port: `19096`
- Request payload: `1024 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 228.861 | 228.861 | 4.348 | 4.379 | 4.741 | 5.202 | 5.202 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 613.836 | 613.836 | 6.211 | 5.869 | 8.690 | 9.738 | 9.738 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 4.102 | 4.915 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.283 | 0.557 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 5.792 | 9.155 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.357 | 1.962 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `serializer-json`

- Status: PASS
- Matrix dimension: `serializer`
- Registry / Protocol / Serializer: `nacos / grpc / json`
- Request payload: `1024 bytes`
- RPC port: `19097`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `json`
- Registry: `nacos`
- RPC port: `19097`
- Request payload: `1024 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 217.780 | 217.780 | 4.571 | 4.490 | 5.127 | 5.324 | 5.324 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 688.900 | 688.900 | 5.658 | 5.577 | 7.143 | 8.124 | 8.124 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 4.375 | 5.095 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.316 | 0.697 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 5.289 | 7.220 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.281 | 1.508 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `registry-local-control`

- Status: PASS
- Matrix dimension: `registry`
- Registry / Protocol / Serializer: `local / grpc / protobuf`
- Request payload: `1024 bytes`
- RPC port: `19098`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `protobuf`
- Registry: `local`
- RPC port: `19098`
- Request payload: `1024 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 217.893 | 217.893 | 4.567 | 4.404 | 5.474 | 6.637 | 6.637 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 608.896 | 608.896 | 6.407 | 6.201 | 9.295 | 10.269 | 10.269 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 4.365 | 6.304 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.201 | 0.604 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 5.959 | 9.312 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.285 | 2.782 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `network-cross-az`

- Status: PASS
- Matrix dimension: `network`
- Registry / Protocol / Serializer: `nacos / grpc / protobuf`
- Request payload: `1024 bytes`
- RPC port: `19099`
- Network profile: `tc cross-AZ/private: 3ms +/-1ms one-way, ~6ms added RTT, 500mbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19099`
- Request payload: `1024 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 119.204 | 119.204 | 8.364 | 8.434 | 9.806 | 9.959 | 9.959 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 421.090 | 421.090 | 9.349 | 9.229 | 12.427 | 12.709 | 12.709 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 8.160 | 9.663 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.194 | 0.475 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 9.115 | 12.498 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.220 | 1.438 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `network-public-internet`

- Status: PASS
- Matrix dimension: `network`
- Registry / Protocol / Serializer: `nacos / grpc / protobuf`
- Request payload: `1024 bytes`
- RPC port: `19100`
- Network profile: `tc public internet: 25ms +/-5ms one-way, ~50ms added RTT, 0.05% loss, 100mbit`
- Strict success requirement: `false`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19100`
- Request payload: `1024 bytes`
- Require all requests to succeed: `false`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 19.130 | 19.130 | 52.233 | 52.526 | 62.168 | 66.551 | 66.551 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 64.913 | 64.913 | 60.299 | 60.200 | 73.189 | 75.444 | 75.444 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 51.976 | 66.280 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.266 | 0.528 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 60.088 | 75.279 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.162 | 0.580 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `network-cross-region`

- Status: PASS
- Matrix dimension: `network`
- Registry / Protocol / Serializer: `nacos / grpc / protobuf`
- Request payload: `1024 bytes`
- RPC port: `19101`
- Network profile: `tc cross-region: 60ms +/-10ms one-way, ~120ms added RTT, 0.1% loss, 50mbit`
- Strict success requirement: `false`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19101`
- Request payload: `1024 bytes`
- Require all requests to succeed: `false`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 16 | 16 | 0 | 100.000 | 1 | 8.153 | 8.153 | 122.599 | 123.710 | 144.924 | 144.924 | 144.924 |
| concurrent | 48 | 48 | 0 | 100.000 | 4 | 28.300 | 28.300 | 136.180 | 135.239 | 159.815 | 165.190 | 165.190 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 16 | 16 | 0 | 0 | 0 | 122.280 | 144.704 |
| sequential | SERVER | 16 | 16 | 0 | 0 | 0 | 0.302 | 0.500 |
| concurrent | CLIENT | 48 | 48 | 0 | 0 | 0 | 135.921 | 164.986 |
| concurrent | SERVER | 48 | 48 | 0 | 0 | 0 | 0.216 | 1.037 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `network-weak-mobile`

- Status: PASS
- Matrix dimension: `network`
- Registry / Protocol / Serializer: `nacos / grpc / protobuf`
- Request payload: `1024 bytes`
- RPC port: `19102`
- Network profile: `tc weak/mobile: 120ms +/-40ms one-way, ~240ms added RTT, 1% loss, 5mbit`
- Strict success requirement: `false`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `grpc`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19102`
- Request payload: `1024 bytes`
- Require all requests to succeed: `false`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 12 | 12 | 0 | 100.000 | 1 | 4.354 | 4.354 | 229.611 | 238.054 | 329.140 | 329.140 | 329.140 |
| concurrent | 36 | 36 | 0 | 100.000 | 3 | 8.877 | 8.877 | 336.140 | 308.474 | 577.337 | 812.470 | 812.470 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 12 | 12 | 0 | 0 | 0 | 229.269 | 328.738 |
| sequential | SERVER | 12 | 12 | 0 | 0 | 0 | 0.347 | 0.573 |
| concurrent | CLIENT | 36 | 36 | 0 | 0 | 0 | 335.817 | 812.307 |
| concurrent | SERVER | 36 | 36 | 0 | 0 | 0 | 0.192 | 0.560 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `protocol-netty`

- Status: PASS
- Matrix dimension: `protocol`
- Registry / Protocol / Serializer: `nacos / netty / protobuf`
- Request payload: `1024 bytes`
- RPC port: `19103`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `netty`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19103`
- Request payload: `1024 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 294.823 | 294.823 | 3.372 | 3.172 | 4.443 | 4.883 | 4.883 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 1115.842 | 1115.842 | 3.446 | 3.311 | 4.583 | 5.648 | 5.648 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 3.175 | 4.704 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.188 | 0.380 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 3.269 | 5.116 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.142 | 0.752 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `protocol-http`

- Status: PASS
- Matrix dimension: `protocol`
- Registry / Protocol / Serializer: `nacos / http / protobuf`
- Request payload: `1024 bytes`
- RPC port: `19104`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `http`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19104`
- Request payload: `1024 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 281.369 | 281.369 | 3.534 | 3.514 | 4.125 | 4.269 | 4.269 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 931.229 | 931.229 | 4.158 | 4.074 | 5.229 | 5.509 | 5.509 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 3.373 | 4.003 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.151 | 0.308 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 3.938 | 5.232 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.156 | 0.660 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
### `protocol-http2`

- Status: PASS
- Matrix dimension: `protocol`
- Registry / Protocol / Serializer: `nacos / http2 / protobuf`
- Request payload: `1024 bytes`
- RPC port: `19105`
- Network profile: `tc LAN: 1ms +/-0.2ms one-way, ~2ms added RTT, 1gbit`
- Strict success requirement: `true`



- Commit: `fc639de74af8fbb3c874307b93ac81e7ca81406a`
- Protocol: `http2`
- Serializer: `protobuf`
- Registry: `nacos`
- RPC port: `19105`
- Request payload: `1024 bytes`
- Require all requests to succeed: `true`
- Java: `17.0.20.1`
- CPU visible to JVM: `4`

| Phase | Attempts | Success | Failed | Success % | Concurrency | Attempt req/s | Success req/s | Avg ms | P50 ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | 20 | 20 | 0 | 100.000 | 1 | 211.683 | 211.683 | 4.702 | 4.595 | 5.372 | 5.747 | 5.747 |
| concurrent | 60 | 60 | 0 | 100.000 | 4 | 522.647 | 522.647 | 7.404 | 7.213 | 10.388 | 12.085 | 12.085 |

#### Framework metrics

| Phase | Side | Total | Success | Failed | Timeout | Active | Metric avg ms | Metric max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sequential | CLIENT | 20 | 20 | 0 | 0 | 0 | 4.496 | 5.575 |
| sequential | SERVER | 20 | 20 | 0 | 0 | 0 | 0.175 | 0.308 |
| concurrent | CLIENT | 60 | 60 | 0 | 0 | 0 | 6.950 | 11.932 |
| concurrent | SERVER | 60 | 60 | 0 | 0 | 0 | 0.514 | 3.268 |

Latency percentiles include successful RPCs only. Low sample counts do not establish a reliable tail-latency SLA.
Nacos visibility polling and warmup are excluded from timing. In lossy profiles, server observation intervals may include late requests.
