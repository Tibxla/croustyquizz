create table contact_secret (
    singleton boolean primary key check (singleton),
    cle bytea not null
);

-- Clé stable, propre à cette base, pour signer les liens et protéger les empreintes d’opposition.
insert into contact_secret (singleton, cle)
values (true, uuid_send(gen_random_uuid()) || uuid_send(gen_random_uuid()));

create table contact_du_lieu (
    id uuid primary key,
    lieu_id uuid not null references lieu (id) on delete cascade,
    numero varchar(12) not null check (numero ~ '^\+33[67][0-9]{8}$'),
    prenom varchar(120) not null,
    nom varchar(120) not null,
    consenti_le timestamptz not null,
    mention varchar(600) not null,
    expire_le timestamptz not null,
    unique (lieu_id, numero)
);

create index contact_expiration on contact_du_lieu (expire_le);

create table contact_opposition (
    contact_id uuid primary key,
    lieu_id uuid not null references lieu (id) on delete cascade,
    empreinte bytea not null,
    unique (lieu_id, empreinte)
);
