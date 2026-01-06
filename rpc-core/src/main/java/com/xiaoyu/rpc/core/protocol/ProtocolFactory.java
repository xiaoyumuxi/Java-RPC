package com.xiaoyu.rpc.core.protocol;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.core.protocol.netty.NettyProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtocolFactory {
    private static final Logger log = LoggerFactory.getLogger(ProtocolFactory.class);

    public static Protocol getProtocol(String name) {
        // 默认使用 Netty
        if (name == null || name.trim().isEmpty()) {
            name = "netty";
        }

        try {
            return ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(name);
        } catch (Exception e) {
            log.error("Failed to load protocol: " + name, e);
            // Fallback or rethrow? Let's use Netty as fallback for robustness if strictness
            // isn't required
            // OR rethrow to fail fast.
            // Given the original code had a default case, let's keep the fail-safe behavior
            // for now but log error.
            if (!"netty".equalsIgnoreCase(name)) {
                log.warn("Falling back to default NettyProtocol");
                return new NettyProtocol();
            }
            throw e;
        }
    }
}