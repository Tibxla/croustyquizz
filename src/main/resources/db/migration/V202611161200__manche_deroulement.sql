-- Cycle commun et reçus durables ; aucune dépendance envers les tables de Soirée à venir.
CREATE TABLE manche_deroulement (
    manche_id UUID PRIMARY KEY,
    soiree_id UUID NOT NULL,
    mode VARCHAR(32) NOT NULL CHECK (mode IN ('QUIZ', 'BLIND_TEST', 'KARAOKE', 'FREESTYLE')),
    etat VARCHAR(32) NOT NULL CHECK (etat IN ('PRETE', 'EN_COURS', 'EN_PAUSE', 'TERMINEE')),
    reglages JSONB NOT NULL,
    cree_le TIMESTAMPTZ NOT NULL,
    modifie_le TIMESTAMPTZ NOT NULL
);
CREATE INDEX manche_deroulement_soiree ON manche_deroulement (soiree_id);
CREATE UNIQUE INDEX manche_active_par_soiree ON manche_deroulement (soiree_id)
    WHERE etat IN ('EN_COURS', 'EN_PAUSE');
CREATE TABLE manche_envoi (
    id UUID PRIMARY KEY,
    acteur VARCHAR(128) NOT NULL,
    route TEXT NOT NULL,
    envoi_id UUID NOT NULL,
    empreinte VARCHAR(64) NOT NULL,
    statut INTEGER NOT NULL,
    corps JSONB NOT NULL,
    cree_le TIMESTAMPTZ NOT NULL,
    UNIQUE (acteur, route, envoi_id)
);
