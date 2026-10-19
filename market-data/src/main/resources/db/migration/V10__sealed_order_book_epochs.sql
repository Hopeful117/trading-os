ALTER TABLE historical_order_book_sequence
    ADD COLUMN sealed_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE historical_order_book_sequence
    DROP CONSTRAINT ck_historical_order_book_sequence_status;

ALTER TABLE historical_order_book_sequence
    ADD CONSTRAINT ck_historical_order_book_sequence_status
        CHECK (status IN ('ACTIVE', 'SEALED', 'INVALIDATED'));

ALTER TABLE historical_order_book_sequence
    ADD CONSTRAINT ck_historical_order_book_sequence_lifecycle
        CHECK ((status = 'SEALED' AND sealed_at IS NOT NULL)
            OR (status <> 'SEALED' AND sealed_at IS NULL));

-- Only termination records with transport evidence can safely preserve the
-- historical interval. Unknown or provenance-free terminations remain closed
-- as invalidated data and are intentionally not salvaged.
UPDATE historical_order_book_sequence sequence
SET status = 'SEALED',
    sealed_at = (
        SELECT max(termination.terminated_at)
        FROM kraken_stream_termination_sequence termination_sequence
        JOIN kraken_stream_termination termination
          ON termination.termination_id = termination_sequence.termination_id
        WHERE termination_sequence.sequence_id = sequence.sequence_id
          AND (termination.classification = 'REMOTE_CLOSE'
               OR (termination.classification = 'NETWORK_FAILURE'
                   AND (termination.transport_exception_observed = TRUE
                        OR (termination.tls_close_observed = TRUE
                            AND termination.tls_close_succeeded = FALSE)))
               OR (termination.classification = 'UNKNOWN_TRANSPORT_COMPLETION'
                   AND termination.channel_inactive_observed = TRUE
                   AND termination.tls_close_observed = TRUE
                   AND termination.tls_close_succeeded = TRUE
                   AND termination.transport_exception_observed = FALSE))
    )
WHERE sequence.status = 'INVALIDATED'
  AND EXISTS (
        SELECT 1
        FROM kraken_stream_termination_sequence termination_sequence
        JOIN kraken_stream_termination termination
          ON termination.termination_id = termination_sequence.termination_id
        WHERE termination_sequence.sequence_id = sequence.sequence_id
          AND (termination.classification = 'REMOTE_CLOSE'
               OR (termination.classification = 'NETWORK_FAILURE'
                   AND (termination.transport_exception_observed = TRUE
                        OR (termination.tls_close_observed = TRUE
                            AND termination.tls_close_succeeded = FALSE)))
               OR (termination.classification = 'UNKNOWN_TRANSPORT_COMPLETION'
                   AND termination.channel_inactive_observed = TRUE
                   AND termination.tls_close_observed = TRUE
                   AND termination.tls_close_succeeded = TRUE
                   AND termination.transport_exception_observed = FALSE))
    );
