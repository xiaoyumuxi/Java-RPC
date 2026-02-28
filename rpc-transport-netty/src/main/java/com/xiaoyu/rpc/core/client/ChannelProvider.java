package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.core.config.RpcConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelProvider {
    private static final Logger log = LoggerFactory.getLogger(ChannelProvider.class);

    private static final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private static final LinkedHashMap<String, Long> lruTracker = new LinkedHashMap<>(16, 0.75f, true);
    private static final int MAX_CONNECTIONS = RpcConfig.getInstance().getMaxConnections();

    public static Channel get(InetSocketAddress inetSocketAddress, Bootstrap bootstrap) {
        String key = inetSocketAddress.toString();
        // 先尝试复用已有连接
        if (channels.containsKey(key)) {
            Channel channel = channels.get(key);
            if (channel != null && channel.isActive()) {
                synchronized (lruTracker) {
                    lruTracker.put(key, System.currentTimeMillis());
                }
                return channel;
            } else {
                removeChannel(key);
            }
        }

        // 缓存不可用时再新建连接
        Channel channel = connect(bootstrap, inetSocketAddress);

        // 新连接建立成功后放回缓存
        if (channel != null) {
            addChannel(key, channel);
        }

        return channel;
    }

    private static void addChannel(String key, Channel channel) {
        synchronized (lruTracker) {
            if (channels.size() >= MAX_CONNECTIONS) {
                evictLRU();
            }
            channels.put(key, channel);
            lruTracker.put(key, System.currentTimeMillis());
        }
    }

    private static void removeChannel(String key) {
        synchronized (lruTracker) {
            channels.remove(key);
            lruTracker.remove(key);
        }
    }

    private static void evictLRU() {
        if (lruTracker.isEmpty()) {
            return;
        }
        String oldestKey = lruTracker.keySet().iterator().next();
        Channel oldChannel = channels.remove(oldestKey);
        lruTracker.remove(oldestKey);
        if (oldChannel != null && oldChannel.isActive()) {
            oldChannel.close();
        }
        log.info("连接池已满，淘汰最久未使用的连接: {}", oldestKey);
    }

    private static Channel connect(Bootstrap bootstrap, InetSocketAddress inetSocketAddress) {
        CountDownLatch latch = new CountDownLatch(1);
        final Channel[] channelHolder = new Channel[1];

        bootstrap.connect(inetSocketAddress).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("客户端连接成功: " + inetSocketAddress);
                channelHolder[0] = future.channel();
            } else {
                log.error("客户端连接失败: " + inetSocketAddress);
            }
            latch.countDown();
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return channelHolder[0];
    }
}
