import client.RpcClientProxy;
import service.HelloService;

public class RpcTest {
    public static void main(String[] args) {
        // 创建代理对象
        HelloService helloService = RpcClientProxy.create(HelloService.class);

        // 像调用本地方法一样调用远程
        String result = helloService.sayHello("World");

        System.out.println("RPC 调用结果: " + result);
    }
}