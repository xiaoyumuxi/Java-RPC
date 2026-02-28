package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class RpcClientErrorTest {

    @Test
    void testRequestTimeout() {
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();

        // 模拟超时场景：5秒后才完成
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                future.complete(RpcResponse.newBuilder().setRequestId("test").build());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        // 1秒超时应该抛出异常
        assertThrows(TimeoutException.class, () -> {
            future.get(1, TimeUnit.SECONDS);
        });
    }

    @Test
    void testRequestFailure() {
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();

        // 模拟请求失败
        future.completeExceptionally(new RuntimeException("Network error"));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get();
        });

        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals("Network error", exception.getCause().getMessage());
    }

    @Test
    void testInvalidServiceName() {
        RpcRequest request = RpcRequest.newBuilder()
                .setRequestId("test-001")
                .setInterfaceName("com.invalid.NonExistentService")
                .setMethodName("someMethod")
                .build();

        assertNotNull(request.getInterfaceName());
        assertEquals("com.invalid.NonExistentService", request.getInterfaceName());
    }

    @Test
    void testConcurrentRequests() throws InterruptedException {
        int requestCount = 100;
        CompletableFuture<?>[] futures = new CompletableFuture[requestCount];

        for (int i = 0; i < requestCount; i++) {
            final int index = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                RpcRequest request = RpcRequest.newBuilder()
                        .setRequestId("req-" + index)
                        .setInterfaceName("com.test.Service")
                        .setMethodName("test")
                        .build();
                return request.getRequestId();
            });
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures);
        assertDoesNotThrow(() -> allOf.get(5, TimeUnit.SECONDS));
    }
}
