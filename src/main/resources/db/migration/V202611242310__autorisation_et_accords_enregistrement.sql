CREATE TABLE autorisation_enregistrement (
    soiree_id UUID PRIMARY KEY,
    lieu_id UUID,
    autorise BOOLEAN NOT NULL
);

COMMENT ON COLUMN autorisation_enregistrement.lieu_id IS
    '#180 remplira cette colonne pour rattacher l’autorisation au Lieu.';

-- Les Interprètes d'un Passage sont réécrits par position : l'accord ne dépend donc que du Passage.
-- Comme passage_interprete, joueur_id n'a pas de clé étrangère vers joueur. L'application efface
-- l'accord de l'Interprète qui quitte le Passage.
CREATE TABLE accord_enregistrement_passage (
    passage_id UUID NOT NULL REFERENCES passage(id) ON DELETE CASCADE,
    joueur_id UUID NOT NULL,
    PRIMARY KEY (passage_id, joueur_id)
);
