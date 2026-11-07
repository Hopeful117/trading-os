CREATE TEMP TABLE ctrader_market_identity_reconciliation ON COMMIT DROP AS
SELECT spot.market_id AS survivor_market_id,
       cfd.market_id AS duplicate_market_id
FROM markets spot
JOIN markets cfd
  ON cfd.provider = spot.provider
 AND cfd.symbol = spot.symbol
 AND cfd.product_type = 'CFD'
WHERE spot.provider = 'CTRADER'
  AND spot.product_type = 'SPOT';

UPDATE historical_ohlc_evidence evidence
SET market_id = (SELECT survivor_market_id
                 FROM ctrader_market_identity_reconciliation
                 WHERE duplicate_market_id = evidence.market_id)
WHERE evidence.market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

UPDATE price_observations observation
SET market_id = (SELECT survivor_market_id
                 FROM ctrader_market_identity_reconciliation
                 WHERE duplicate_market_id = observation.market_id)
WHERE observation.market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

UPDATE historical_funding_evidence evidence
SET market_id = (SELECT survivor_market_id
                 FROM ctrader_market_identity_reconciliation
                 WHERE duplicate_market_id = evidence.market_id)
WHERE evidence.market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

UPDATE historical_order_book_evidence evidence
SET market_id = (SELECT survivor_market_id
                 FROM ctrader_market_identity_reconciliation
                 WHERE duplicate_market_id = evidence.market_id)
WHERE evidence.market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

UPDATE historical_order_book_evidence_segment segment
SET market_id = (SELECT survivor_market_id
                 FROM ctrader_market_identity_reconciliation
                 WHERE duplicate_market_id = segment.market_id)
WHERE segment.market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

UPDATE historical_order_book_replay_artifact artifact
SET market_id = (SELECT survivor_market_id
                 FROM ctrader_market_identity_reconciliation
                 WHERE duplicate_market_id = artifact.market_id)
WHERE artifact.market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

UPDATE historical_order_book_sequence sequence
SET market_id = (SELECT survivor_market_id
                 FROM ctrader_market_identity_reconciliation
                 WHERE duplicate_market_id = sequence.market_id)
WHERE sequence.market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

UPDATE kraken_stream_termination_sequence sequence
SET market_id = (SELECT survivor_market_id
                 FROM ctrader_market_identity_reconciliation
                 WHERE duplicate_market_id = sequence.market_id)
WHERE sequence.market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

UPDATE valuation_facts fact
SET market_id = (SELECT survivor_market_id
                 FROM ctrader_market_identity_reconciliation
                 WHERE duplicate_market_id = fact.market_id)
WHERE fact.market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

UPDATE valuation_facts fact
SET source_market_id = (SELECT survivor_market_id
                        FROM ctrader_market_identity_reconciliation
                        WHERE duplicate_market_id = fact.source_market_id)
WHERE fact.source_market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

UPDATE valuation_conversion_legs leg
SET source_market_id = (SELECT survivor_market_id
                        FROM ctrader_market_identity_reconciliation
                        WHERE duplicate_market_id = leg.source_market_id)
WHERE leg.source_market_id IN (SELECT duplicate_market_id FROM ctrader_market_identity_reconciliation);

DELETE FROM markets
WHERE market_id IN (
    SELECT duplicate_market_id
    FROM ctrader_market_identity_reconciliation
);

UPDATE markets
SET product_type = 'CFD'
WHERE provider = 'CTRADER'
  AND product_type = 'SPOT'
  AND provider_symbol IN ('1', '2', '41', '283');
