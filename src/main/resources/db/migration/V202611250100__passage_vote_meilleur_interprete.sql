-- Vote du meilleur Interprète d'un Passage de Freestyle à plusieurs (#74) : un vote porte soit une note,
-- soit l'Interprète désigné, jamais les deux.
ALTER TABLE passage_note ALTER COLUMN note DROP NOT NULL;
ALTER TABLE passage_note ADD COLUMN interprete_designe_id UUID;
ALTER TABLE passage_note ADD CONSTRAINT passage_note_note_ou_designation
    CHECK ((note IS NULL) <> (interprete_designe_id IS NULL));
