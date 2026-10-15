package com.hope.trading.market_data.scheduler;

import com.hope.trading.market_data.service.HistoricalOhlcCaptureService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class HistoricalOhlcCaptureScheduler {
    private final HistoricalOhlcCaptureService captureService;

    @Scheduled(fixedDelayString = "${historical-ohlc.capture.fixed-delay:PT15M}")
    public void capture() {
        captureService.capture();
    }
}
