package protocol;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProtocolFactory {

    public static Protocol getProtocol(String name) {
        if (name == null || name.trim().isEmpty()) {
            return new NettyProtocol();
        }

        switch (name.toLowerCase()) {
            case "netty":
                return new NettyProtocol();
            // case "http": return new HttpProtocol(); // 以后写
            default:
                log.warn("未知协议: {}, 默认使用 Netty", name);
                return new NettyProtocol();
        }
    }
}