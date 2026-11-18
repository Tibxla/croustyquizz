CREATE TABLE karaoke_contenu_passage (
    passage_id UUID PRIMARY KEY REFERENCES passage(id),
    chanson_id TEXT NOT NULL,
    id_lrclib BIGINT NOT NULL,
    titre TEXT NOT NULL,
    artiste TEXT NOT NULL,
    album TEXT,
    duree_ms BIGINT NOT NULL CHECK (duree_ms > 0),
    lignes_paroles JSONB NOT NULL
);
