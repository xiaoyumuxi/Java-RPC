# RPC Performance History

该文档由 CI 持续维护，用于记录 Java-RPC 真实端到端调用链路在不同协议、序列化器、注册中心和网络条件下的性能数据。

> 数据来自 GitHub Hosted Runner，适合比较实现差异、观察趋势和发现明显回归，不作为绝对性能承诺或硬性合并门槛。CI 使用正交矩阵：一次只改变一个主要变量，避免全笛卡尔积既昂贵又难解释。

## CI 性能矩阵

### 协议

固定 `Local Registry + Kryo + loopback`：

- `netty`
- `http`
- `http2`
- `grpc`

### 序列化

固定 `Local Registry + Netty + loopback`：

- `kryo`
- `java`
- `json`
- `protobuf`

### 注册中心

固定 `Netty + Kryo + loopback`：

- `local`
- `nacos`（CI 启动真实 `nacos/nacos-server:v2.5.4` Docker）

### 网络

固定 `Local Registry + Netty + Kryo`，通过 Linux `tc netem` 作用于 `lo`：

- 原始 loopback（baseline）
- `delay 20ms ± 5ms` + normal jitter
- `delay 10ms ± 2ms` + `loss 0.2%`
- `delay 5ms` + `rate 1mbit`

> `tc` 场景的目的不是模拟某个运营商的精确 SLA，而是稳定制造 RTT、抖动、丢包和带宽约束，观察 RPC 的 P95/P99、吞吐和 timeout 行为。由于当前性能用例 payload 较小，带宽场景主要用于发现排队/协议开销趋势；如果后续加入大对象 RPC，再单独增加大 payload 网络矩阵。

## 记录指标

每个场景都会记录：

- 顺序调用与并发调用的 Throughput
- Avg / P50 / P95 / P99 / Max latency
- Client / Server 的 total、success、failed、timeout、active
- Client / Server 内置 metric 的平均与最大耗时
- Registry / Protocol / Serializer / Network profile
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
