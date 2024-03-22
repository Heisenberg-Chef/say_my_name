package com.heisenberg.chatroom.pojo;

import io.netty.channel.Channel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo {

    private static AtomicInteger uidGener = new AtomicInteger(1000);

    private boolean isAuth = false;

    private long time = 0; // 在线时长

    private int userId;

    private String addr;

    private String nick;

    private Channel channel;

    public void setUserId() {
        this.userId = uidGener.incrementAndGet();
    }
}
