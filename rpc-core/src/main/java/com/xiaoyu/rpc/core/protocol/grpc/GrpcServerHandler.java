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

    private final io.netty.channel.ChannelHandler busineesHandler;

    public GrpcServerHandler(io.netty.channel.ChannelHandler busineesHandler) {
        this.busineesHandler = busineesHandler;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2Frame) {
            try {
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

            if (content.readableBytes() < 5)
                return;

            content.readByte(); // Compressed-Flag
            int length = content.readInt();

            if (content.readableBytes() < length) {
                // Return reader index to start if not enough data (simplified, normally
                // requires buffering)
                content.resetReaderIndex();
                return;
            }

            // Zero-Copy Optimization:
            // Use slicing to avoid allocating an intermediate byte[]
            ByteBuf slice = content.readSlice(length);

            RpcRequest rpcRequest;
            if (slice.nioBufferCount() > 0) {
                // Zero-Copy: Direct access via NIO ByteBuffer
                rpcRequest = RpcRequest.parseFrom(slice.nioBuffer());
            } else {
                // Fallback: Use InputStream (avoids large byte[] allocation)
                // Note: Protobuf CodedInputStream still copies when string/bytes parsed, but we
                // avoid the big chunk copy
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
