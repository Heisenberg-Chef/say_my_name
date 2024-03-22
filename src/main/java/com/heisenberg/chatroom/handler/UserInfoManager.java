package com.heisenberg.chatroom.handler;

import com.heisenberg.chatroom.pojo.UserInfo;
import com.heisenberg.chatroom.proto.ChatProto;
import com.heisenberg.chatroom.util.BlankUtil;
import com.heisenberg.chatroom.util.NettyUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UserInfoManager {

    private static final Logger logger = LoggerFactory.getLogger(UserInfoManager.class);
    // 读写错主要面向的事userInfos信息，控制channel的扫描、删除、添加等操作的并发线程安全。
    private static ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);

    private static ConcurrentMap<Channel, UserInfo> userInfos = new ConcurrentHashMap<>();

    private static AtomicInteger userCount = new AtomicInteger(0);

    public static void addChannel(Channel channel) {
        String remoteAddr = NettyUtil.parseChannelRemoteAddr(channel);
        if (!channel.isActive()) {
            logger.error("Channel is not active, address: {}.", remoteAddr);
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setAddr(remoteAddr);
        userInfo.setChannel(channel);
        userInfo.setTime(System.currentTimeMillis());
        userInfos.put(channel, userInfo);
    }

    public static boolean saveUser(Channel channel, String nick) {
        UserInfo userInfo = userInfos.get(channel);
        if (userInfo == null) {
            return false;
        }
        if (!channel.isActive()) {
            logger.error("channel is not active, address: {}, nick: {}", userInfo.getAddr(), nick);
            return false;
        }

        // 增加一个认证了的用户
        userCount.incrementAndGet();
        userInfo.setNick(nick);
        userInfo.setAuth(true);
        userInfo.setUserId();
        return true;
    }

    /**
     * 从缓存中移除Channel，并且关闭Channel
     *
     * @param channel
     */
    public static void removeChannel(Channel channel) {
        try {
            logger.warn("channel will be remove, address is :{}", NettyUtil.parseChannelRemoteAddr(channel));
            rwLock.writeLock().lock();
            channel.close();
            UserInfo userInfo = userInfos.get(channel);
            if (userInfo != null) {
                UserInfo tmp = userInfos.remove(channel);
                if (tmp != null && tmp.isAuth()) {
                    // 减去一个认证用户
                    userCount.decrementAndGet();
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 广播普通消息
     *
     * @param uid
     * @param nick
     * @param message
     */
    public static void broadcastMess(int uid, String nick, String message) {
        if (!BlankUtil.isBlank(message)) {
            try {
                rwLock.readLock().lock();
                Set<Channel> channels = userInfos.keySet();
                channels.forEach(ch -> {
                    UserInfo userInfo = userInfos.get(ch);
                    if (userInfo != null || userInfo.isAuth()) {
                        // 当频道中存在用户，并且用户是认证的
                        ch.writeAndFlush(new TextWebSocketFrame(ChatProto.buildMessProto(uid, nick, message)));
                    }
                });
            } finally {
                rwLock.readLock().unlock();
            }
        }
    }

    /**
     * 广播系统消息
     *
     * @param code
     * @param mess
     */
    public static void broadCastInfo(int code, Object mess) {
        try {
            rwLock.readLock().lock();
            Set<Channel> keySet = userInfos.keySet();
            for (Channel ch : keySet) {
                UserInfo userInfo = userInfos.get(ch);
                if (userInfo == null || !userInfo.isAuth()) continue;
                ch.writeAndFlush(new TextWebSocketFrame(ChatProto.buildSysProto(code, mess)));
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * ping 消息
     */
    public static void broadCastPing() {
        try {
            rwLock.readLock().lock();
            logger.info("broadCastPing userCount: {}", userCount.intValue());
            Set<Channel> keySet = userInfos.keySet();
            for (Channel ch : keySet) {
                UserInfo userInfo = userInfos.get(ch);
                if (userInfo == null || !userInfo.isAuth()) continue;
                ch.writeAndFlush(new TextWebSocketFrame(ChatProto.buildPingProto()));
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }


    /**
     * 发送系统消息
     *
     * @param code
     * @param mess
     */
    public static void sendInfo(Channel channel, int code, Object mess) {
        channel.writeAndFlush(new TextWebSocketFrame(ChatProto.buildSysProto(code, mess)));
    }

    public static void sendPong(Channel channel) {
        channel.writeAndFlush(new TextWebSocketFrame(ChatProto.buildPongProto()));
    }

    public static void scanNotActiveChannel() {
        Set<Channel> keySet = userInfos.keySet();
        for (Channel ch : keySet) {
            UserInfo userInfo = userInfos.get(ch);
            if (userInfo == null) continue;
            if (!ch.isOpen() || !ch.isActive() || (!userInfo.isAuth() && (System.currentTimeMillis() - userInfo.getTime()) > 10000)) {
                removeChannel(ch);
            }
        }
    }

    public static UserInfo getUserInfo(Channel channel) {
        return userInfos.get(channel);
    }

    public static ConcurrentMap<Channel, UserInfo> getUserInfos() {
        return userInfos;
    }

    public static int getAuthUserCount() {
        return userCount.get();
    }

    public static void updateUserTime(Channel channel) {
        UserInfo userInfo = getUserInfo(channel);
        if (userInfo != null) {
            userInfo.setTime(System.currentTimeMillis());
        }
    }
}
