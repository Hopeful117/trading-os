ALTER TABLE trading_opportunity_versions
    ADD COLUMN strategy_match_id UUID NULL;

ALTER TABLE trading_opportunity_versions
    ADD CONSTRAINT fk_opportunity_strategy_match
    FOREIGN KEY (strategy_match_id) REFERENCES strategy_matches (match_id);

CREATE INDEX idx_opportunity_strategy_match
    ON trading_opportunity_versions(strategy_match_id);
