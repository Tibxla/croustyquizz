CREATE TABLE karaoke_suggestion_chanson (
    id UUID PRIMARY KEY,
    soiree_id UUID NOT NULL,
    titre VARCHAR(120) NOT NULL,
    artiste VARCHAR(120) NOT NULL,
    titre_normalise TEXT NOT NULL,
    artiste_normalise TEXT NOT NULL,
    UNIQUE (soiree_id, titre_normalise, artiste_normalise)
);

CREATE TABLE karaoke_demande_chanson (
    suggestion_id UUID NOT NULL REFERENCES karaoke_suggestion_chanson(id) ON DELETE CASCADE,
    joueur_id UUID NOT NULL,
    PRIMARY KEY (suggestion_id, joueur_id)
);

CREATE TABLE karaoke_favori_chanson (
    soiree_id UUID NOT NULL,
    chanson_id VARCHAR(128) NOT NULL,
    lieu_id UUID,
    PRIMARY KEY (soiree_id, chanson_id)
);

COMMENT ON COLUMN karaoke_favori_chanson.lieu_id IS
    'L’issue #180 renseignera le Lieu à partir de la Soirée et ajoutera la clé étrangère.';
