package com.hope.trading.market_data.kraken.websocket;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class KrakenNettyTerminationObserverTest {
    @Test
    void forwardsCloseFrameAndCapturesItsMetadata() {
        var evidence = new KrakenTransportTerminationEvidenceAccumulator();
        var observer = new KrakenNettyTerminationObserver(evidence);
        var channel = new EmbeddedChannel(observer);
        var frame = new CloseWebSocketFrame(1001, "going away");

        assertThat(Boolean.valueOf(channel.writeInbound(frame))).isTrue();
        assertThat((CloseWebSocketFrame) channel.readInbound()).isSameAs(frame);
        assertThat(evidence.snapshot().websocketCloseObserved()).isTrue();
        assertThat(evidence.snapshot().websocketCloseCode()).isEqualTo(1001);
        assertThat(evidence.snapshot().websocketCloseReason()).isEqualTo("going away");

        channel.finishAndReleaseAll();
    }

    @Test
    void forwardsUserEventsAndCapturesTransportEvidence() {
        var evidence = new KrakenTransportTerminationEvidenceAccumulator();
        var forwardedEvent = new AtomicReference<>();
        var channel = new EmbeddedChannel(new KrakenNettyTerminationObserver(evidence),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(io.netty.channel.ChannelHandlerContext ctx, Object evt) {
                        forwardedEvent.set(evt);
                    }

                    @Override
                    public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
                    }
                });

        channel.pipeline().fireUserEventTriggered(io.netty.channel.socket.ChannelInputShutdownReadComplete.INSTANCE);
        channel.pipeline().fireUserEventTriggered(SslCloseCompletionEvent.SUCCESS);
        channel.pipeline().fireExceptionCaught(new IllegalStateException("transport failed"));

        var snapshot = evidence.snapshot();
        assertThat(snapshot.inputShutdownObserved()).isTrue();
        assertThat(snapshot.tlsCloseObserved()).isTrue();
        assertThat(snapshot.tlsCloseSucceeded()).isTrue();
        assertThat(snapshot.transportExceptionObserved()).isTrue();
        assertThat(snapshot.transportExceptionClass()).isEqualTo(IllegalStateException.class.getName());
        assertThat(snapshot.transportRootCauseClass()).isEqualTo(IllegalStateException.class.getName());
        assertThat(forwardedEvent.get()).isSameAs(SslCloseCompletionEvent.SUCCESS);

        channel.finishAndReleaseAll();
    }

    @Test
    void forwardsChannelInactiveAndDoesNotClassifyItAsRemoteClose() {
        var evidence = new KrakenTransportTerminationEvidenceAccumulator();
        var channel = new EmbeddedChannel(
                new KrakenNettyTerminationObserver(evidence), new ChannelInboundHandlerAdapter());

        channel.close();

        var snapshot = evidence.snapshot();
        assertThat(snapshot.channelInactiveObserved()).isTrue();
        assertThat(snapshot.websocketCloseObserved()).isFalse();
        assertThat(snapshot.transportExceptionObserved()).isFalse();
    }

    @Test
    void boundsCloseReasonAndDoesNotRetainPayloads() {
        var evidence = new KrakenTransportTerminationEvidenceAccumulator();
        var channel = new EmbeddedChannel(new KrakenNettyTerminationObserver(evidence));

        channel.writeInbound(new CloseWebSocketFrame(1000, "x".repeat(400)));

        assertThat(evidence.snapshot().websocketCloseReason()).hasSize(256);
        assertThat(evidence.snapshot().nettyChannelId()).isNotBlank();
        channel.finishAndReleaseAll();
    }
}
