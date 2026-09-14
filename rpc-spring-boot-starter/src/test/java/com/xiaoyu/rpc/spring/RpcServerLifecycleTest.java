package com.xiaoyu.rpc.spring;

import com.xiaoyu.rpc.core.server.RpcServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("Spring RPC 生命周期测试")
class RpcServerLifecycleTest {

    @Test
    @DisplayName("SmartLifecycle 启动和停止委托给 RpcServer")
    void testStartAndStop() throws Exception {
        RpcServer rpcServer = mock(RpcServer.class);
        RpcAutoConfiguration.RpcServerLifecycle lifecycle =
                new RpcAutoConfiguration.RpcServerLifecycle(rpcServer);

        lifecycle.start();
        assertTrue(lifecycle.isRunning());
        verify(rpcServer, times(1)).start();

        lifecycle.stop();
        assertFalse(lifecycle.isRunning());
        verify(rpcServer, times(1)).close();

        lifecycle.stop();
        verify(rpcServer, times(1)).close();
    }

    @Test
    @DisplayName("RpcServer 启动失败时 Spring 生命周期保持未运行状态并向上抛错")
    void testStartupFailurePropagates() throws Exception {
        RpcServer rpcServer = mock(RpcServer.class);
        doThrow(new IllegalStateException("port occupied")).when(rpcServer).start();
        RpcAutoConfiguration.RpcServerLifecycle lifecycle =
                new RpcAutoConfiguration.RpcServerLifecycle(rpcServer);

        IllegalStateException error = assertThrows(IllegalStateException.class, lifecycle::start);

        assertTrue(error.getMessage().contains("port occupied"));
        assertFalse(lifecycle.isRunning());
    }
}
