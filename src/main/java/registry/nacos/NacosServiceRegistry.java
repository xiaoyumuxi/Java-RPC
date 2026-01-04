package registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import registry.ServiceRegistry;

import java.net.InetSocketAddress;

@Slf4j
public class NacosServiceRegistry implements ServiceRegistry {

    @Override
    public void registerService(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            NacosUtils.registerService(serviceName, inetSocketAddress);
            log.info("服务注册成功: {} -> {}", serviceName, inetSocketAddress);
        } catch (NacosException e) {
            log.error("注册服务失败:", e);
            throw new RuntimeException("注册服务失败", e);
        }
    }
}
