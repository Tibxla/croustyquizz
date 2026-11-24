-- Invitations des Passages à plusieurs (#135), émises à l'inscription, dans leur ordre d'émission.
-- Les Joueurs de démonstration ne sont pas dans la table joueur, d'où l'absence de clé étrangère vers elle.
CREATE TABLE passage_invitation (
    passage_id UUID NOT NULL REFERENCES passage(id),
    invite_id UUID NOT NULL,
    manche_id UUID NOT NULL REFERENCES passage_file_attente(manche_id),
    etat TEXT NOT NULL CHECK (etat IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE', 'EXPIREE')),
    ordre INTEGER NOT NULL CHECK (ordre >= 0),
    PRIMARY KEY (passage_id, invite_id)
);

CREATE INDEX passage_invitation_manche_idx ON passage_invitation (manche_id, ordre);
