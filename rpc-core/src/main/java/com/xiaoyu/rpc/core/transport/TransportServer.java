package com.xiaoyu.rpc.core.transport;

/**
 * 传输层服务端接口。
 */
public interface TransportServer {

    /**
     * 启动服务并在监听端口成功 bind 后返回。
     *
     * @throws InterruptedException 启动过程被中断
     */
    void start() throws InterruptedException;

    /**
     * 阻塞等待服务端停止。默认实现用于不需要阻塞语义的传输层。
     *
     * @throws InterruptedException 等待过程被中断
     */
    default void awaitTermination() throws InterruptedException {
        // no-op by default
    }

    /**
     * 停止服务并释放资源。
     */
    void stop();
}
