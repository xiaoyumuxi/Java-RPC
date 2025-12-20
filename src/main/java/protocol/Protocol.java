package protocol;

import io.netty.channel.ChannelPipeline;

public interface Protocol {

    /**
     * 获取协议名称 (例如: "netty", "http")
     */
    String getName();

    /**
     * 配置 Pipeline
     * 在这里把该协议需要的 Encoder, Decoder, Handler 添加到流水线上
     */
    void config(ChannelPipeline pipeline, boolean isServer);
}