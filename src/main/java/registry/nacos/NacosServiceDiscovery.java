package registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import registry.ServiceDiscovery;

import java.net.InetSocketAddress;
import config.RpcConfig;
import extension.ExtensionLoader;
import loadbalancer.LoadBalancer;
import java.net.InetSocketAddress;
import java.util.List;

@Slf4j
public class NacosServiceDiscovery implements ServiceDiscovery {

    private final NamingService namingService;
    private final LoadBalancer loadBalancer;

    public NacosServiceDiscovery() {
        this.namingService = NacosUtils.getNacosNamingService();
        String loadBalancerCode = RpcConfig.getInstance().getLoadBalancer();
        this.loadBalancer = ExtensionLoader.getExtensionLoader(LoadBalancer.class).getExtension(loadBalancerCode);
    }

    @Override
    public InetSocketAddress lookupService(String serviceName) {
        try {
            List<Instance> instances = namingService.getAllInstances(serviceName);
            if (instances.size() == 0) {
                log.error("未找到服务: {}", serviceName);
                throw new RuntimeException("未找到服务: " + serviceName);
            }

            // 转换 Instance 列表为 String 列表 (ip:port)
            List<String> addressList = instances.stream()
                    .map(instance -> instance.getIp() + ":" + instance.getPort())
                    .collect(java.util.stream.Collectors.toList());

            // 负载均衡选择
            String targetAddress = loadBalancer.select(addressList);
            log.info("负载均衡选择服务地址: {}", targetAddress);

            String[] array = targetAddress.split(":");
            String host = array[0];
            int port = Integer.parseInt(array[1]);

            return new InetSocketAddress(host, port);
        } catch (NacosException e) {
            log.error("获取服务实例时发生错误:", e);
        }
        return null;
    }
}
