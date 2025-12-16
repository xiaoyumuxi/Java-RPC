package service;
import VO.RpcRequest;
import VO.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RpcServerHandler extends SimpleChannelInboundHandler<RpcRequest> {

    // 模拟注册中心，存放 接口名 -> 实现类
    private static final Map<String, Object> SERVICE_MAP = new ConcurrentHashMap<>();

    // 注册服务的方法
    public static void registerService(String interfaceName, Object serviceBean) {
        SERVICE_MAP.put(interfaceName, serviceBean);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
        // 1. 获取实现类
        Object serviceBean = SERVICE_MAP.get(request.getInterfaceName());

        // 2. 反射调用
        Class<?> serviceClass = serviceBean.getClass();
        Method method = serviceClass.getMethod(request.getMethodName(), request.getParamTypes());
        Object result = method.invoke(serviceBean, request.getParameters());

        // 3. 封装结果并返回
        RpcResponse response = new RpcResponse();
        response.setData(result);
        response.setMessage("Success");

        ctx.writeAndFlush(response);
    }
}

