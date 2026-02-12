# 🚀 XiaoYu RPC Framework

> 基于 **Netty**, **Nacos**, 和 **ByteBuddy** 构建的轻量级、高性能、可扩展 RPC 框架。
> 支持多种协议，包括 **HTTP/2**, **HTTP/1.1**, 以及自定义 **Netty** 协议。

![Java](https://img.shields.io/badge/Java-17%2B-blue?style=flat-square&logo=java)
![Netty](https://img.shields.io/badge/Netty-4.1.x-green?style=flat-square)
![Nacos](https://img.shields.io/badge/Nacos-2.x-orange?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

---

## 📖 简介

本项目是一个旨在演示标准协议与动态调用融合的高性能插件化 RPC 框架。
与传统强绑定单一协议或需要为每个服务生成代码的 RPC 框架不同，**XiaoYu RPC** 独创了 **"通用 gRPC 适配器 (Universal gRPC Adapter)"**。它实现了标准的 gRPC 协议 (HTTP/2 + Protobuf)，但能够将请求动态路由到 Java 服务实现。这使得您可以：

1.  **直接使用标准 gRPC 客户端** (如 Python, Go, Node.js) 调用您的 Java 服务。
2.  **保留 Java 的动态灵活性** (反射/ByteBuddy)，无需为每个业务类生成单独的 `.proto` Stub 代码。

## 🏗️ 项目架构

项目采用模块化设计，确保关注点分离和可维护性：

| 模块 | 描述 |
|--------|-------------|
| **`rpc-api`** | 定义服务接口。由服务提供者（Provider）和消费者（Consumer）共享。 |
| **`rpc-common`** | 通用工具类、值对象 (`RpcRequest`, `RpcResponse`) 及 Protobuf 定义 (`rpc_meta.proto`)。 |
| **`rpc-core`** | 框架核心实现。包含 SPI 接口定义、动态代理和注册中心逻辑。**完全不含 Netty 依赖**。 |
| **`rpc-transport-netty`** | 基于 **Netty** 实现的默认传输层模块。 |
| **`rpc-provider`** | 示例服务提供者应用，用于实现并暴露服务。 |
| **`rpc-consumer`** | 示例服务消费者应用，用于引入并调用服务。 |
| **`rpc-spring-boot-starter`** | Spring Boot 自动配置 Starter，便于集成 Provider/Consumer。 |
| **`rpc-benchmark`** | 基于 JMH (Java Microbenchmark Harness) 的性能基准测试模块。 |
| **`python_client`** | Python 客户端实现，用于演示跨语言 gRPC 互操作性。 |
| **`go_client`** | Go 客户端实现，用于演示跨语言 gRPC 互操作性。 |

## ✨ 核心特性

- **🔌 插件化架构**: 采用自定义 SPI 机制，实现最大程度的灵活性。
- **🤝 通用 gRPC 兼容性**: 自研 `GrpcProtocol` 层，在 HTTP/2 上运行标准 gRPC 协议，实测可与官方 `grpc-python` 客户端完美互通。
- **⚡ 动静混合模式**: 结合了 Protobuf 序列化（配合自定义类型包装器）的高性能和 Java 动态代理的灵活性。
- **🚀 可插拔传输层**: 传输层完全解耦。默认实现为 `rpc-transport-netty`，但可无缝替换为 Tomcat 或 Socket 实现。
- **📡 多协议支持**: 支持 `Netty` (自定义), `HTTP/1.1`, 或 `gRPC` (HTTP/2) 通信协议。
- **⚡ 高性能代理**: 使用 **ByteBuddy** 生成动态代理，针对 Java 17+ 进行了优化。
- **⚖️ 智能负载均衡**: 内置 `RoundRobin` (轮询) 和 `Random` (随机) 策略。
- **📦 多样化序列化**: 支持 `Protobuf` (增强版), **Kryo** (针对 POJO 优化), `JSON`, 以及标准 `Java` 序列化。
- **🔄 请求多路复用**: 真正的异步请求/响应关联，通过 `request_id` 实现单条连接处理数千个并发流（尤其在 HTTP/2 下性能卓越）。
- **🔍 服务发现**: 集成 **Nacos** 实现健壮的服务注册与发现。

---

## 🔌 SPI 设计与生态

XiaoYu RPC 遵循 **微内核架构 (Microkernel Architecture)**，核心模块 (`rpc-core`) 仅提供生命周期管理和 SPI (Service Provider Interface) 定义，所有具体功能均作为插件实现。这种设计确保了框架的高度可扩展性和轻量化，并遵循 **开闭原则 (Open-Closed Principle)**。

### 🧩 核心扩展点

我们要严格定义接口以解耦每个主要组件：

| 接口 | 描述 | 默认实现 | 设计目的 |
|-----------|-------------|--------------|---------|
| **`Transport`** | 网络通信抽象。解耦底层 I/O 框架。 | `NettyTransport` | 允许在不更改核心逻辑的情况下切换 Netty, Tomcat, 或 Socket。 |
| **`Protocol`** | 消息协议定义。控制字节流的帧处理方式。 | `NettyProtocol` | 支持在同一端口上运行多种协议 (自定义 RPC, gRPC, HTTP)。 |
| **`Serializer`** | 对象序列化策略。 | `ProtoBuf` | 平衡性能 (Protobuf/Kryo) 与兼容性 (JSON/Java)。 |
| **`LoadBalancer`** | 客户端负载均衡策略。 | `RoundRobin` | 均匀或随机地将流量分发给服务提供者。 |
| **`ServiceRegistry`** | 服务注册与发现。 | `Nacos`, `Local` | 解耦具体的注册中心后端 (可轻松替换为 Zookeeper/Consul)。 |
| **`ProxyFactory`** | 动态代理生成策略。 | `ByteBuddy` | 针对不同 JDK 版本的优化 (ByteBuddy 在 Java 17+ 表现更佳)。 |

### 🛠️ ExtensionLoader

我们实现了一套类似于 Dubbo 的强大加载机制 `ExtensionLoader`。它会扫描 `META-INF/rpc/` 目录下的配置文件，并按需懒加载实现类。

### 如何添加新扩展

1.  **实现接口**: 创建一个类实现目标 SPI 接口 (例如 `Serializer`)。
2.  **创建 SPI 配置文件**:
    *   在 `src/main/resources/META-INF/rpc/` 目录下创建文件。
    *   文件名必须与接口的全限定名一致 (例如 `com.xiaoyu.rpc.common.serialization.Serializer`)。
3.  **注册实现类**: 在文件中添加键值对:
    ```properties
    my-serializer=com.example.MyCustomSerializer
    ```
4.  **使用扩展**: 更新 `rpc-config.yaml`:
    ```yaml
    rpc:
      serializer: my-serializer
    ```

---

## 🚀 快速开始

### 1. 前置条件 (Nacos)

使用 Docker 启动 Nacos:

```bash
docker run --name nacos-standalone \
    -e MODE=standalone \
    -p 8848:8848 \
    -p 9848:9848 \
    -d nacos/nacos-server:v2.3.1-slim
```

### 2. 运行 Provider

执行以下命令启动 Java RPC Provider。这将编译项目并将 `HelloService` 注册到本地 Nacos 实例。

默认 `rpc.protocol` 已设置为 `netty`，用于 Java-to-Java 的 Provider/Consumer 调用。
若需要 Python/Go 跨语言互通，请在 `rpc-core/src/main/resources/rpc-config.yaml` 中手动切换到 `grpc`。

```bash
# 1. 构建项目
mvn clean package -DskipTests

# 2. 启动 Provider
java -cp rpc-provider/target/rpc-provider-1.0-SNAPSHOT.jar:rpc-transport-netty/target/rpc-transport-netty-1.0-SNAPSHOT.jar:rpc-core/target/rpc-core-1.0-SNAPSHOT.jar:rpc-common/target/rpc-common-1.0-SNAPSHOT.jar:rpc-api/target/rpc-api-1.0-SNAPSHOT.jar:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -pl rpc-provider -am) com.xiaoyu.rpc.provider.ProviderApp
```

### 3. 运行 Consumer

在 `rpc-consumer` 模块中执行 `ConsumerApp` 发起调用。

```bash
java -cp rpc-consumer/target/rpc-consumer-1.0-SNAPSHOT.jar:rpc-transport-netty/target/rpc-transport-netty-1.0-SNAPSHOT.jar:rpc-core/target/rpc-core-1.0-SNAPSHOT.jar:rpc-common/target/rpc-common-1.0-SNAPSHOT.jar:rpc-api/target/rpc-api-1.0-SNAPSHOT.jar:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -pl rpc-consumer -am) com.xiaoyu.rpc.consumer.ConsumerApp
```

### 4. 运行测试

**单元测试**:
运行包含 SPI、序列化器、负载均衡器和协议的全套单元测试：

```bash
mvn test -pl rpc-core,rpc-transport-netty
```

**集成测试**:
运行完整的集成测试套件：

```bash
mvn test -pl rpc-consumer -am -Dtest=FullIntegrationTest
```

### 5. 基准测试与性能结果

XiaoYu RPC 专注于极致性能。以下是使用 **JMH** 测得的真实数据（基于 8 线程并发，本地回路 127.0.0.1 压测）。

#### 5.1 协议性能对比（吞吐量与延迟）

| 通信协议 | 吞吐量 (ops/ms) | 平均延迟 (ms/op) | 性能简评 |
| :--- | :--- | :--- | :--- |
| **Netty (自定义)** | **84.245** | **0.093** | **性能冠军。** 纯二进制协议，开销极小。 |
| **HTTP/1.1** | 76.853 | 0.104 | 表现稳健，但在单连接高并发下受限于串行处理。 |
| **HTTP/2** | 58.847 | 0.137 | **多路复用利器。** 虽然协议头较重，但支持高并发流。 |

> [!TIP]
> **为什么多路复用很重要？**
> 在零延迟的本地测试中，简单的 HTTP/1.1 略快；但在真实生产环境下，由于网络波动和高延迟，HTTP/2 通过消除**队头阻塞 (HoL)** 能显著提升系统吞吐量和稳定性。

#### 5.2 序列化效率对比
针对标准 POJO 对象 (`RpcRequest`) 的处理能力。

| 序列化器 | 吞吐量 (ops/us) | 延迟 (us/op) | 报文大小 |
| :--- | :---: | :---: | :---: |
| **Protobuf** | **34.429** | **0.029** | **65 bytes** |
| **Kryo (已优化)** | 11.932 | 0.066 | 68 bytes |
| **JSON** | 2.050 | 0.497 | 231 bytes |
| **Java** | 1.102 | 0.895 | 652 bytes |

**核心洞察：**
- **Protobuf vs. Java**: Protobuf 的处理速度比 Java 原生序列化快 **77 倍**，且体积缩小了 **10 倍**。
- **二进制 vs. 文本**: 在处理复杂 POJO 时，二进制协议 (Kryo/Protobuf) 的吞吐量比文本协议 (JSON) 高出约 **6 倍**，这归功于 Varint 压缩和去除字段名存储。

---

## 🛠️ 配置手册

通过 `rpc-core/src/main/resources/rpc-config.yaml` 配置框架。

```yaml
rpc:
  transport: "netty"         # 传输层: netty
  protocol: "netty"          # 协议: netty, http, http2
  server-host: "127.0.0.1"
  server-port: 8080
  registry: "nacos"          # 注册中心: nacos, local
  registry-address: "127.0.0.1:8848"
  serializer: "protobuf"     # 序列化器: protobuf, kryo, java, json
  proxy: "bytebuddy"         # 代理方式: jdk, bytebuddy
  load-balancer: roundrobin  # 负载均衡: roundrobin, random
  max-message-size: 8388608  # 8MB
```

> [!IMPORTANT]
> 默认使用 `netty`，适合本地 Java-to-Java 的高性能链路。
> 现在 `grpc` 也支持 `rpc-consumer`（`RpcClientProxy`）调用，可同时用于 Java 侧和 Python/Go 互操作。

---

## ❓ 常见问题 (FAQ)

**Q: 为什么选择 ByteBuddy?**
A: CGLIB 在 Java 17+ 上由于深层反射限制存在问题。ByteBuddy 是目前字节码操作的行业标准。

**Q: 连接超时或被拒绝 (Connection Refused)?**
A: 请确保 Nacos 已运行且端口 `8848` 和 `9848` 可访问。检查 `rpc-config.yaml` 主机/端口配置是否正确。

**Q: 如何切换到本地注册中心进行测试？**
A: 在 `rpc-config.yaml` 中设置 `registry: "local"`。这将绕过 Nacos，使用内存 Map 进行服务注册，非常适合单元测试或无网开发。



**Q: 遇到 "No Transport Found" 错误？**
A: 请确保你在运行时依赖中引入了 `rpc-transport-netty`（或其他传输模块）。为了保证轻量和解耦，`rpc-core` 默认不包含传输层实现。

---

## 🌱 Spring Boot 集成

我们提供了一个专用的 Spring Boot Starter: `rpc-spring-boot-starter`。

### 依赖引入

```xml
<dependency>
    <groupId>com.xiaoyu.rpc</groupId>
    <artifactId>rpc-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 服务提供者示例

```java
@RpcService
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name;
    }
}
```

### 服务消费者示例

```java
@RestController
public class HelloController {
    @RpcReference
    private HelloService helloService;

    @GetMapping("/hello")
    public String hello(@RequestParam String name) {
        return helloService.sayHello(name);
    }
}
```

### 配置 (`application.yml`)

```yaml
rpc:
  server-port: 8080
  registry: nacos
  registry-address: 127.0.0.1:8848
  serializer: kryo
  server-enabled: true  # 对于纯消费者应用，设置为 false
```

---

## 🌐 多语言 gRPC 支持 (Python & Go)

本框架支持与标准 gRPC 客户端（如 Python, Go）互操作，允许非 Java 客户端调用本框架托管的服务。

### 特性

- **标准 gRPC 协议**: 实现了兼容主流 gRPC 库（如 `grpc-io`）的标准 HTTP/2 传输层。
- **Protobuf 序列化**: 通过包装类型支持标准 Protobuf `Empty`, `StringValue`, `Int32Value` 等的数据交换。
- **Nacos 集成**: 注册在 Nacos 中的服务可以被发现和调用。

### 使用指南

1.  **配置 Java 服务端 (gRPC 模式)**:
    将 `rpc-core/src/main/resources/rpc-config.yaml` 调整为以下配置（可直接覆盖）:

    ```yaml
    rpc:
      transport: "netty"
      protocol: "grpc"
      server-host: "127.0.0.1"
      server-port: 8080
      registry: "nacos"
      registry-address: "127.0.0.1:8848"
      serializer: "protobuf"
      proxy: "bytebuddy"
      load-balancer: "roundrobin"
      max-message-size: 8388608
    ```
    完成多语言联调后，如需恢复 Java-to-Java 调用，请将 `protocol` 改回 `netty`（或 `http`/`http2`）。

2.  **启动 Java Provider (gRPC 模式)**:

    ```bash
    # 构建项目 (跳过测试以加速)
    mvn clean package -DskipTests

    # 启动 Provider
    java -cp rpc-provider/target/rpc-provider-1.0-SNAPSHOT.jar:rpc-transport-netty/target/rpc-transport-netty-1.0-SNAPSHOT.jar:rpc-core/target/rpc-core-1.0-SNAPSHOT.jar:rpc-common/target/rpc-common-1.0-SNAPSHOT.jar:rpc-api/target/rpc-api-1.0-SNAPSHOT.jar:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -pl rpc-provider -am) com.xiaoyu.rpc.provider.ProviderApp
    ```

3.  **运行 Python 客户端**:
    详情请参考 [`python_client/client.py`](python_client/client.py)。

    ```bash
    cd python_client

    # 创建并激活虚拟环境
    python3 -m venv venv
    source venv/bin/activate

    # 安装依赖
    pip install grpcio grpcio-tools protobuf

    # 运行客户端
    python3 client.py
    ```

    **预期输出**:

    ```text
    RpcResponse received:
    Data: Hello, World! (from Multi-Module Netty Server)
    Message: Success
    ```

4.  **运行 Go 客户端**:
    进入 `go_client` 目录并运行:

    ```bash
    cd go_client
    go run main.go
    ```

    **预期输出**:

    ```text
    Sending RpcRequest: interface=com.xiaoyu.rpc.api.HelloService, method=sayHello, param=World
    RpcResponse received:
    Data: Hello, World!
    Message: Success
    ```

## 🔄 持续集成与交付 (CI/CD)

为了确保系统可靠性和代码质量，本项目集成了一套基于 **GitHub Actions** 的健壮 CI/CD 流水线。

**关键工作流:**
- **自动化测试**: 运行单元测试和集成测试以验证 RPC 功能。
- **服务验证**: 在容器化环境中启动 Nacos, Java Provider, 和 Python Client 以测试跨语言互操作性。
- **构建状态**: 通过 GitHub Actions 提供代码健康度的即时反馈。

![CI/CD Workflow Result](docs/images/image.png)

---

## 🔬 技术深度解析: gRPC 协议实现

XiaoYu RPC 互操作性的核心在于基于 Netty HTTP/2 栈自定义实现的 gRPC 传输协议。

![gRPC Data Processing Flow](docs/images/grpc_processing_flow.png)

### 1. 报文格式 (5字节头部)

每个 gRPC 消息都以 5 字节的头部开始，由 `GrpcServerHandler` 直接处理：

- **压缩标志 (1 字节)**: `0` (未压缩) 或 `1` (压缩)。
- **消息长度 (4 字节)**: 大端序整数，指定后续 Protobuf 载荷的长度。
- **载荷**: 标准 Protobuf 二进制数据，通过 `NativeProtobufSerializer` 反序列化。

### 2. 头部对齐 (Header Alignment)

严格遵守 gRPC HTTP/2 头部规范以确保兼容性：

- **:status**: `200` (HTTP 层面的成功)
- **content-type**: `application/grpc` (客户端识别的关键)
- **te**: `trailers`

### 3. Trailer 与状态码

gRPC 使用 HTTP/2 Trailers 来传递最终的 RPC 状态，这与 HTTP 状态码是区分开的。

- **HEADERS 帧 (EndStream=true)**: 在数据载荷之后发送。
- **grpc-status**: `0` 表示 OK，非零表示错误。
- **grpc-message**: 描述性错误信息。

---

## 🤝 贡献

欢迎贡献代码！请随时提交 Issue 或 Pull Request 以改进框架。
