package com.xiaoyu.rpc.core.server;

import com.xiaoyu.rpc.core.registry.ServiceRegistry;
import com.xiaoyu.rpc.core.transport.TransportServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RpcServer 启动发布顺序测试")
class RpcServerLifecycleTest {

    @Test
    @DisplayName("服务必须在传输层 bind 成功后才发布到注册中心")
    void testBindBeforeRegistryPublish() throws Exception {
        List<String> events = new ArrayList<>();
        RecordingRegistry registry = new RecordingRegistry(events);
        RecordingTransportServer transportServer = new RecordingTransportServer(events);
        RpcServer server = new RpcServer("127.0.0.1", 9090, registry, transportServer);

        server.register(DemoService.class, new DemoServiceImpl());
        assertTrue(events.isEmpty(), "register() 只登记本地服务，启动前不应向注册中心发布");

        server.start();

        assertEquals(List.of("bind", "publish:" + DemoService.class.getName()), events);
        server.close();
    }

    @Test
    @DisplayName("服务端启动后新增服务应立即发布")
    void testRegisterAfterStartedPublishesImmediately() throws Exception {
        List<String> events = new ArrayList<>();
        RecordingRegistry registry = new RecordingRegistry(events);
        RecordingTransportServer transportServer = new RecordingTransportServer(events);
        RpcServer server = new RpcServer("127.0.0.1", 9090, registry, transportServer);

        server.start();
        server.register(DemoService.class, new DemoServiceImpl());

        assertEquals(List.of("bind", "publish:" + DemoService.class.getName()), events);
        server.close();
    }

    interface DemoService {
        String hello();
    }

    static class DemoServiceImpl implements DemoService {
        @Override
        public String hello() {
            return "ok";
        }
    }

    private static final class RecordingRegistry implements ServiceRegistry {
        private final List<String> events;

        private RecordingRegistry(List<String> events) {
            this.events = events;
        }

        @Override
        public void registerService(String serviceName, InetSocketAddress inetSocketAddress) {
            events.add("publish:" + serviceName);
        }

        @Override
        public void clearRegistry() {
            // 生命周期顺序断言只关注 bind/publish。
        }
    }

    private static final class RecordingTransportServer implements TransportServer {
        private final List<String> events;

        private RecordingTransportServer(List<String> events) {
            this.events = events;
        }

        @Override
        public void start() {
            events.add("bind");
        }

        @Override
        public void stop() {
            // no-op
        }
    }
}
