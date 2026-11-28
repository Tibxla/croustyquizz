-- Délégation (#67) : pouvoirs qu'un Animateur confie à un Joueur présent, le temps de la Soirée.
-- Au plus une Délégation par Joueur ; elle est effacée à la fin de la Soirée, et avec le Joueur ou la Soirée.
-- Les Soirées de démonstration, absentes de la table soiree, n'en ont pas.
create table tempsreel_delegation (
	joueur_id       uuid        primary key references joueur (id) on delete cascade,
	soiree_id       uuid        not null references soiree (id) on delete cascade,
	file_attente    boolean     not null,
	musique_attente boolean     not null,
	photos          boolean     not null,
	modifiee_le     timestamptz not null,
	-- Un ensemble vide se dit par le retrait de la Délégation.
	constraint tempsreel_delegation_un_pouvoir check (file_attente or musique_attente or photos)
);

create index tempsreel_delegation_soiree on tempsreel_delegation (soiree_id);
