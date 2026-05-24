package com.xiaoyu.rpc.core.protocol;

import com.xiaoyu.rpc.core.server.NettyRpcHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 协议嗅探器 —— 服务端自动识别多种协议
 * <p>
 * 原理：连接建立后，偷看（peek）入站数据的前几个字节，根据特征判断协议类型：
 * <ul>
 * <li>0xAABBCCDD → TCP 私有协议（NettyProtocol）</li>
 * <li>0x50524920 ("PRI ") → HTTP/2 Connection Preface（Http2Protocol 或
 * GrpcProtocol）</li>
 * <li>HTTP 方法名（GET / POST / PUT / HEAD / DELETE / OPTIONS / PATCH）→
 * HTTP/1.1（HttpProtocol）</li>
 * </ul>
 * 识别后动态配置 pipeline 并移除自身，后续按确定的协议处理。
 * <p>
 * 注意：gRPC 底层也是 HTTP/2 传输，无法在字节层面与普通 HTTP/2 区分。
 * 通过构造函数的 {@code http2ProtocolName} 参数控制 HTTP/2 连接的处理方式。
 */
public class ProtocolDetectHandler extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(ProtocolDetectHandler.class);

    /** TCP 私有协议魔数，与 NettyRpcEncoder/NettyRpcDecoder 一致 */
    private static final int NETTY_MAGIC = 0xAABBCCDD;

    /** HTTP/2 Connection Preface 前 4 字节: "PRI " = 0x50524920 */
    private static final int HTTP2_MAGIC = 0x50524920;

    // 常见 HTTP/1.1 方法的首字母 ASCII 码
    private static final byte BYTE_G = 'G'; // GET
    private static final byte BYTE_P = 'P'; // POST, PUT, PATCH
    private static final byte BYTE_D = 'D'; // DELETE
    private static final byte BYTE_H = 'H'; // HEAD
    private static final byte BYTE_O = 'O'; // OPTIONS
    private static final byte BYTE_T = 'T'; // TRACE
    private static final byte BYTE_C = 'C'; // CONNECT

    /**
     * 当检测到 HTTP/2 Connection Preface 时使用的协议名。
     * 因为 gRPC 底层也是 HTTP/2，无法在字节层面区分，
     * 所以通过这个参数指定：可以是 "http2" 或 "grpc"。
     * 默认为 "http2"。
     */
    private final String http2ProtocolName;

    /**
     * 默认构造函数，HTTP/2 连接使用 Http2Protocol 处理
     */
    public ProtocolDetectHandler() {
        this("http2");
    }

    /**
     * 指定 HTTP/2 连接的处理协议
     *
     * @param http2ProtocolName HTTP/2 连接使用的协议名（"http2" 或 "grpc"）
     */
    public ProtocolDetectHandler(String http2ProtocolName) {
        this.http2ProtocolName = http2ProtocolName;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 至少需要 4 字节才能判断协议类型
        if (in.readableBytes() < 4) {
            return;
        }

        // 偷看前 4 字节，不消费（不移动 readerIndex）
        int magic = in.getInt(in.readerIndex());
        byte firstByte = in.getByte(in.readerIndex());

        if (magic == NETTY_MAGIC) {
            log.info("检测到 TCP 私有协议连接, 远程地址: {}", ctx.channel().remoteAddress());
            configProtocol(ctx, "netty");
        } else if (magic == HTTP2_MAGIC) {
            log.info("检测到 HTTP/2 协议连接 (使用 {} 处理), 远程地址: {}",
                    http2ProtocolName, ctx.channel().remoteAddress());
            configProtocol(ctx, http2ProtocolName);
        } else if (isHttpMethod(firstByte)) {
            log.info("检测到 HTTP/1.1 协议连接, 远程地址: {}", ctx.channel().remoteAddress());
            configProtocol(ctx, "http");
        } else {
            log.warn("未知协议, 首字节: 0x{}, 关闭连接, 远程地址: {}",
                    Integer.toHexString(magic), ctx.channel().remoteAddress());
            in.clear();
            ctx.close();
        }
    }

    /**
     * 判断首字节是否可能是 HTTP/1.1 方法名的开头
     */
    private boolean isHttpMethod(byte firstByte) {
        return firstByte == BYTE_G // GET
                || firstByte == BYTE_P // POST, PUT, PATCH
                || firstByte == BYTE_D // DELETE
                || firstByte == BYTE_H // HEAD
                || firstByte == BYTE_O // OPTIONS
                || firstByte == BYTE_T // TRACE
                || firstByte == BYTE_C; // CONNECT
    }

    /**
     * 根据协议名动态配置 pipeline，然后移除自身
     */
    private void configProtocol(ChannelHandlerContext ctx, String protocolName) {
        Protocol protocol = ProtocolFactory.getProtocol(protocolName);
        // 先移除自身，再配置协议的编解码器和 Handler
        ctx.pipeline().remove(this);
        protocol.config(ctx.pipeline(), true, new NettyRpcHandler());
    }
}
