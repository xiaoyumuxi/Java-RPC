package com.xiaoyu.rpc.core.transport;

/**
 * 传输层服务端接口
 */
public interface TransportServer {

    /**
     * 启动服务
     * 
     * @throws InterruptedException 如果启动过程被中断
     */
    void start() throws InterruptedException;

    /**
     * 停止服务
     */
    void stop();
}
