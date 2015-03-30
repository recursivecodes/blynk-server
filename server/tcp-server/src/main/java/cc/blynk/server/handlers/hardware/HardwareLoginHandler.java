package cc.blynk.server.handlers.hardware;

import cc.blynk.common.handlers.DefaultExceptionHandler;
import cc.blynk.common.model.messages.protocol.appllication.LoginMessage;
import cc.blynk.server.dao.FileManager;
import cc.blynk.server.dao.SessionsHolder;
import cc.blynk.server.dao.UserRegistry;
import cc.blynk.server.exceptions.IllegalCommandException;
import cc.blynk.server.exceptions.InvalidTokenException;
import cc.blynk.server.model.auth.ChannelState;
import cc.blynk.server.model.auth.User;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import static cc.blynk.common.enums.Response.OK;
import static cc.blynk.common.model.messages.MessageFactory.produce;

/**
 * Handler responsible for managing hardware and apps login messages.
 * Initializes netty channel with a state tied with user.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
@ChannelHandler.Sharable
public class HardwareLoginHandler extends SimpleChannelInboundHandler<LoginMessage> implements DefaultExceptionHandler {

    protected final FileManager fileManager;
    protected final UserRegistry userRegistry;
    protected final SessionsHolder sessionsHolder;

    public HardwareLoginHandler(FileManager fileManager, UserRegistry userRegistry, SessionsHolder sessionsHolder) {
        this.fileManager = fileManager;
        this.userRegistry = userRegistry;
        this.sessionsHolder = sessionsHolder;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, LoginMessage message) throws Exception {
        //warn: split may be optimized
        String[] messageParts = message.body.split(" ", 2);

        if (messageParts.length != 1) {
            throw new IllegalCommandException("Wrong income message format.", message.id);
        }

        String token = messageParts[0];
        User user = userRegistry.getUserByToken(token);

        if (user == null) {
            throw new InvalidTokenException(String.format("Hardware token is invalid. Token '%s', %s", token, ctx.channel()), message.id);
        }

        Integer dashId = UserRegistry.getDashIdByToken(user, token);

        Channel channel = ctx.channel();
        channel.attr(ChannelState.DASH_ID).set(dashId);
        channel.attr(ChannelState.IS_HARD_CHANNEL).set(true);
        channel.attr(ChannelState.USER).set(user);

        sessionsHolder.addChannelToGroup(user, channel, message.id);

        log.info("Adding hardware channel with id {} to userGroup {}.", ctx.channel(), user.getName());

        ctx.writeAndFlush(produce(message.id, OK));

        //send Pin Mode command in case channel connected to active dashboard with Pin Mode command that
        //was sent previously
        if (dashId.equals(user.getUserProfile().getActiveDashId()) && user.getUserProfile().getPinModeMessage() != null) {
            ctx.writeAndFlush(user.getUserProfile().getPinModeMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
       handleGeneralException(ctx, cause);
    }
}
