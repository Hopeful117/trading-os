package com.hope.trading.news.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsIdentityTest {
    @Test
    void createsStableDifferentIdentitiesForEventsAndItems() {
        assertThat(NewsIdentity.eventId("fixture", "1"))
                .isEqualTo(NewsIdentity.eventId("fixture", "1"));
        assertThat(NewsIdentity.eventId("fixture", "1"))
                .isNotEqualTo(NewsIdentity.newsItemId("fixture", "1"));
    }

    @Test
    void keepsDelimiterContainingSourceValuesDistinct() {
        assertThat(NewsIdentity.eventId("a:b", "c"))
                .isNotEqualTo(NewsIdentity.eventId("a", "b:c"));
        assertThat(NewsIdentity.newsItemId("a:b", "c"))
                .isNotEqualTo(NewsIdentity.newsItemId("a", "b:c"));
    }

    @Test
    void rejectsBlankSourceIdentityValues() {
        assertThatThrownBy(() -> NewsIdentity.eventId("source", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NewsIdentity.newsItemId("", "item"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
