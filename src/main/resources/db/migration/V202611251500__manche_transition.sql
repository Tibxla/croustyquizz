-- Transition du Pilotage Automatique entre deux Manches (#123) : au plus une par Soirée, relue au redémarrage.
-- Pas de clé étrangère vers soiree, comme manche_deroulement.soiree_id : les Soirées de démonstration n'y sont pas.
CREATE TABLE manche_transition (
    soiree_id UUID PRIMARY KEY,
    manche_terminee_id UUID NOT NULL REFERENCES manche_deroulement(manche_id),
    fin_prevue_le TIMESTAMPTZ NOT NULL
);
