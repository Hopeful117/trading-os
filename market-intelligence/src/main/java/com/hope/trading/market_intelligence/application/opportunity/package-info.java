/**
 * ADR-026 creation, fusion, deduplication, lifecycle, registry, expiration,
 * ranking and user-projection use cases.
 *
 * <p>{@code OpportunityEngine} is the creation boundary. Other services either
 * manage immutable versions or build read-only views.</p>
 */
package com.hope.trading.market_intelligence.application.opportunity;
