CREATE TABLE karaoke_lecture_passage (
    passage_id UUID PRIMARY KEY REFERENCES passage(id),
    lecture_id UUID NOT NULL UNIQUE,
    lecteur_id UUID,
    etat TEXT NOT NULL CHECK (etat IN ('PREPAREE', 'DEMARREE', 'EN_ERREUR')),
    source TEXT NOT NULL CHECK (source IN ('FICHIER_LOCAL', 'PAROLES_SEULES')),
    motif_paroles_seules TEXT CHECK (motif_paroles_seules IN
        ('FICHIER_ABSENT', 'FICHIER_AMBIGU', 'FICHIER_ILLISIBLE', 'ECRAN_NON_PRET', 'ERREUR_LECTURE')),
    origine_paroles TEXT NOT NULL CHECK (origine_paroles IN ('VERSION_ADAPTEE', 'REPLI_INSCRIPTION', 'INSCRIPTION')),
    fichier_nom TEXT,
    fichier_taille_octets BIGINT CHECK (fichier_taille_octets > 0),
    fichier_duree_ms BIGINT CHECK (fichier_duree_ms > 0),
    paroles_id UUID NOT NULL,
    duree_paroles_ms BIGINT NOT NULL CHECK (duree_paroles_ms > 0),
    lignes_paroles JSONB NOT NULL,
    version_source_id BIGINT NOT NULL CHECK (version_source_id > 0),
    decalage_ms BIGINT NOT NULL,
    numero_modification_calage BIGINT NOT NULL CHECK (numero_modification_calage >= 0),
    demande_id UUID,
    demandee_le TIMESTAMPTZ,
    CHECK ((demande_id IS NULL) = (demandee_le IS NULL)),
    CHECK ((source = 'FICHIER_LOCAL' AND fichier_nom IS NOT NULL AND fichier_taille_octets IS NOT NULL
            AND motif_paroles_seules IS NULL)
        OR (source = 'PAROLES_SEULES' AND fichier_nom IS NULL AND fichier_taille_octets IS NULL
            AND fichier_duree_ms IS NULL AND motif_paroles_seules IS NOT NULL)),
    CHECK ((origine_paroles = 'INSCRIPTION') = (source = 'PAROLES_SEULES'
        AND motif_paroles_seules IS DISTINCT FROM 'ERREUR_LECTURE')),
    CHECK (source <> 'PAROLES_SEULES' OR (etat <> 'EN_ERREUR' AND demande_id IS NULL))
);
CREATE TABLE karaoke_lecteur_chanson (
    manche_id UUID PRIMARY KEY REFERENCES manche_deroulement(manche_id),
    lecteur_id UUID NOT NULL
);
CREATE TABLE karaoke_calage_chanson (
    chanson_id TEXT NOT NULL,
    version_source_id BIGINT NOT NULL CHECK (version_source_id > 0),
    fichier_nom TEXT NOT NULL,
    fichier_taille_octets BIGINT NOT NULL CHECK (fichier_taille_octets > 0),
    decalage_ms BIGINT NOT NULL,
    numero_modification BIGINT NOT NULL CHECK (numero_modification >= 1),
    modifie_le TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (chanson_id, version_source_id, fichier_nom, fichier_taille_octets)
);
