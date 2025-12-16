package client;

import VO.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.CompletableFuture;

public class RpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    private CompletableFuture<Object> future;

    public void setFuture(CompletableFuture<Object> future) {
        this.future = future;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
        // 收到响应，完成 Future，唤醒等待的主线程
        future.complete(response.getData());
    }
}