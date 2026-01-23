package com.xiaoyu.rpc.core.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelProvider {
    private static final Logger log = LoggerFactory.getLogger(ChannelProvider.class);

    private static final Map<String, Channel> channels = new ConcurrentHashMap<>();

    public static Channel get(InetSocketAddress inetSocketAddress, Bootstrap bootstrap) {
        String key = inetSocketAddress.toString();
        // 1. 尝试从缓存获取
        if (channels.containsKey(key)) {
            Channel channel = channels.get(key);
            if (channel != null && channel.isActive()) {
                return channel;
            } else {
                channels.remove(key);
            }
        }

        // 2. 建立新连接
        Channel channel = connect(bootstrap, inetSocketAddress);

        // 3. 放入缓存
        if (channel != null) {
            channels.put(key, channel);
        }

        return channel;
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
