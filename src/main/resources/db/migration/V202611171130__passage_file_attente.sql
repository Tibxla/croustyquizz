CREATE TABLE passage_numero_soiree (
    soiree_id UUID PRIMARY KEY,
    prochain_numero BIGINT NOT NULL CHECK (prochain_numero > 0)
);
CREATE TABLE passage_file_attente (
    manche_id UUID PRIMARY KEY,
    soiree_id UUID NOT NULL,
    limite_passages_en_attente BIGINT NOT NULL CHECK (limite_passages_en_attente BETWEEN 1 AND 9007199254740991),
    tour_ouvert BIGINT NOT NULL CHECK (tour_ouvert > 0),
    prochain_ordre_inscription BIGINT NOT NULL CHECK (prochain_ordre_inscription > 0),
    prochaine_cle_ordre BIGINT NOT NULL CHECK (prochaine_cle_ordre > 0),
    passage_prioritaire_id UUID
);
CREATE TABLE passage (
    id UUID PRIMARY KEY,
    soiree_id UUID NOT NULL,
    manche_id UUID NOT NULL REFERENCES passage_file_attente(manche_id),
    inscripteur_id UUID NOT NULL,
    contenu_id TEXT NOT NULL,
    duree_ms BIGINT NOT NULL CHECK (duree_ms > 0),
    numero BIGINT NOT NULL CHECK (numero > 0),
    numero_modification BIGINT NOT NULL CHECK (numero_modification >= 0),
    etat TEXT NOT NULL,
    inscription_le TIMESTAMPTZ NOT NULL,
    ordre_inscription BIGINT NOT NULL,
    tour_normal BIGINT NOT NULL,
    cle_ordre BIGINT NOT NULL,
    tour_consomme BIGINT,
    trace_motif TEXT,
    trace_acteur_id UUID,
    trace_instant TIMESTAMPTZ,
    UNIQUE (soiree_id, numero),
    UNIQUE (manche_id, ordre_inscription),
    CHECK ((trace_motif IS NULL) = (trace_instant IS NULL))
);
CREATE TABLE passage_interprete (
    passage_id UUID NOT NULL REFERENCES passage(id),
    joueur_id UUID NOT NULL,
    ordre INTEGER NOT NULL,
    PRIMARY KEY (passage_id, ordre),
    UNIQUE (passage_id, joueur_id)
);
CREATE TABLE passage_tour_consomme (
    manche_id UUID NOT NULL REFERENCES passage_file_attente(manche_id),
    interprete_id UUID NOT NULL,
    dernier_tour_consomme BIGINT NOT NULL,
    PRIMARY KEY (manche_id, interprete_id)
);
