# 🚀 XiaoYu RPC Framework

> A lightweight, high-performance, and extensible RPC framework based on **Netty**, **Nacos**, and **ByteBuddy**.
> Supports multiple protocols including **HTTP/2**, **HTTP/1.1**, and custom **Netty** protocols.

![Java](https://img.shields.io/badge/Java-17%2B-blue?style=flat-square&logo=java)
![Netty](https://img.shields.io/badge/Netty-4.1.x-green?style=flat-square)
![Nacos](https://img.shields.io/badge/Nacos-2.x-orange?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

---

## 📖 Introduction

This project is a modular RPC framework designed to demonstrate the core principles of remote procedure calls. It features a highly extensible architecture using **SPI (Service Provider Interface)**, allowing developers to easily plug in custom serializers, load balancers, and registry center implementations.

## 🏗️ Project Structure

The project follows a clean multi-module Maven architecture:

- **`rpc-api`**: Common interfaces and data models shared between provider and consumer.
- **`rpc-common`**: Core utilities, SPI loader mechanism, and common abstractions.
- **`rpc-core`**: The heart of the framework, containing protocol implementations, networking, and server/client logic.
- **`rpc-provider`**: Sample service provider implementation.
- **`rpc-consumer`**: Sample service consumer implementation and integration tests.

## ✨ Key Features

- **🔌 Plugin-based Architecture**: Leverages a custom SPI mechanism for maximum flexibility.
- **📡 Multi-Protocol Support**: Choice of `Netty` (custom), `HTTP/1.1`, or `HTTP/2` for communication.
- **⚡ High-Performance Proxy**: Uses **ByteBuddy** for dynamic proxy generation, optimized for Java 17+.
- **⚖️ Intelligent Load Balancing**: Includes `RoundRobin` and `Random` strategies.
- **📦 Diverse Serialization**: Supports `Protobuf`, `Kryo`, and standard `Java` serialization.
- **🔍 Service Discovery**: Integrated with **Nacos** for robust service registry and discovery.

---

## 🚀 Quick Start

### 1. Prerequisites (Nacos)

Start Nacos using Docker:

```bash
docker run --name nacos-standalone \
    -e MODE=standalone \
    -p 8848:8848 \
    -p 9848:9848 \
    -d nacos/nacos-server:v2.3.1-slim
```

### 2. Run the Provider

Execute the `ProviderApp` in the `rpc-provider` module. This will register the `HelloService` to your local Nacos instance.

```bash
# Main Class: com.xiaoyu.rpc.provider.ProviderApp
```

### 3. Run the Consumer

Execute the `ConsumerApp` in the `rpc-consumer` module to make calls to the provider.

```bash
# Main Class: com.xiaoyu.rpc.consumer.ConsumerApp
```

### 4. Running Integration Tests

To run the full integration test suite:

```bash
mvn test -pl rpc-consumer -Dtest=FullIntegrationTest
```

---

## 🛠️ Configuration

Configure the framework via `rpc-core/src/main/resources/rpc-config.yaml`.

```yaml
rpc:
  protocol: "http2"          # Protocol: netty, http, http2
  server-host: 127.0.0.1
  server-port: 8080
  registry: "nacos"          # Registry: nacos, local
  registry-address: "127.0.0.1:8848"
  serializer: KRYO           # Serializer: PROTOBUF, KRYO, JAVA
  proxy: bytebuddy           # Proxy: jdk, bytebuddy
  load-balancer: roundrobin  # Load Balancer: roundrobin, random
```

## ❓ FAQ

**Q: Why ByteBuddy?**  
A: CGLIB is problematic on Java 17+ due to deep reflection restrictions. ByteBuddy is the modern industry standard for bytecode manipulation.

**Q: Connection Timeout/Refusal?**  
A: Ensure Nacos is running and the ports `8848` and `9848` are accessible. Check your `rpc-config.yaml` for correct host/port settings.


---

## 🐍 Cross-Language gRPC Support (Python)

This framework supports interoperability with standard gRPC clients (e.g., Python), allowing non-Java clients to invoke services hosted by the RPC framework.

### Features
- **Standard gRPC Protocol**: Implements standard HTTP/2 transport compatible with widespread gRPC libraries (via `grpc-io`).
- **Protobuf Serialization**: Supports standard Protobuf `Empty`, `StringValue`, `Int32Value`, etc., via wrapper types for seamless data exchange.
- **Nacos Integation**: Services registered in Nacos can be discovered and invoked.

### Usage Guide

1. **Configure Java Server**:
   Update `rpc-config.yaml` to enable `grpc` protocol and `protobuf` serialization:
   ```yaml
   rpc:
     protocol: "grpc"
     serializer: "protobuf"
     registry: "nacos"
   ```
2. **Start the Java Provider (gRPC Mode)**:
   Use the helper script to start the server:
   ```bash
   ./run_server.sh
   # Or manually: java -cp ... com.xiaoyu.rpc.provider.ProviderApp
   ```

3. **Run the Python Client**:
   Use the helper script in the `python_client` directory:
   ```bash
   ./python_client/run_client.sh
   ```
   
   **Expected Output**:
   ```text
   RpcResponse received:
   Data: Hello, World! (from Multi-Module Netty Server)
   Message: Success
   ```
   
   **Expected Output**:
   ```text
   RpcResponse received:
   Data: Hello, World! (from Multi-Module Netty Server)
   Message: Success
   ```

---

## 🤝 Contributing

Contributions are welcome! Feel free to open issues or submit pull requests to improve the framework.
