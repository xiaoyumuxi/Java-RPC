package com.xiaoyu.rpc.core.protocol.grpc;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.*;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GrpcServerHandler extends ChannelDuplexHandler {

    // 透传的业务处理器（例如 NettyRpcHandler）
    private final io.netty.channel.ChannelHandler busineesHandler;

    public GrpcServerHandler(io.netty.channel.ChannelHandler busineesHandler) {
        this.busineesHandler = busineesHandler;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2Frame) {
            try {
                // 只在这里处理 gRPC 对应的 HTTP/2 Frame，转换成内部 RpcRequest
                processFrame(ctx, (Http2Frame) msg);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void processFrame(ChannelHandlerContext ctx, Http2Frame frame) throws Exception {
        if (frame instanceof Http2HeadersFrame) {
            Http2HeadersFrame headersFrame = (Http2HeadersFrame) frame;
            Http2Headers headers = headersFrame.headers();
            CharSequence contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
            if (contentType != null && contentType.toString().startsWith("application/grpc")) {
                // Initial gRPC header received
            }
        }

        if (frame instanceof Http2DataFrame) {
            Http2DataFrame dataFrame = (Http2DataFrame) frame;
            ByteBuf content = dataFrame.content();

            // gRPC 数据帧固定前缀：1 字节压缩标记 + 4 字节消息长度
            if (content.readableBytes() < 5)
                return;

            content.readByte(); // Compressed-Flag
            int length = content.readInt();

            if (content.readableBytes() < length) {
                // 当前帧数据不足，回退读指针等待后续数据（简化处理，生产环境建议引入缓冲聚合）
                content.resetReaderIndex();
                return;
            }

            // 尽量避免中间大数组拷贝：先切片，再按底层存储类型选择解析路径
            ByteBuf slice = content.readSlice(length);

            RpcRequest rpcRequest;
            if (slice.nioBufferCount() > 0) {
                // 直接走 NIO Buffer 解析，少一次复制
                rpcRequest = RpcRequest.parseFrom(slice.nioBuffer());
            } else {
                // 兜底路径：内存布局不支持 NIO Buffer 时退回字节数组解析
                byte[] bytes = new byte[length];
                slice.readBytes(bytes);
                rpcRequest = RpcRequest.parseFrom(bytes);
            }

            ctx.fireChannelRead(rpcRequest);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise) throws Exception {
        if (msg instanceof RpcResponse) {
            RpcResponse response = (RpcResponse) msg;
            try {
                byte[] bytes = response.toByteArray();
                // gRPC 响应体同样要补上 5 字节前缀（压缩位 + 长度）
                ByteBuf out = ctx.alloc().buffer();
                out.writeByte(0);
                out.writeInt(bytes.length);
                out.writeBytes(bytes);

                Http2Headers headers = new DefaultHttp2Headers().status("200")
                        .set(HttpHeaderNames.CONTENT_TYPE, "application/grpc");
                ctx.write(new DefaultHttp2HeadersFrame(headers));

                ctx.write(new DefaultHttp2DataFrame(out, false));

                Http2Headers trailers = new DefaultHttp2Headers()
                        .set("grpc-status", "0")
                        .set("grpc-message", "");
                ctx.writeAndFlush(new DefaultHttp2HeadersFrame(trailers, true), promise);
            } catch (Exception e) {
                log.error("Failed to write gRPC response", e);
                promise.setFailure(e);
            }
            return;
        }
        super.write(ctx, msg, promise);
    }
}
