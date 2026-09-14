package com.xiaoyu.rpc.core.server;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.config.RpcConfig;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NettyRpcHandler 业务执行测试")
class NettyRpcHandlerTest {

    private Serializer serializer;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("rpc.registry", "local");
        System.setProperty("rpc.serializer", "java");
        resetRpcConfigSingleton();
        serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("rpc.registry");
        System.clearProperty("rpc.serializer");
        resetRpcConfigSingleton();
    }

    @Test
    @DisplayName("基本类型参数可以正确解析并调用服务")
    void testPrimitiveParameterInvocation() {
        ServiceRepository.registerService(PrimitiveService.class.getName(), new PrimitiveServiceImpl());
        EmbeddedChannel channel = new EmbeddedChannel(new NettyRpcHandler(Runnable::run));

        RpcRequest request = RpcRequest.newBuilder()
                .setRequestId("primitive-1")
                .setInterfaceName(PrimitiveService.class.getName())
                .setMethodName("add")
                .addParamTypes("int")
                .addParamTypes("int")
                .addParameters(ByteString.copyFrom(serializer.serialize(20)))
                .addParameters(ByteString.copyFrom(serializer.serialize(22)))
                .build();

        channel.writeInbound(request);
        RpcResponse response = channel.readOutbound();

        assertNotNull(response);
        assertEquals("Success", response.getMessage());
        Integer result = serializer.deserialize(response.getData().toByteArray(), Integer.class);
        assertEquals(42, result);
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("业务方法在注入的业务线程中执行")
    void testBusinessInvocationRunsOffEventLoop() throws Exception {
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        ServiceRepository.registerService(ThreadService.class.getName(), new ThreadServiceImpl(invoked, threadName));

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "rpc-business-test"));
        EmbeddedChannel channel = new EmbeddedChannel(new NettyRpcHandler(executor));
        try {
            RpcRequest request = RpcRequest.newBuilder()
                    .setRequestId("thread-1")
                    .setInterfaceName(ThreadService.class.getName())
                    .setMethodName("currentThread")
                    .build();

            channel.writeInbound(request);

            assertTrue(invoked.await(1, TimeUnit.SECONDS), "Business method should be invoked");
            assertEquals("rpc-business-test", threadName.get());
        } finally {
            executor.shutdownNow();
            channel.finishAndReleaseAll();
        }
    }

    public interface PrimitiveService {
        int add(int left, int right);
    }

    public static class PrimitiveServiceImpl implements PrimitiveService {
        @Override
        public int add(int left, int right) {
            return left + right;
        }
    }

    public interface ThreadService {
        String currentThread();
    }

    public static class ThreadServiceImpl implements ThreadService {
        private final CountDownLatch invoked;
        private final AtomicReference<String> threadName;

        public ThreadServiceImpl(CountDownLatch invoked, AtomicReference<String> threadName) {
            this.invoked = invoked;
            this.threadName = threadName;
        }

        @Override
        public String currentThread() {
            threadName.set(Thread.currentThread().getName());
            invoked.countDown();
            return threadName.get();
        }
    }

    private static void resetRpcConfigSingleton() throws Exception {
        Field field = RpcConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
