package com.heisenberg.chatroom.core;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public abstract class BaseServer implements Server {

    private Logger log = LoggerFactory.getLogger(BaseServer.class);

    protected String host = "0.0.0.0";

    protected int port = 6969;

    protected DefaultEventLoopGroup defLoopGroup;

    protected NioEventLoopGroup bossGroup;

    protected NioEventLoopGroup workGroup;

    protected NioServerSocketChannel ssch;

    protected ChannelFuture cf;

    protected ServerBootstrap b;

    public void init() {
        // Thread factory 用来创建线程，在这里使用了一个AtomicInteger来记录第几号线程
        defLoopGroup = new DefaultEventLoopGroup(8, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "DEFAULTEVENTLOOPGROUP_" + index.incrementAndGet());
            }
        });

        // runtime 中可以得到处理器的个数
        bossGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {

            private AtomicInteger index = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "BOSS_" + index.incrementAndGet());
            }
        });

        workGroup = new NioEventLoopGroup(
                Runtime.getRuntime().availableProcessors(),
                new ThreadFactory() {
                    private AtomicInteger index = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "WORK_" + index.incrementAndGet());
                    }

                });

        b = new ServerBootstrap();
    }

    // 让他们优雅地关机。
    @Override
    public void shutdown() {
        if (defLoopGroup != null) {
            defLoopGroup.shutdownGracefully();
        }
        bossGroup.shutdownGracefully();
        workGroup.shutdownGracefully();
    }

}
