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

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({ Mode.Throughput, Mode.AverageTime })
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
public class ProtocolBenchmark {

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

        // 1. Set Protocol via Reflection
        RpcConfig config = RpcConfig.getInstance();
        Field protocolField = RpcConfig.class.getDeclaredField("protocol");
        protocolField.setAccessible(true);
        protocolField.set(config, protocol);

        // Ensure Serializer is set to something known, e.g., "java" for arg
        // serialization
        Field serializerField = RpcConfig.class.getDeclaredField("serializerType");
        serializerField.setAccessible(true);
        serializerField.set(config, "java");

        // 2. Register Service
        ServiceRepository.registerService(HelloService.class.getName(), new HelloServiceImpl());

        // 3. Start Server
        server = new NettyTransportServer(port);
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace(); // Log server startup errors
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Wait for server to start
        TimeUnit.SECONDS.sleep(2);

        // 4. Setup Client
        client = new NettyTransportClient();
        address = new InetSocketAddress("127.0.0.1", port);

        // 5. Build Request
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
