package protocol;

import lombok.extern.slf4j.Slf4j;
import protocol.Http.HttpProtocol;
import protocol.Netty.NettyProtocol;

@Slf4j
public class ProtocolFactory {

    public static Protocol getProtocol(String name) {
        if (name == null || name.trim().isEmpty()) {
            return new NettyProtocol();
        }

        switch (name.toLowerCase()) {
            case "netty":
                return new NettyProtocol();
            case "http":
                return new HttpProtocol();
            default:
                log.warn("未知协议: {}, 默认使用 Netty", name);
                return new NettyProtocol();
        }
    }
}