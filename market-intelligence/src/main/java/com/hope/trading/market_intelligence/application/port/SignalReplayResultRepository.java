package com.hope.trading.market_intelligence.application.port;

import com.hope.trading.market_intelligence.domain.replay.SignalReplayResult;

import java.util.Optional;

public interface SignalReplayResultRepository {
    Optional<SignalReplayResult> findByFingerprint(String fingerprint);

    SignalReplayResult saveIfAbsent(SignalReplayResult result);
}
