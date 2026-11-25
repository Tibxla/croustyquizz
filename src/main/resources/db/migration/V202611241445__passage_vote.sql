CREATE TABLE passage_vote (
    passage_id UUID PRIMARY KEY REFERENCES passage(id),
    manche_id UUID NOT NULL REFERENCES passage_file_attente(manche_id),
    ouvert_le TIMESTAMPTZ NOT NULL,
    echeance_le TIMESTAMPTZ NOT NULL,
    pause_depuis TIMESTAMPTZ,
    cloture_le TIMESTAMPTZ,
    CHECK (cloture_le IS NULL OR pause_depuis IS NULL)
);

CREATE INDEX passage_vote_manche_idx ON passage_vote (manche_id);

-- Les Joueurs de démonstration ne sont pas dans la table joueur.
CREATE TABLE passage_note (
    passage_id UUID NOT NULL REFERENCES passage_vote(passage_id),
    joueur_id UUID NOT NULL,
    note SMALLINT NOT NULL CHECK (note BETWEEN 1 AND 5),
    recue_le TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (passage_id, joueur_id)
);
