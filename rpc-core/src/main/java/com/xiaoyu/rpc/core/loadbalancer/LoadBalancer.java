package com.xiaoyu.rpc.core.loadbalancer;

import com.xiaoyu.rpc.common.extension.SPI;
import java.util.List;

@SPI
public interface LoadBalancer {
    /**
     * 从服务列表中选择一个实例
     * 
     * @param serviceAddresses 服务地址列表 (格式: ip:port)
     * @return 选择的服务地址
     */
    String select(List<String> serviceAddresses);
}
