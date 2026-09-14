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
