package protocol.Http;

import Serialization.Serializer;
import Serialization.SerializerCode;
import VO.RpcRequest;
import VO.RpcResponse;
import config.RpcConfig;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpClientCodec;
import protocol.Protocol;

public class HttpProtocol implements Protocol {

    @Override
    public String getName() {
        return "http";
    }


    @Override
    public void config(ChannelPipeline pipeline, boolean isServer) {
        // 1. 获取序列化器
        RpcConfig rpcConfig = RpcConfig.getInstance();
        Serializer serializer = SerializerCode.getSerializerByCode(rpcConfig.getSerializerCode());

        // 2. HTTP 编解码基础
        if (isServer) {
            pipeline.addLast(new HttpServerCodec());
        } else {
            pipeline.addLast(new HttpClientCodec());
        }
        pipeline.addLast(new HttpObjectAggregator(512 * 1024));

        // 3. HTTP 与 RpcObject 的转换层
        if (isServer) {
            // 服务端：解码 Request，编码 Response
            pipeline.addLast(new HttpRpcDecoder(serializer, RpcRequest.class));
            pipeline.addLast(new HttpRpcEncoder(serializer));
        } else {
            // 客户端：编码 Request，解码 Response
            pipeline.addLast(new HttpRpcEncoder(serializer));
            pipeline.addLast(new HttpRpcDecoder(serializer, RpcResponse.class));
        }
    }
}