# 🚀 RPC Framework

> A lightweight, extensible RPC framework based on Netty, Nacos, and dynamic proxies.
> **Added support for HTTP2&HTTP1.1 protocol.**<br>
> **The purpose is to imitate the idea of gRPC to implement an RPC framework.**

![Java](https://img.shields.io/badge/Java-17%2B-blue)
![Nacos](https://img.shields.io/badge/Nacos-Registry-orange)
![Netty](https://img.shields.io/badge/Netty-Networking-green)
![Protobuf](https://img.shields.io/badge/Protobuf-Serialization-red)

## ✨ Authorization & Features

This project demonstrates the core principles of an RPC framework with a highly modular design using SPI (Service Provider Interface).

- **🔌 Plugin-based Architecture (SPI)**: All major components (Registry, Proxy, LoadBalancer) are loaded via SPI.
- **🔄 Proxy Generation**:
    - **JDK Dynamic Proxy**: Standard implementation for interface-based proxying.
    - **ByteBuddy**: Modern, high-performance proxy generation (works seamlessly on Java 17+ without extra JVM flags).
- **⚖️ Load Balancing**:
    - **RoundRobin**: Evenly distributes traffic across providers.
    - **Random**: Randomly selects a provider.
- **📦 Serialization**: Supports **Protobuf**, **Kryo**, and **Java** serialization.
- **📡 Protocol**: Custom protocol on top of Netty / HTTP2 support.

---

## 🛠️ Configuration

The framework is fully configurable via `src/main/resources/rpc-config.yaml`.

```yaml
rpc:
  # ---------------------------------------------------------
  # Core Network Config
  # ---------------------------------------------------------
  protocol: "http2"          # Protocol: netty, http, http2
  server-host: 127.0.0.1     # Server binding address
  server-port: 8080          # Server binding port

  # ---------------------------------------------------------
  # Service Registry (Nacos)
  # ---------------------------------------------------------
  registry: "nacos"          # Registry type
  registry-address: "127.0.0.1:8848" # Nacos address

  # ---------------------------------------------------------
  # Client Side Settings
  # ---------------------------------------------------------
  # Serializer: PROTOBUF, KRYO, JAVA
  serializer: KRYO

  # Proxy Strategy:
  # - jdk: Standard JDK Dynamic Proxy
  # - bytebuddy: High-perf proxy (Recommended for Java 17+)
  proxy: bytebuddy

  # Load Balancer:
  # - roundrobin: Cyclic selection
  # - random: Random selection
  load-balancer: roundrobin
```

---

## 🚀 Quick Start

### 1. Start Nacos (Docker)

Use Docker to start a standalone Nacos server for service discovery.

```bash
docker run --name nacos-standalone \
    -e MODE=standalone \
    -p 8848:8848 \
    -p 9848:9848 \
    -d nacos/nacos-server:v2.3.1-slim
```

### 2. Start RPC Server (Provider)

Run the `main` method in `src/main/java/service/RpcServer.java`.

- It will register `HelloService` to Nacos.
- Listens on port `8080`.

### 3. Start RPC Client (Consumer)

Run the test client to verify the call (and load balancing).

```bash
mvn exec:java -Dexec.mainClass="Test.Http2SimpleTest"
```

Or run the Load Balancer test specifically:

```bash
mvn exec:java -Dexec.mainClass="Test.LoadBalancerTest"
```

---

## 📂 Project Structure

```text
src/main/java
├── client          # Client proxy & request logic (SPI implementations for Jdk/ByteBuddy)
├── config          # Configuration loading (Yaml)
├── extension       # Custom SPI loader (similar to Dubbo)
├── loadbalancer    # Load balancing strategies (Random, RoundRobin)
├── protocol        # Network protocols (Netty, Http2)
├── registry        # Service discovery & registration (Nacos)
└── service         # Service interfaces & implementations
```

---

## ❓ FAQ

**Q: Why use ByteBuddy instead of CGLIB?**
A: CGLIB is deprecated and causes `InaccessibleObjectException` on Java 17+ due to module system encapsulation. ByteBuddy is modern, maintained, and works out-of-the-box.

**Q: Connection Refuse to 9848?**
A: Nacos 2.x uses gRPC on port `9848`. Ensure you mapped both `8848` and `9848` when running Docker.

---

