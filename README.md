# 🚀 XiaoYu RPC Framework

> A lightweight, high-performance, and extensible RPC framework based on **Netty**, **Nacos**, and **ByteBuddy**.
> Supports multiple protocols including **HTTP/2**, **HTTP/1.1**, and custom **Netty** protocols.

![Java](https://img.shields.io/badge/Java-17%2B-blue?style=flat-square&logo=java)
![Netty](https://img.shields.io/badge/Netty-4.1.x-green?style=flat-square)
![Nacos](https://img.shields.io/badge/Nacos-2.x-orange?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

---

## 📖 Introduction

This project is a high-performance, pluggable RPC framework designed to demonstrate the convergence of standard protocols and dynamic invocation.
Unlike traditional RPC frameworks that bind tightly to a single protocol or require strict code generation for every service, **XiaoYu RPC** features a unique **"Universal gRPC Adpater"**. It implements the standard gRPC protocol (HTTP/2 + Protobuf) but routes requests dynamically to Java service implementations. This allows you to:

1. **Use standard gRPC clients** (like Python, Go, Node.js) to call your Java services directly.
2. **Retain Java's dynamic flexibility** (Reflection/ByteBuddy) without generating separate `.proto` service stubs for every business class.

## ✨ Key Features

- **🔌 Plugin-based Architecture**: Leverages a custom SPI mechanism for maximum flexibility.
- **🤝 Universal gRPC Compatibility**: A custom-implemented `GrpcProtocol` layer that runs standard gRPC on HTTP/2, proven to interoperate with official `grpc-python` clients.
- **⚡ Dynamic-Static Hybrid**: Combines the performance of Protobuf serialization (with custom Type Wrappers) and the flexibility of Java dynamic proxies.
- **📡 Multi-Protocol Support**: Choice of `Netty` (Custom), `HTTP/1.1`, or `gRPC` (HTTP/2) for communication.
- **⚡ High-Performance Proxy**: Uses **ByteBuddy** for dynamic proxy generation, optimized for Java 17+.
- **⚖️ Intelligent Load Balancing**: Includes `RoundRobin` and `Random` strategies.
- **📦 Diverse Serialization**: Supports `Protobuf` (Enhanced with Scalar Wrappers), `Kryo`, `JSON`, and standard `Java` serialization.
- **🔍 Service Discovery**: Integrated with **Nacos** for robust service registry and discovery.

---

## 🔬 Technical Deep Dive: gRPC Protocol Implementation

The core of XiaoYu RPC's interoperability lies in its custom implementation of the gRPC wire protocol over Netty's HTTP/2 stack.

![gRPC Data Processing Flow](docs/images/grpc_processing_flow.png)

### 1. Wire Format (5-Byte Header)

Every gRPC message is prefixed with a 5-byte header, handled directly in `GrpcServerHandler`:

- **Compression Flag (1 Byte)**: `0` (Uncompressed) or `1` (Compressed).
- **Message Length (4 Bytes)**: Big-endian integer specifying the length of the following Protobuf payload.
- **Payload**: Standard Protobuf binary data, deserialized via `NativeProtobufSerializer`.

### 2. Header Alignment

Strict adherence to gRPC HTTP/2 headers ensures compatibility:

- **:status**: `200` (HTTP level success)
- **content-type**: `application/grpc` (Crucial for client recognition)
- **te**: `trailers`

### 3. Trailer & Status

gRPC uses HTTP/2 Trailers to convey the final RPC status, distinct from the HTTP status code.

- **HEADERS Frame (EndStream=true)**: Sent after the data payload.
- **grpc-status**: `0` for OK, non-zero for errors.
- **grpc-message**: Descriptive error message.

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

Execute the following commands to start the Java RPC Provider. This will build the project and register the `HelloService` to your local Nacos instance.

```bash
# 1. Build Project
mvn clean package -DskipTests

# 2. Start Provider
java -cp rpc-provider/target/rpc-provider-1.0-SNAPSHOT.jar:rpc-core/target/rpc-core-1.0-SNAPSHOT.jar:rpc-common/target/rpc-common-1.0-SNAPSHOT.jar:rpc-api/target/rpc-api-1.0-SNAPSHOT.jar:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -pl rpc-provider -am) com.xiaoyu.rpc.provider.ProviderApp
```

### 3. Run the Consumer

Execute the `ConsumerApp` in the `rpc-consumer` module to make calls to the provider.

```bash
java -cp rpc-consumer/target/rpc-consumer-1.0-SNAPSHOT.jar:rpc-core/target/rpc-core-1.0-SNAPSHOT.jar:rpc-common/target/rpc-common-1.0-SNAPSHOT.jar:rpc-api/target/rpc-api-1.0-SNAPSHOT.jar:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -pl rpc-consumer -am) com.xiaoyu.rpc.consumer.ConsumerApp
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
  serializer: KRYO           # Serializer: PROTOBUF, KRYO, JAVA, JSON
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
   Run the following commands to build the project and start the server:

   ```bash
   # Build the project (skip tests to speed up)
   mvn clean package -DskipTests

   # Run the Provider
   java -cp rpc-provider/target/rpc-provider-1.0-SNAPSHOT.jar:rpc-core/target/rpc-core-1.0-SNAPSHOT.jar:rpc-common/target/rpc-common-1.0-SNAPSHOT.jar:rpc-api/target/rpc-api-1.0-SNAPSHOT.jar:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -pl rpc-provider -am) com.xiaoyu.rpc.provider.ProviderApp
   ```
3. **Run the Python Client**:
   Navigate to the `python_client` directory and set up the environment:

   ```bash
   cd python_client

   # Create and valid virtual environment
   python3 -m venv venv
   source venv/bin/activate

   # Install dependencies
   pip install grpcio grpcio-tools protobuf

   # Run the client
   python3 client.py
   ```

   **Expected Output**:

   ```text
   RpcResponse received:
   Data: Hello, World! (from Multi-Module Netty Server)
   Message: Success
   ```
## 🔄 Continuous Integration & Delivery (CI/CD)

To ensure system reliability and code quality, this project integrates a robust CI/CD pipeline using **GitHub Actions**. This pipeline automatically validates the build process and runs integration tests upon every push and pull request.

**Key Workflows:**
- **Automated Testing**: Runs unit and integration tests to verify RPC functionality.
- **Service Verification**: Launches Nacos, the Java Provider, and Python Client in a containerized environment to test cross-language interoperability.
- **Build Status**: Provides immediate feedback on code health via GitHub Actions.

![CI/CD Workflow Result](docs/images/image.png)


---

## 🤝 Contributing

Contributions are welcome! Feel free to open issues or submit pull requests to improve the framework.
