package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.registry.ServiceDiscovery;
import com.xiaoyu.rpc.core.transport.Transport;
import com.xiaoyu.rpc.core.transport.TransportClient;

import java.net.InetSocketAddress;

public class RpcClient {

    private final TransportClient transportClient;
    private final ServiceDiscovery serviceDiscovery;

    public RpcClient() {
        RpcConfig config = RpcConfig.getInstance();
        // 初始化服务发现
        this.serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class)
                .getExtension(config.getRegistryType());

        // 初始化传输层客户端
        Transport transport = ExtensionLoader.getExtensionLoader(Transport.class).getExtension(config.getTransport());
        this.transportClient = transport.createClient();
    }

    public Object sendRequest(RpcRequest request, Class<?> returnType) {
        try {
            // 1. 服务发现
            InetSocketAddress address = serviceDiscovery.lookupService(request.getInterfaceName());

            if (address == null) {
                throw new RuntimeException("未发现服务: " + request.getInterfaceName());
            }

            // 2. 使用传输层发送请求
            // 注意: TransportClient 返回的是反序列化后的结果(RpcResponse)或者已经提取的数据
            // 在 NettyTransportClient 实现中，我们返回了 RpcResponse 对象
            Object result = transportClient.sendRequest(request, address);

            // 3. 处理结果 (这一步逻辑如果 NettyTransportClient 已经做了反序列化，这里可能有点冗余，但保持检查是好的)
            if (result instanceof com.xiaoyu.rpc.common.vo.RpcResponse) {
                com.xiaoyu.rpc.common.vo.RpcResponse response = (com.xiaoyu.rpc.common.vo.RpcResponse) result;

                // 数据已经在 TransportClient 中反序列化了吗？
                // 查看 NettyTransportClient 代码:
                // return rpcResponse; -> 它并没有反序列化 data 字段成 returnType
                // 所以这里需要反序列化

                byte[] data = response.getData().toByteArray();

                com.xiaoyu.rpc.common.serialization.Serializer serializer = com.xiaoyu.rpc.common.serialization.SerializerCode
                        .getSerializerByCode(RpcConfig.getInstance().getSerializerCode());
                return serializer.deserialize(data, returnType);
            }

            throw new RuntimeException("Unexpected response type: " + result.getClass());

        } catch (Exception e) {
            throw new RuntimeException("RPC请求发送失败", e);
        }
    }
}
