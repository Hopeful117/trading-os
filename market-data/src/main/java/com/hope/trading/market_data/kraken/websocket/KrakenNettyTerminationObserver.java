package com.hope.trading.market_data.kraken.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.ssl.SslCloseCompletionEvent;

import java.time.Instant;

/** Passive Netty handler that records bounded evidence and forwards everything. */
public final class KrakenNettyTerminationObserver extends ChannelInboundHandlerAdapter {
    private final KrakenTransportTerminationEvidenceAccumulator evidence;

    public KrakenNettyTerminationObserver(KrakenTransportTerminationEvidenceAccumulator evidence) {
        this.evidence = evidence;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        evidence.channelId(ctx.channel().id().asLongText());
        super.handlerAdded(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof CloseWebSocketFrame close) {
            evidence.websocketClose(close.statusCode(), close.reasonText());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        evidence.channelInactive(Instant.now());
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        evidence.transportException(cause);
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof ChannelInputShutdownEvent || evt instanceof ChannelInputShutdownReadComplete) {
            evidence.inputShutdown();
        } else if (evt instanceof SslCloseCompletionEvent ssl) {
            evidence.tlsClose(ssl.isSuccess(), ssl.cause());
        }
        ctx.fireUserEventTriggered(evt);
    }
}
