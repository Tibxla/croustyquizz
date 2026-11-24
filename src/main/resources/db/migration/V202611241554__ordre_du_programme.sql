ALTER TABLE manche_deroulement ADD COLUMN position_programme INTEGER;

WITH ordre_initial AS (
    SELECT manche_id, row_number() OVER (PARTITION BY soiree_id ORDER BY cree_le, manche_id) - 1 AS position
    FROM manche_deroulement
)
UPDATE manche_deroulement m SET position_programme = o.position
FROM ordre_initial o WHERE m.manche_id = o.manche_id;

ALTER TABLE manche_deroulement
    ALTER COLUMN position_programme SET NOT NULL,
    ADD CONSTRAINT manche_position_positive CHECK (position_programme >= 0),
    ADD CONSTRAINT manche_position_unique UNIQUE (soiree_id, position_programme) DEFERRABLE INITIALLY DEFERRED;
