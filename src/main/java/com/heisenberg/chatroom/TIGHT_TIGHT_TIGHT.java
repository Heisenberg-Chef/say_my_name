package com.heisenberg.chatroom;

import com.heisenberg.chatroom.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TIGHT_TIGHT_TIGHT {
    private static final Logger logger = LoggerFactory.getLogger(TIGHT_TIGHT_TIGHT.class);

    public static void main(String[] args) {
        HeisenbergChatroomServer server = new HeisenbergChatroomServer(Constants.DEFAULT_PORT);
        server.init();
        server.start();

        // 注册进程钩子，在JVM进程关闭之前释放资源
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.shutdown();
                logger.warn(">>>>>>>>>> jvm shutdown");
                System.exit(0);
            }
        });
    }
}
