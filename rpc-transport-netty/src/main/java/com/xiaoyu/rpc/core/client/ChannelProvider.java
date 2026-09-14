package com.xiaoyu.rpc.core.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单个 Netty 客户端实例使用的连接池。
 * 缓存已建立连接，并对同一地址的并发首次建连进行去重。
 */
public final class ChannelProvider implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ChannelProvider.class);

    @FunctionalInterface
    interface ChannelConnector {
        ChannelFuture connect(Bootstrap bootstrap, InetSocketAddress address);
    }

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Channel>> connectingChannels = new ConcurrentHashMap<>();
    private final LinkedHashMap<String, Long> lruTracker = new LinkedHashMap<>(16, 0.75f, true);
    private final int maxConnections;
    private final ChannelConnector connector;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ChannelProvider(int maxConnections) {
        this(maxConnections, (bootstrap, address) -> bootstrap.connect(address));
    }

    ChannelProvider(int maxConnections, ChannelConnector connector) {
        this.maxConnections = Math.max(1, maxConnections);
        this.connector = connector;
    }

    public CompletableFuture<Channel> get(InetSocketAddress address, Bootstrap bootstrap) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("ChannelProvider 已关闭"));
        }

        String key = key(address);
        Channel cached = channels.get(key);
        if (cached != null) {
            if (cached.isActive()) {
                touch(key);
                return CompletableFuture.completedFuture(cached);
            }
            removeChannel(key, cached);
        }

        CompletableFuture<Channel> existing = connectingChannels.get(key);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<Channel> candidate = new CompletableFuture<>();
        existing = connectingChannels.putIfAbsent(key, candidate);
        if (existing != null) {
            return existing;
        }

        candidate.whenComplete((channel, throwable) -> connectingChannels.remove(key, candidate));
        connect(key, address, bootstrap, candidate);
        return candidate;
    }

    private void connect(String key, InetSocketAddress address, Bootstrap bootstrap,
            CompletableFuture<Channel> result) {
        if (closed.get()) {
            result.completeExceptionally(new IllegalStateException("ChannelProvider 已关闭"));
            return;
        }

        try {
            connector.connect(bootstrap, address).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    Throwable cause = future.cause() != null
                            ? future.cause()
                            : new RuntimeException("客户端连接失败: " + address);
                    log.error("客户端连接失败: {}", address, cause);
                    result.completeExceptionally(cause);
                    return;
                }

                Channel channel = future.channel();
                if (closed.get() || !cacheChannel(key, channel)) {
                    channel.close();
                    result.completeExceptionally(new IllegalStateException("ChannelProvider 已关闭"));
                    return;
                }

                log.info("客户端连接成功: {}", address);
                result.complete(channel);
            });
        } catch (Exception e) {
            result.completeExceptionally(e);
        }
    }

    private boolean cacheChannel(String key, Channel channel) {
        synchronized (lruTracker) {
            if (closed.get()) {
                return false;
            }

            while (channels.size() >= maxConnections && !lruTracker.isEmpty()) {
                evictLRU();
            }

            channels.put(key, channel);
            lruTracker.put(key, System.currentTimeMillis());
        }

        channel.closeFuture().addListener(ignored -> removeChannel(key, channel));
        return true;
    }

    private void touch(String key) {
        synchronized (lruTracker) {
            if (channels.containsKey(key)) {
                lruTracker.put(key, System.currentTimeMillis());
            }
        }
    }

    private void removeChannel(String key, Channel expected) {
        synchronized (lruTracker) {
            if (channels.remove(key, expected)) {
                lruTracker.remove(key);
            }
        }
    }

    private void evictLRU() {
        String oldestKey = lruTracker.keySet().iterator().next();
        Channel oldChannel = channels.remove(oldestKey);
        lruTracker.remove(oldestKey);
        if (oldChannel != null) {
            oldChannel.close();
        }
        log.info("连接池已满，淘汰最久未使用的连接: {}", oldestKey);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        IllegalStateException closeCause = new IllegalStateException("ChannelProvider 已关闭");
        connectingChannels.values().forEach(future -> future.completeExceptionally(closeCause));
        connectingChannels.clear();

        List<Channel> snapshot;
        synchronized (lruTracker) {
            snapshot = new ArrayList<>(channels.values());
            channels.clear();
            lruTracker.clear();
        }
        snapshot.forEach(Channel::close);
    }

    int cachedChannelCount() {
        return channels.size();
    }

    int connectingChannelCount() {
        return connectingChannels.size();
    }

    private static String key(InetSocketAddress address) {
        return address.getHostString() + ':' + address.getPort();
    }
}
