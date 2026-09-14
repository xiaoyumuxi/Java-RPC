package com.xiaoyu.rpc.core.protocol.http2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceAndSettingsFrameWrittenEvent;
import io.netty.util.AttributeKey;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletableFuture;

/**
 * Gates client-created HTTP/2 streams until Netty has written the connection preface and initial SETTINGS frame.
 *
 * <p>Netty explicitly requires clients not to write HTTP/2 application data before
 * {@link Http2ConnectionPrefaceAndSettingsFrameWrittenEvent} has been processed. Without this gate a request can race
 * channel activation and put a HEADERS frame on the wire before the HTTP/2 client preface.</p>
 */
public final class Http2ClientConnectionReadyHandler extends ChannelInboundHandlerAdapter {

    private static final AttributeKey<CompletableFuture<Void>> READY_FUTURE =
            AttributeKey.valueOf(Http2ClientConnectionReadyHandler.class, "readyFuture");

    private final CompletableFuture<Void> readyFuture;

    private Http2ClientConnectionReadyHandler(CompletableFuture<Void> readyFuture) {
        this.readyFuture = readyFuture;
    }

    public static void install(ChannelPipeline pipeline) {
        CompletableFuture<Void> readyFuture = new CompletableFuture<>();
        pipeline.channel().attr(READY_FUTURE).set(readyFuture);
        pipeline.addLast(new Http2ClientConnectionReadyHandler(readyFuture));
    }

    public static CompletableFuture<Void> readinessFuture(Channel channel) {
        CompletableFuture<Void> readyFuture = channel.attr(READY_FUTURE).get();
        if (readyFuture != null) {
            return readyFuture;
        }

        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException(
                "HTTP/2 client readiness handler is not installed on channel " + channel));
        return failed;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof Http2ConnectionPrefaceAndSettingsFrameWrittenEvent) {
            readyFuture.complete(null);
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (!readyFuture.isDone()) {
            readyFuture.completeExceptionally(new ClosedChannelException());
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!readyFuture.isDone()) {
            readyFuture.completeExceptionally(cause);
        }
        ctx.fireExceptionCaught(cause);
    }
}
