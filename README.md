# RPC Framework with Nacos Registry

这是一个简单的 RPC 框架实现，使用 **Nacos** 作为服务注册中心。

## 1. 环境准备

### 启动 Nacos (Docker 方式)
本项目默认连接本地的 Nacos (127.0.0.1:8848)。推荐使用 Docker 快速启动 Nacos 单机模式。

请确保已安装 Docker，然后在终端执行以下命令：

```bash
# 拉取并启动 Nacos (单机模式)
docker run --name nacos-standalone \
    -e MODE=standalone \
    -p 8848:8848 \
    -p 9848:9848 \
    -d nacos/nacos-server:v2.3.1-slim
```

> **注意**: 
> - `-e MODE=standalone` 是必须的，否则 Nacos 会默认以集群模式启动并报错。
> - 端口 `8848` 是主端口，`9848` 是 gRPC 通信端口（Nacos 2.x 版本需要）。

### 验证 Nacos 是否启动成功
访问控制台：[http://127.0.0.1:8848/nacos](http://127.0.0.1:8848/nacos)
- 默认账号: `nacos`
- 默认密码: `nacos`

---

## 2. 项目配置

默认配置在 `src/main/resources/rpc-config.yaml` 中：

```yaml
rpc:
  # 服务端口
  server-port: 8080
  # 注册中心地址
  registry-address: "127.0.0.1:8848"
```

如果你的 Nacos 部署在其他机器，请修改 `registry-address`。

---

## 3. 运行指南

### 启动服务端 (Provider)
运行 `src/main/java/service/RpcServer.java` 的 `main` 方法。
- 控制台将显示 `RPC Server started on port 8080...`
- 在 Nacos 控制台的 "服务管理" -> "服务列表" 中，你应该能看到名为 `service.HelloService` 的服务。

### 启动客户端 (Consumer)
运行 `src/main/java/client/RpcClientProxy.java` 或者测试类（如 `src/main/java/Test/SpiTest.java`，视具体测试代码而定）。
- 客户端会自动从 Nacos 发现服务地址并发起调用。

---

## 4. 常见问题

**Q: 连接 Nacos 报错？**
A: 
1. 检查 Docker 容器是否正常运行 (`docker ps`)。
2. 检查 8848 和 9848 端口是否被占用。
3. 确保 `MODE=standalone` 环境变量已设置。

**Q: 客户端找不到服务？**
A: 请确认服务端已成功启动，并且在 Nacos 控制台中可以看到服务处于“健康”状态。
