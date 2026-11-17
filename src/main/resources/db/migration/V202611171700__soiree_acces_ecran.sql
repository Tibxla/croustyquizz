-- Accès de lecture des Écrans de salle (#6) : une clé par Écran ouvert depuis la Console.
create table acces_ecran (
	id           uuid        primary key,
	soiree_id    uuid        not null references soiree (id),
	-- Empreinte SHA-256 de la clé : la clé elle-même n'est jamais stockée.
	empreinte_cle bytea      not null unique check (length(empreinte_cle) = 32),
	cree_le      timestamptz not null,
	expire_le    timestamptz not null check (expire_le > cree_le)
);

create index acces_ecran_soiree on acces_ecran (soiree_id);
