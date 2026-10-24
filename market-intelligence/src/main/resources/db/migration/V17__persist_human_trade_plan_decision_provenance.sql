ALTER TABLE trade_plan_versions
    ADD COLUMN decision_actor_id UUID;

ALTER TABLE trade_plan_versions
    ADD COLUMN decision_at TIMESTAMP WITH TIME ZONE;
