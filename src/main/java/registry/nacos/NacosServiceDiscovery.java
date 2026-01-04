package registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import registry.ServiceDiscovery;

import java.net.InetSocketAddress;
import java.util.List;

@Slf4j
public class NacosServiceDiscovery implements ServiceDiscovery {

    private final NamingService namingService;

    public NacosServiceDiscovery() {
        this.namingService = NacosUtils.getNacosNamingService();
    }

    @Override
    public InetSocketAddress lookupService(String serviceName) {
        try {
            List<Instance> instances = namingService.getAllInstances(serviceName);
            if (instances.size() == 0) {
                log.error("未找到服务: {}", serviceName);
                throw new RuntimeException("未找到服务: " + serviceName);
            }
            // 简单的负载均衡：取第一个
            Instance instance = instances.get(0);
            log.info("发现服务地址: {}:{}", instance.getIp(), instance.getPort());
            return new InetSocketAddress(instance.getIp(), instance.getPort());
        } catch (NacosException e) {
            log.error("获取服务实例时发生错误:", e);
        }
        return null;
    }
}
