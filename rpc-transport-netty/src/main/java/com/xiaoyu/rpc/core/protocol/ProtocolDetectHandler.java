package com.xiaoyu.rpc.core.protocol;

import com.xiaoyu.rpc.core.server.NettyRpcHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * 协议嗅探器 —— 服务端自动识别多种协议。
 */
public class ProtocolDetectHandler extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(ProtocolDetectHandler.class);

    /** TCP 私有协议魔数，与 NettyRpcEncoder/NettyRpcDecoder 一致 */
    private static final int NETTY_MAGIC = 0xAABBCCDD;
    /** HTTP/2 Connection Preface 前 4 字节: "PRI " */
    private static final int HTTP2_MAGIC = 0x50524920;

    private static final byte BYTE_G = 'G';
    private static final byte BYTE_P = 'P';
    private static final byte BYTE_D = 'D';
    private static final byte BYTE_H = 'H';
    private static final byte BYTE_O = 'O';
    private static final byte BYTE_T = 'T';
    private static final byte BYTE_C = 'C';

    private final String http2ProtocolName;
    private final ChannelHandler serverHandler;

    public ProtocolDetectHandler() {
        this("http2", new NettyRpcHandler());
    }

    public ProtocolDetectHandler(String http2ProtocolName) {
        this(http2ProtocolName, new NettyRpcHandler());
    }

    public ProtocolDetectHandler(ChannelHandler serverHandler) {
        this("http2", serverHandler);
    }

    public ProtocolDetectHandler(String http2ProtocolName, ChannelHandler serverHandler) {
        this.http2ProtocolName = Objects.requireNonNull(http2ProtocolName, "http2ProtocolName");
        this.serverHandler = Objects.requireNonNull(serverHandler, "serverHandler");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 4) {
            return;
        }

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

    private boolean isHttpMethod(byte firstByte) {
        return firstByte == BYTE_G
                || firstByte == BYTE_P
                || firstByte == BYTE_D
                || firstByte == BYTE_H
                || firstByte == BYTE_O
                || firstByte == BYTE_T
                || firstByte == BYTE_C;
    }

    private void configProtocol(ChannelHandlerContext ctx, String protocolName) {
        Protocol protocol = ProtocolFactory.getProtocol(protocolName);
        ctx.pipeline().remove(this);
        protocol.config(ctx.pipeline(), true, serverHandler);
    }
}
