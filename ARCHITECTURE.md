# gRPC 架构图解

## 整体架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          gRPC 通信流程                                    │
└─────────────────────────────────────────────────────────────────────────┘

客户端 (Client)                                    服务端 (Server)
┌──────────────────────┐                          ┌──────────────────────┐
│                      │                          │                      │
│  ┌────────────────┐  │                          │  ┌────────────────┐  │
│  │ GrpcClient.java│  │                          │  │GrpcServer.java │  │
│  │                │  │                          │  │                │  │
│  │ - getUserExample│                            │  │ - start()      │  │
│  │ - listUsersEx...│                            │  │ - stop()       │  │
│  └───────┬────────┘  │                          │  └───────┬────────┘  │
│          │           │                          │          │           │
│          ▼           │                          │          ▼           │
│  ┌────────────────┐  │                          │  ┌────────────────┐  │
│  │ Stub           │  │                          │  │UserServiceImpl │  │
│  │                │  │                          │  │                │  │
│  │ - BlockingStub │  │                          │  │ - getUser()    │  │
│  │ - AsyncStub    │  │                          │  │ - listUsers()  │  │
│  └───────┬────────┘  │                          │  │ - createUsers()│  │
│          │           │                          │  │ - chat()       │  │
│          ▼           │                          │  └───────▲────────┘  │
│  ┌────────────────┐  │                          │          │           │
│  │ Channel        │  │      HTTP/2 连接          │  ┌───────┴────────┐  │
│  │ (ManagedChannel│◄─┼──────────────────────────┼─►│  ServerImpl    │  │
│  └───────┬────────┘  │                          │  └───────┬────────┘  │
│          │           │                          │          │           │
│          ▼           │                          │          ▼           │
│  ┌────────────────┐  │                          │  ┌────────────────┐  │
│  │   Netty        │  │                          │  │    Netty       │  │
│  │  Transport     │  │   Protocol Buffers       │  │  Transport     │  │
│  └────────────────┘  │   二进制数据传输           │  └────────────────┘  │
│                      │                          │                      │
└──────────────────────┘                          └──────────────────────┘
```

## 四种 RPC 通信模式流程图

### 1. Unary RPC (一元调用)

```
Client                                          Server
  │                                               │
  │  GetUserRequest(userId=1)                     │
  ├──────────────────────────────────────────────►│
  │                                               │
  │                                        ┌──────┴──────┐
  │                                        │ 查找用户数据  │
  │                                        └──────┬──────┘
  │                                               │
  │            UserResponse(name="Alice")         │
  │◄──────────────────────────────────────────────┤
  │                                               │
  ▼                                               ▼
完成                                            完成
```

### 2. Server Streaming RPC (服务端流式)

```
Client                                          Server
  │                                               │
  │  ListUsersRequest(pageSize=3)                 │
  ├──────────────────────────────────────────────►│
  │                                               │
  │                                        ┌──────┴──────┐
  │                                        │ 准备用户列表  │
  │                                        └──────┬──────┘
  │            UserResponse(User1)                │
  │◄──────────────────────────────────────────────┤
  │                                               │
  │            UserResponse(User2)                │
  │◄──────────────────────────────────────────────┤
  │                                               │
  │            UserResponse(User3)                │
  │◄──────────────────────────────────────────────┤
  │                                               │
  │            END_STREAM                         │
  │◄──────────────────────────────────────────────┤
  │                                               │
  ▼                                               ▼
完成                                            完成
```

### 3. Client Streaming RPC (客户端流式)

```
Client                                          Server
  │                                               │
  │  CreateUserRequest(Alice)                     │
  ├──────────────────────────────────────────────►│
  │                                               │
  │  CreateUserRequest(Bob)                       │
  ├──────────────────────────────────────────────►│
  │                                        ┌──────┴──────┐
  │                                        │   存储用户   │
  │  CreateUserRequest(Charlie)            └──────┬──────┘
  ├──────────────────────────────────────────────►│
  │                                               │
  │  END_STREAM                                   │
  ├──────────────────────────────────────────────►│
  │                                        ┌──────┴──────┐
  │                                        │   处理完成   │
  │                                        └──────┬──────┘
  │       CreateUsersResponse(count=3)            │
  │◄──────────────────────────────────────────────┤
  │                                               │
  ▼                                               ▼
完成                                            完成
```

### 4. Bidirectional Streaming RPC (双向流式)

```
Client                                          Server
  │                                               │
  │  ChatMessage("Hello")                         │
  ├──────────────────────────────────────────────►│
  │                                               │
  │         ChatMessage("Echo: Hello")            │
  │◄──────────────────────────────────────────────┤
  │                                               │
  │         ChatMessage("Welcome!")               │
  │◄──────────────────────────────────────────────┤
  │                                               │
  │  ChatMessage("How are you?")                  │
  ├──────────────────────────────────────────────►│
  │                                               │
  │         ChatMessage("Echo: How are you?")     │
  │◄──────────────────────────────────────────────┤
  │                                               │
  │  ChatMessage("Goodbye")                       │
  ├──────────────────────────────────────────────►│
  │                                               │
  │  END_STREAM                                   │
  ├──────────────────────────────────────────────►│
  │                                               │
  │         ChatMessage("Goodbye!")               │
  │◄──────────────────────────────────────────────┤
  │                                               │
  │         END_STREAM                            │
  │◄──────────────────────────────────────────────┤
  │                                               │
  ▼                                               ▼
完成                                            完成
```

## Protocol Buffers 编译流程

```
┌─────────────────────┐
│ user_service.proto  │  定义服务和消息
└──────────┬──────────┘
           │
           ▼
    ┌──────────────┐
    │    protoc    │  Protocol Buffers 编译器
    └──────┬───────┘
           │
           ├─────────────────┬─────────────────┐
           ▼                 ▼                 ▼
    ┌─────────────┐   ┌─────────────┐  ┌──────────────┐
    │ Message类    │   │ Service基类  │  │  Stub 类     │
    │             │   │             │  │              │
    │UserResponse │   │UserService  │  │BlockingStub  │
    │GetUserReq   │   │  ImplBase   │  │AsyncStub     │
    │...          │   │             │  │FutureStub    │
    └─────────────┘   └─────────────┘  └──────────────┘
```

## 数据流转过程

### 请求发送 (Client → Server)

```
1. 客户端调用
   GrpcClient.getUserExample(1)
           │
           ▼
2. Stub 处理
   blockingStub.getUser(request)
           │
           ▼
3. 序列化
   GetUserRequest → Protobuf 二进制
   [0x08 0x01] (userId = 1)
           │
           ▼
4. 封装 HTTP/2
   HEADERS: :method=POST, :path=/UserService/GetUser
   DATA: [Length][Protobuf二进制]
           │
           ▼
5. 网络传输
   Netty → TCP → 服务器
```

### 响应返回 (Server → Client)

```
1. 服务端接收
   HTTP/2 Frame → 解析请求
           │
           ▼
2. 反序列化
   Protobuf 二进制 → GetUserRequest 对象
           │
           ▼
3. 业务处理
   UserServiceImpl.getUser(request)
   查询数据库/内存
           │
           ▼
4. 序列化响应
   UserResponse → Protobuf 二进制
           │
           ▼
5. 发送响应
   HTTP/2 DATA Frame
           │
           ▼
6. 客户端接收
   反序列化 → UserResponse 对象
```

## StreamObserver 状态机

```
                    ┌──────────────┐
                    │   Created    │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
              ┌────►│    Active    │
              │     └──────┬───────┘
              │            │
              │            ├─── onNext() ────┐
              │            │                 │
              └────────────┘                 │
                                             │
                           ┌─────────────────┘
                           │
                           ├─── onError() ───►┌──────────────┐
                           │                  │    Error     │
                           │                  └──────────────┘
                           │
                           └─── onCompleted() ┌──────────────┐
                                             ►│  Completed   │
                                              └──────────────┘
```

## 连接管理

```
Client                      Channel Pool              Server
  │                              │                        │
  │  getUser()                   │                        │
  ├──────────►获取连接            │                        │
  │           │                  │                        │
  │           ├── 检查连接池       │                        │
  │           │                  │                        │
  │           ├── 复用已有连接 ───┼───────HTTP/2 Stream 1 ─►│
  │◄──────────┤                  │                        │
  │                              │                        │
  │  listUsers()                 │                        │
  ├──────────►获取连接            │                        │
  │           │                  │                        │
  │           ├── 复用同一连接 ───┼───────HTTP/2 Stream 2 ─►│
  │◄──────────┤                  │                        │
  │                              │                        │
  │  关闭                        │                        │
  ├──────────►释放连接            │                        │
              └── 保持连接池      │                        │
                   (可配置)       │                        │
```

## HTTP/2 多路复用

```
单个 TCP 连接
┌────────────────────────────────────────────────┐
│                                                │
│  Stream 1: GetUser 请求                         │
│  ├─ HEADERS Frame                              │
│  ├─ DATA Frame (Request)                       │
│  └─ DATA Frame (Response)                      │
│                                                │
│  Stream 2: ListUsers 请求                       │
│  ├─ HEADERS Frame                              │
│  ├─ DATA Frame (Request)                       │
│  ├─ DATA Frame (Response 1)                    │
│  ├─ DATA Frame (Response 2)                    │
│  └─ DATA Frame (Response 3)                    │
│                                                │
│  Stream 3: CreateUsers 请求                     │
│  ├─ HEADERS Frame                              │
│  ├─ DATA Frame (Request 1)                     │
│  ├─ DATA Frame (Request 2)                     │
│  └─ DATA Frame (Response)                      │
│                                                │
│  Stream 4: Chat 请求                            │
│  ├─ HEADERS Frame                              │
│  ├─ DATA Frame ◄──► DATA Frame                │
│  ├─ DATA Frame ◄──► DATA Frame                │
│  └─ DATA Frame ◄──► DATA Frame                │
│                                                │
└────────────────────────────────────────────────┘

优势:
- 无需建立多个连接
- 减少延迟
- 更好的网络利用率
```

## 实现自己的 RPC - 架构对照

```
┌──────────────────────────────────────────────────────────────┐
│                       你的 RPC 框架                            │
└──────────────────────────────────────────────────────────────┘

gRPC 组件              →    你的实现
─────────────────────────────────────────────────────────
.proto 文件            →    Java 接口 + 注解
Protocol Buffers       →    JSON / 自定义序列化
HTTP/2 (Netty)        →    TCP (Netty)
ServerBuilder         →    RpcServer 类
Service ImplBase      →    接口实现类
Stub                  →    JDK 动态代理
StreamObserver        →    自己的 Observer 接口
Channel               →    连接池管理
```

## 完整调用链路示例

```
完整的 getUser(1) 调用链路:

1. GrpcClient.getUserExample(1)
   │
   ▼
2. GetUserRequest.newBuilder().setUserId(1).build()
   │
   ▼
3. blockingStub.getUser(request)
   │
   ▼
4. ClientCalls.blockingUnaryCall(channel, method, request)
   │
   ▼
5. NettyClientHandler.write(HTTP/2 Frames)
   │
   ▼
6. [网络传输 TCP/IP]
   │
   ▼
7. NettyServerHandler.channelRead(HTTP/2 Frames)
   │
   ▼
8. ServerCalls.UnaryRequestMethod.invoke()
   │
   ▼
9. UserServiceImpl.getUser(request, responseObserver)
   │
   ▼
10. 查询数据: users.get(request.getUserId())
   │
   ▼
11. responseObserver.onNext(userResponse)
   │
   ▼
12. responseObserver.onCompleted()
   │
   ▼
13. NettyServerHandler.write(HTTP/2 Response)
   │
   ▼
14. [网络传输 TCP/IP]
   │
   ▼
15. NettyClientHandler.channelRead(HTTP/2 Response)
   │
   ▼
16. 返回给调用者: UserResponse
```

这个架构图展示了 gRPC 的核心工作原理，帮助你理解如何实现自己的 RPC 框架！
