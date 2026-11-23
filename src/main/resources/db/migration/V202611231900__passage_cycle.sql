CREATE TABLE passage_cycle (
    manche_id UUID PRIMARY KEY REFERENCES passage_file_attente(manche_id),
    passage_id UUID REFERENCES passage(id),
    etape_id UUID,
    appele_le TIMESTAMPTZ,
    echeance_appel_le TIMESTAMPTZ,
    demarre_le TIMESTAMPTZ,
    duree_reference_ms BIGINT CHECK (duree_reference_ms > 0),
    temps_pause_ms BIGINT NOT NULL CHECK (temps_pause_ms >= 0),
    pause_depuis TIMESTAMPTZ,
    cloture_le TIMESTAMPTZ,
    CHECK ((passage_id IS NULL) = (etape_id IS NULL))
);
