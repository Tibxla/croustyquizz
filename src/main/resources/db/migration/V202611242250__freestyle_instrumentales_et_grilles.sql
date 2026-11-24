CREATE TABLE freestyle_catalogue (
    soiree_id UUID PRIMARY KEY,
    fiches JSONB NOT NULL
);

CREATE TABLE freestyle_contenu_passage (
    passage_id UUID PRIMARY KEY REFERENCES passage(id),
    contenu JSONB NOT NULL
);
