package com.xiaoyu.rpc.core.protocol.grpc;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.config.RpcConfig;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;

/** One instance per unary gRPC stream, including its bounded fragmented-message state. */
public class GrpcServerHandler extends ChannelDuplexHandler {
    private final GrpcMessageAccumulator accumulator;
    private RpcRequest request;
    private boolean dispatched;

    public GrpcServerHandler(ChannelHandler businessHandler) {
        // The business handler follows this adapter in the child pipeline.
        accumulator = new GrpcMessageAccumulator(RpcConfig.getInstance().getMaxMessageSize());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Http2Frame)) {
            ctx.fireChannelRead(msg);
            return;
        }
        try {
            if (msg instanceof Http2DataFrame data) {
                byte[] payload = accumulator.append(ctx.alloc(), data.content());
                if (payload != null) {
                    request = RpcRequest.parseFrom(payload);
                }
                if (data.isEndStream()) {
                    dispatch(ctx);
                }
            } else if (msg instanceof Http2HeadersFrame headers && headers.isEndStream()) {
                dispatch(ctx);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void dispatch(ChannelHandlerContext ctx) {
        accumulator.requireComplete();
        if (dispatched || request == null) {
            throw new CorruptedFrameException("Invalid unary gRPC request");
        }
        dispatched = true;
        ctx.fireChannelRead(request);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof RpcResponse response)) {
            super.write(ctx, msg, promise);
            return;
        }
        byte[] bytes = response.toByteArray();
        ByteBuf body = ctx.alloc().buffer(bytes.length + 5);
        body.writeByte(0).writeInt(bytes.length).writeBytes(bytes);
        Http2Headers headers = new DefaultHttp2Headers().status("200")
                .set(HttpHeaderNames.CONTENT_TYPE, "application/grpc");
        Http2Headers trailers = new DefaultHttp2Headers().set("grpc-status", "0");
        PromiseCombiner writes = new PromiseCombiner(ctx.executor());
        writes.add(ctx.write(new DefaultHttp2HeadersFrame(headers, false)));
        writes.add(ctx.write(new DefaultHttp2DataFrame(body, false)));
        writes.add(ctx.write(new DefaultHttp2HeadersFrame(trailers, true)));
        writes.finish(promise);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        accumulator.close();
        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        accumulator.close();
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        accumulator.close();
        ctx.close();
    }
}
