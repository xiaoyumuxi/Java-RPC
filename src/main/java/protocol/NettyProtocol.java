package protocol;

import Serialization.MyRpcDecoder;
import Serialization.MyRpcEncoder;
import Serialization.Serializer;
import Serialization.SerializerCode;
import VO.RpcRequest;
import VO.RpcResponse;
import config.RpcConfig;
import io.netty.channel.ChannelPipeline;

public class NettyProtocol implements Protocol {

    @Override
    public String getName() {
        return "netty";
    }

    @Override
    public void config(ChannelPipeline pipeline, boolean isServer) {
        // 1. 获取配置
        RpcConfig rpcConfig = RpcConfig.getInstance();
        byte code = rpcConfig.getSerializerCode();
        Serializer serializer = SerializerCode.getSerializerByCode(code);

        // 2. 判断解码类型
        // 如果是服务端(isServer=true)，我要读 Request
        // 如果是客户端(isServer=false)，我要读 Response
        if (isServer) {
            pipeline.addLast(new MyRpcDecoder(RpcRequest.class));
        } else {
            pipeline.addLast(new MyRpcDecoder(RpcResponse.class));
        }

        // 3. 编码器 (收发都需要编码)
        pipeline.addLast(new MyRpcEncoder(serializer));
    }
}