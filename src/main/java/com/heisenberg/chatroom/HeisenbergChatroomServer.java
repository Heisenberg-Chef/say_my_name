package com.heisenberg.chatroom;

import com.heisenberg.chatroom.core.BaseServer;
import com.heisenberg.chatroom.handler.MessageHandler;
import com.heisenberg.chatroom.handler.UserAuthHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.heisenberg.chatroom.handler.UserInfoManager;

public class HeisenbergChatroomServer extends BaseServer {

    private final ScheduledExecutorService executorService;

    public HeisenbergChatroomServer(int port) {
        this.port = port;
        executorService = new ScheduledThreadPoolExecutor(2,
                new HeisenbergBlueFactory("schedule"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    class HeisenbergBlueFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);

        private final String namePrefix;

        public HeisenbergBlueFactory(String name) {
            namePrefix = "pool-" + poolNumber.getAndIncrement() + "-thread-" + name;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }

    @Override
    public void start() {
        this.b.group(this.bossGroup, this.workGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true) // 如果在2h内没有任何数据交互，TCP会发送一个探测数据包，看看那连接活着没？
                .option(ChannelOption.TCP_NODELAY, true) // nagle 算法，合并数据包，禁用可以减少延迟
                .option(ChannelOption.SO_BACKLOG, 1024) // 1024个链接，如果队伍满了那么就阻塞
                .localAddress(new InetSocketAddress(port))
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(defLoopGroup,
                                new HttpServerCodec(), // 请求解码器
                                new HttpObjectAggregator(65535), // 将多个消息转为单一消息对象
                                new ChunkedWriteHandler(), // 支持一步最大的码流，一般用于文件发送
                                new IdleStateHandler(60, 0, 0), // 检测链路是否读空闲
                                new UserAuthHandler(), // 处理握手与认证
                                new MessageHandler() // 处理消息发送
                        );
                    }
                });
        try {
            cf = b.bind().sync();
            InetSocketAddress address = (InetSocketAddress) cf.channel().localAddress();
            logger.info("WebSocketServer start success, port is:{}", address.getPort());

            // 定时扫描所有的Channel，关闭无效的Channel
            executorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    logger.info("scanNotActiveChannel >>>>> ");
                    UserInfoManager.scanNotActiveChannel();
                }
            }, 3, 60, TimeUnit.SECONDS);

            // 定时向所有客户端发送Ping消息
            executorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    UserInfoManager.broadCastPing();
                }
            }, 3, 50, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("WebSocketServer start fail,", e);
        }

    }

    @Override
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
        super.shutdown();
    }
}
