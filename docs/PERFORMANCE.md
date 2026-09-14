# RPC 性能测试说明

本页只说明 Java-RPC 的 CI 性能测试规则、基线和报告存储方式，**不再持续追加每次运行的数据**。

性能数据来自 GitHub Hosted Runner，用于比较实现差异、观察趋势和发现明显回归，不代表生产环境绝对性能，也不作为固定 SLA。

## 报告存储方式

每次 `main` CI 的 Build、Unit Tests、RPC Integration、RPC Performance Matrix 和 CI Gate 全部成功后，CI 会创建一份新的独立 Markdown 报告：

```text
docs/performance/YYYY-MM-DD/YYYY-MM-DD_HH-MM-SSZ_<short-sha>.md
```

例如：

```text
docs/performance/2026-09-14/2026-09-14_10-58-02Z_75070ff.md
```

时间统一使用 **UTC**，文件时间取本次性能矩阵开始运行的时间。同一次运行只写一个报告文件，历史报告不会被覆盖，也不会再把所有结果堆进本页。

[浏览性能报告目录](./performance/)

早期没有保存精确运行时间的旧数据放在 `docs/performance/legacy/`，不会为了统一命名而伪造时间。

## 固定报告模板

每份独立报告使用同一套中文模板，包含：

- 运行时间、提交 SHA、Runner、Java、CPU
- 生产基线和测试策略
- 每个场景的注册中心、协议、序列化器、请求载荷和网络环境
- 尝试数、成功数、失败数、成功率
- 尝试吞吐和成功吞吐
- 成功 RPC 的平均延迟、P50、P95、P99、最大延迟
- Client / Server 的 total、success、failed、timeout、active 指标

## 生产基线

默认基线为：

- 协议：`grpc`
- 注册中心：`nacos`（CI 启动真实 `nacos/nacos-server:v2.5.4`）
- 序列化器：`protobuf`
- 请求载荷：`1 KiB`
- 网络：Linux `tc netem` 模拟内网，单向 `1ms ± 0.2ms`，约增加 `2ms RTT`，`1gbit`

Local Registry 只作为对照，不作为默认性能基线。

## 正交性能矩阵

矩阵围绕生产基线一次只改变一个主要变量，避免做昂贵且难解释的全笛卡尔积。

### 协议

固定 `Nacos + Protobuf + 1 KiB + tc 内网`：

- `grpc`（基线）
- `netty`
- `http`
- `http2`

### 序列化器

固定 `gRPC + Nacos + 1 KiB + tc 内网`：

- `protobuf`（基线）
- `kryo`
- `java`
- `json`

### 注册中心

固定 `gRPC + Protobuf + 1 KiB + tc 内网`：

- `nacos`（基线，真实 Docker）
- `local`（仅作对照）

### 请求载荷

固定 `gRPC + Nacos + Protobuf + tc 内网`：

- `64 B`
- `1 KiB`（基线）
- `16 KiB`
- `256 KiB`

### 网络环境

固定 `gRPC + Nacos + Protobuf + 1 KiB`，`tc` 只塑形 RPC 数据端口，不影响 Nacos 的 `8848/9848/9849` 控制面流量。

| 网络环境 | 单向附加延迟 / 抖动 | 名义附加 RTT | 丢包 | 限速 |
| --- | --- | --- | --- | --- |
| 内网 | `1ms ± 0.2ms` | 约 `2ms` | 0 | `1gbit` |
| 跨 AZ / 私网 | `3ms ± 1ms` | 约 `6ms` | 0 | `500mbit` |
| 普通公网 | `25ms ± 5ms` | 约 `50ms` | `0.05%` | `100mbit` |
| 跨地域公网 | `60ms ± 10ms` | 约 `120ms` | `0.1%` | `50mbit` |
| 弱网 / 移动网络 | `120ms ± 40ms` | 约 `240ms` | `1%` | `5mbit` |

这些是为了可重复比较而设置的合成网络条件，不代表某个云厂商或运营商的真实 SLA。

## 测量约束

性能 job 与覆盖率 job 分离，性能矩阵显式关闭 JaCoCo，并使用低日志配置减少测试框架自身对延迟和吞吐的影响。

Nacos 服务可见性轮询和 warmup 不计入 RPC 性能计时。公网、跨地域和弱网场景允许出现真实失败，报告会继续记录成功率、失败数、超时数、成功吞吐以及成功请求的 P95/P99；数据损坏仍会直接判定为测试失败。

P95/P99 只统计成功请求，小样本不能用于推导生产 SLA。当前网络模拟仍发生在同一 GitHub Runner 的真实 TCP loopback 上，不等价于跨物理主机或真实公网压测。
