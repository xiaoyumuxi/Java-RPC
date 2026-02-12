package com.xiaoyu.rpc.core.protocol;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.core.protocol.netty.NettyProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtocolFactory {
    private static final Logger log = LoggerFactory.getLogger(ProtocolFactory.class);

    public static Protocol getProtocol(String name) {
        // 配置为空时使用默认协议，保证最小可用
        if (name == null || name.trim().isEmpty()) {
            name = "netty";
        }

        try {
            return ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(name);
        } catch (Exception e) {
            log.error("Failed to load protocol: " + name, e);
            // 非默认协议加载失败时，兜底回退到 Netty，避免服务直接不可用
            if (!"netty".equalsIgnoreCase(name)) {
                log.warn("Falling back to default NettyProtocol");
                return new NettyProtocol();
            }
            // 默认协议都加载失败，继续抛出异常交给上层处理
            throw e;
        }
    }
}
