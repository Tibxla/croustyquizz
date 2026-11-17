-- État interne du mode factice, absent des livrables de production.
CREATE TABLE test_manche_etat (
    manche_id UUID PRIMARY KEY REFERENCES manche_deroulement(manche_id),
    donnees JSONB NOT NULL
);
