package com.xiaoyu.rpc.benchmark;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.api.HelloService;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.server.ServiceRepository;
import com.xiaoyu.rpc.core.transport.TransportClient;
import com.xiaoyu.rpc.core.transport.netty.NettyTransportClient;
import com.xiaoyu.rpc.core.transport.netty.NettyTransportServer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({ Mode.Throughput, Mode.AverageTime })
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(8)
public class ProtocolBenchmark {

    private static final Logger log = LoggerFactory.getLogger(ProtocolBenchmark.class);

    @Param({ "netty", "http", "http2" })
    private String protocol;

    private NettyTransportServer server;
    private TransportClient client;
    private InetSocketAddress address;
    private RpcRequest request;
    private int port = 9091;

    @Setup
    public void setup() throws Exception {
        // Use a random port to avoid conflicts (Address already in use / TIME_WAIT)
        this.port = 10000 + new java.util.Random().nextInt(50000);

        // 通过反射覆盖本次基准测试的协议配置
        RpcConfig config = RpcConfig.getInstance();
        Field protocolField = RpcConfig.class.getDeclaredField("protocol");
        protocolField.setAccessible(true);
        protocolField.set(config, protocol);

        // Ensure Serializer is set to something known, e.g., "java" for arg
        // serialization
        Field serializerField = RpcConfig.class.getDeclaredField("serializerType");
        serializerField.setAccessible(true);
        serializerField.set(config, "java");

        // 注册测试服务实现
        ServiceRepository.registerService(HelloService.class.getName(), new HelloServiceImpl());

        // 启动服务端
        server = new NettyTransportServer(port);
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                log.error("Failed to start benchmark server on port {}", port, e);
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Wait for server to start
        TimeUnit.SECONDS.sleep(2);

        // 初始化客户端
        client = new NettyTransportClient();
        address = new InetSocketAddress("127.0.0.1", port);

        // 组装测试请求
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
        byte[] argBytes = serializer.serialize("Benchmark");

        request = RpcRequest.newBuilder()
                .setInterfaceName(HelloService.class.getName())
                .setMethodName("sayHello")
                .addParamTypes("java.lang.String")
                .addParameters(ByteString.copyFrom(argBytes))
                .build();
    }

    @TearDown
    public void teardown() {
        if (server != null) {
            server.stop();
        }
    }

    @Benchmark
    public Object benchmarkCall() {
        return client.sendRequest(request, address);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ProtocolBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    public static class HelloServiceImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello, " + name;
        }
    }
}
