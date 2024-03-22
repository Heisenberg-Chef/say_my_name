package com.heisenberg.chatroom.handler;

import com.alibaba.fastjson2.JSONObject;
import com.heisenberg.chatroom.pojo.UserInfo;
import com.heisenberg.chatroom.proto.ChatCode;
import com.heisenberg.chatroom.util.Constants;
import com.heisenberg.chatroom.util.NettyUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserAuthHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger logger = LoggerFactory.getLogger(UserAuthHandler.class);

    private WebSocketServerHandshaker handshaker;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            // 判断channel 是否空闲，空闲时移除channel
            Channel channel = ctx.channel();
            final String remoteAddr = NettyUtil.parseChannelRemoteAddr(channel);
            logger.warn("NETTY SERVER PIPELINE: IDLE exception [{}]", remoteAddr);
            UserInfoManager.removeChannel(ctx.channel());
            UserInfoManager.broadCastInfo(ChatCode.SYS_USER_COUNT, UserInfoManager.getAuthUserCount());
        }
        ctx.fireUserEventTriggered(evt); // go on!
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {

    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!request.decoderResult().isSuccess() || !"websocket".equals(request.headers().get("Upgrade"))) {
            logger.warn("protobuf don't support websocket");
            ctx.channel().close();
            return;
        }
        // 如果确定是websocket，那么进行下一次的服务器的握手
        WebSocketServerHandshakerFactory webSocketServerHandshakerFactory = new WebSocketServerHandshakerFactory(
                Constants.WEBSOCKET_URL, null, true
        );
        handshaker = webSocketServerHandshakerFactory.newHandshaker(request);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), request);
            UserInfo userInfo = new UserInfo();
            userInfo.setAddr(NettyUtil.parseChannelRemoteAddr(ctx.channel()));
            // 保存已经存在的Channel到对象中
            UserInfoManager.addChannel(ctx.channel());
        }
    }

    private void handleWebSocket(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 判断是否关闭链路命令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain()); // netty 引用计数 +1 release为 -1
            UserInfoManager.removeChannel(ctx.channel());
            return;
        }
        // 判断是否为ping
        if (frame instanceof PingWebSocketFrame) {
            logger.info("ping message:{}", frame.content().retain());
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        // 判断是否Pong消息
        if (frame instanceof PongWebSocketFrame) {
            logger.info("pong message:{}", frame.content().retain());
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        // 只支持文字信息
        if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(frame.getClass().getName() + " frame type not supported.");
        }
        String message = ((TextWebSocketFrame) frame).text();
        JSONObject json = JSONObject.parseObject(message);
        int code = json.getInteger("code");
        Channel channel = ctx.channel();
        switch (code) {
            case ChatCode.PING_CODE:
            case ChatCode.PONG_CODE:
                UserInfoManager.updateUserTime(channel);
                logger.info("receive pong message, address: {}", NettyUtil.parseChannelRemoteAddr(channel));
                return;
            case ChatCode.AUTH_CODE:
                boolean isSuccess = UserInfoManager.saveUser(channel, json.getString("nick"));
                UserInfoManager.sendInfo(channel, ChatCode.SYS_AUTH_STATE, isSuccess);
                if (isSuccess) {
                    // ch.writeAndFlush(new TextWebSocketFrame(ChatProto.buildSysProto(code, mess)));
                    UserInfoManager.broadCastInfo(ChatCode.SYS_USER_COUNT, UserInfoManager.getAuthUserCount());
                }
                return;
            case ChatCode.MESS_CODE:// 普通消息留给MessageHandler来处理
                break;
            default:
                logger.warn("The code [{}] can't be auth!!!", code);
                return;
        }
        // 向后传递消息给MessageHandler
        ctx.fireChannelRead(frame.retain());
    }

}
