CREATE TABLE passage_cycle (
    manche_id UUID PRIMARY KEY REFERENCES passage_file_attente(manche_id),
    passage_id UUID REFERENCES passage(id),
    etape_id UUID,
    appele_le TIMESTAMPTZ,
    echeance_appel_le TIMESTAMPTZ,
    demarre_le TIMESTAMPTZ,
    duree_reference_secondes BIGINT CHECK (duree_reference_secondes >= 0),
    duree_reference_nanos INTEGER CHECK (duree_reference_nanos BETWEEN 0 AND 999999999),
    temps_pause_secondes BIGINT NOT NULL CHECK (temps_pause_secondes >= 0),
    temps_pause_nanos INTEGER NOT NULL CHECK (temps_pause_nanos BETWEEN 0 AND 999999999),
    pause_depuis TIMESTAMPTZ,
    cloture_le TIMESTAMPTZ,
    CHECK ((passage_id IS NULL) = (etape_id IS NULL)),
    CHECK ((passage_id IS NULL) = (appele_le IS NULL)),
    CHECK ((passage_id IS NULL) = (echeance_appel_le IS NULL)),
    CHECK (passage_id IS NOT NULL OR (demarre_le IS NULL AND pause_depuis IS NULL AND cloture_le IS NULL
        AND temps_pause_secondes = 0 AND temps_pause_nanos = 0)),
    CHECK ((demarre_le IS NULL) = (duree_reference_secondes IS NULL)),
    CHECK ((duree_reference_secondes IS NULL) = (duree_reference_nanos IS NULL)),
    CHECK (duree_reference_secondes IS NULL OR duree_reference_secondes > 0 OR duree_reference_nanos > 0),
    CHECK (cloture_le IS NULL OR pause_depuis IS NULL)
);
