package com.hope.trading.broker_service.broker.scheduler;

import com.hope.trading.broker_service.broker.application.service.HistoricalFeeScheduleCaptureService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public final class HistoricalFeeScheduleCaptureScheduler {
    private final HistoricalFeeScheduleCaptureService capture;

    public HistoricalFeeScheduleCaptureScheduler(HistoricalFeeScheduleCaptureService capture) {
        this.capture = capture;
    }

    @Scheduled(fixedDelayString = "${historical-fee-schedule.capture.fixed-delay:PT1H}")
    public void capture() { capture.capture(); }
}
