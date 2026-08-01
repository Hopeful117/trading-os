# Trading Core migrations

`V1` is the clean-install definition of the pre-Story Trading Core JPA schema. It contains no data.

Flyway automatic baselining is intentionally disabled. Before deploying against an existing unmanaged database, an operator must verify that its schema matches `V1` and explicitly baseline that database at version `1`. Trading Core does not infer, seed, or backfill account risk configuration, profiles, assignments, portfolio identities, or risk facts.
