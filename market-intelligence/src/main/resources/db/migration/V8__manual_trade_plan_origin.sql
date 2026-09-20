ALTER TABLE trade_plan_versions
    ADD COLUMN origin VARCHAR(32) NOT NULL DEFAULT 'OPPORTUNITY';

ALTER TABLE trade_plan_versions
    ADD COLUMN author_id UUID;

ALTER TABLE trade_plan_versions
    ADD CONSTRAINT ck_trade_plan_origin CHECK (origin IN ('OPPORTUNITY', 'MANUAL'));
