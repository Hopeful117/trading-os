package com.hope.trading.market_data.service;

import com.hope.trading.market_data.adapter.persistence.JpaHistoricalOhlcEvidenceRepository;
import com.hope.trading.market_data.model.HistoricalOhlcSeries;
import com.hope.trading.market_data.model.OhlcInterval;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoricalOhlcHistoryServiceTest {
    @Test
    void delegatesStrictObservedReadToRepository() {
        JpaHistoricalOhlcEvidenceRepository repository = mock(JpaHistoricalOhlcEvidenceRepository.class);
        HistoricalOhlcHistoryService service = new HistoricalOhlcHistoryService(repository);
        UUID marketId = UUID.randomUUID();
        Instant asOf = Instant.parse("2026-10-15T10:20:00Z");
        HistoricalOhlcSeries expected = new HistoricalOhlcSeries(
                marketId, OhlcInterval.FIFTEEN_MINUTES, asOf, List.of(),
                HistoricalOhlcSeries.HistoricalOhlcSeriesStatus.INCOMPLETE, "missing");
        when(repository.findAsOf(marketId, OhlcInterval.FIFTEEN_MINUTES, asOf, 10,
                com.hope.trading.market_data.model.HistoricalOhlcEvidencePolicy.STRICT_OBSERVED))
                .thenReturn(expected);

        HistoricalOhlcSeries result = service.findStrictObserved(
                marketId, OhlcInterval.FIFTEEN_MINUTES, asOf, 10);

        assertThat(result).isSameAs(expected);
        verify(repository).findAsOf(marketId, OhlcInterval.FIFTEEN_MINUTES, asOf, 10,
                com.hope.trading.market_data.model.HistoricalOhlcEvidencePolicy.STRICT_OBSERVED);
    }
}
