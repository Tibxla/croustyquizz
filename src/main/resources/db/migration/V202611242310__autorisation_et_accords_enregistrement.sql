CREATE TABLE autorisation_enregistrement (
    soiree_id UUID PRIMARY KEY,
    lieu_id UUID,
    autorise BOOLEAN NOT NULL
);

COMMENT ON COLUMN autorisation_enregistrement.lieu_id IS
    '#180 remplira cette colonne pour rattacher l’autorisation au Lieu.';

CREATE TABLE accord_enregistrement_passage (
    passage_id UUID NOT NULL,
    interprete_id UUID NOT NULL,
    PRIMARY KEY (passage_id, interprete_id),
    FOREIGN KEY (passage_id, interprete_id) REFERENCES passage_interprete(passage_id, joueur_id)
        ON DELETE CASCADE
);
