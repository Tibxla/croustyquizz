-- Blind test (#177) : la sélection tirée à l'ajout de la Manche et l'état du déroulement, jamais publiés tels quels.
-- La sélection porte les identifiants Deezer et les réponses : elle ne quitte pas le serveur avant la Révélation.
CREATE TABLE blindtest_manche (
    manche_id UUID PRIMARY KEY REFERENCES manche_deroulement(manche_id) ON DELETE CASCADE,
    selection JSONB NOT NULL CHECK (jsonb_typeof(selection) = 'array' AND jsonb_array_length(selection) BETWEEN 1 AND 50),
    etat JSONB
);
