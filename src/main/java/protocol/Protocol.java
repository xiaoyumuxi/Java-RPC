package protocol;

import extension.SPI;
import io.netty.channel.ChannelPipeline;

@SPI
public interface Protocol {

    /**
     * 获取协议名称 (例如: "netty", "http")
     */
    String getName();

    /**
     * 配置 Pipeline
     * 在这里把该协议需要的 Encoder, Decoder, Handler 添加到流水线上
     * 
     * @param serverHandler 服务端业务处理器 (仅服务端需要，客户端传 null)
     */
    void config(ChannelPipeline pipeline, boolean isServer, io.netty.channel.ChannelHandler serverHandler);

    /**
     * 发送 RPC 请求
     * 不同协议有不同的发送逻辑（例如 HTTP/2 需要创建 Stream）
     *
     * @param channel       连接通道
     * @param request       RPC 请求体
     * @param clientHandler 客户端接收处理 Handler
     * @throws Exception 发送异常
     */
    void sendRequest(io.netty.channel.Channel channel, VO.RpcRequest request,
            client.NettyRpcClientHandler clientHandler) throws Exception;
}