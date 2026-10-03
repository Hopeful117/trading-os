package com.hope.trading.news.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsIdentityTest {
    @Test
    void createsStableDifferentIdentitiesForEventsAndItems() {
        assertThat(NewsIdentity.eventId("fixture", "1"))
                .isEqualTo(NewsIdentity.eventId("fixture", "1"));
        assertThat(NewsIdentity.eventId("fixture", "1"))
                .isNotEqualTo(NewsIdentity.newsItemId("fixture", "1"));
    }
}
