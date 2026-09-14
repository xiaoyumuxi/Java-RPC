package com.xiaoyu.rpc.core.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ChannelProvider 并发建连与生命周期测试")
class ChannelProviderTest {

    @Test
    @DisplayName("同一地址的并发首次获取共享一个 in-flight 连接")
    void testDeduplicateInFlightConnection() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultChannelPromise connectPromise = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        AtomicInteger connectAttempts = new AtomicInteger();
        ChannelProvider provider = new ChannelProvider(8, (bootstrap, address) -> {
            connectAttempts.incrementAndGet();
            return connectPromise;
        });
        Bootstrap bootstrap = new Bootstrap();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 18080);

        CompletableFuture<Channel> first = provider.get(address, bootstrap);
        CompletableFuture<Channel> second = provider.get(address, bootstrap);
        CompletableFuture<Channel> third = provider.get(address, bootstrap);

        assertEquals(1, connectAttempts.get());
        assertEquals(1, provider.connectingChannelCount());
        assertFalse(first.isDone());

        connectPromise.setSuccess();

        assertSame(channel, first.join());
        assertSame(channel, second.join());
        assertSame(channel, third.join());
        assertEquals(1, connectAttempts.get());
        assertEquals(1, provider.cachedChannelCount());
        assertEquals(0, provider.connectingChannelCount());

        provider.close();
        assertFalse(channel.isOpen());
    }

    @Test
    @DisplayName("关闭时失败正在建立的连接并拒绝后续获取")
    void testCloseFailsConnectingAndRejectsNewGet() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultChannelPromise connectPromise = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        ChannelProvider provider = new ChannelProvider(8, (bootstrap, address) -> connectPromise);
        Bootstrap bootstrap = new Bootstrap();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 18081);

        CompletableFuture<Channel> connecting = provider.get(address, bootstrap);
        provider.close();

        assertTrue(connecting.isCompletedExceptionally());
        assertThrows(CompletionException.class, connecting::join);

        connectPromise.setSuccess();
        assertFalse(channel.isOpen(), "关闭后才完成的连接必须立即关闭");

        CompletableFuture<Channel> afterClose = provider.get(address, bootstrap);
        assertThrows(CompletionException.class, afterClose::join);
    }
}
