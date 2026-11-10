-- Soirée et Joueurs qui la rejoignent (#6).
-- Le lien avec le Lieu et l'Animateur arrive avec la connexion de l'Animateur (#5).

create table soiree (
	id          uuid        primary key,
	titre       varchar(150) not null,
	format      varchar(16) not null check (format in ('SOLO', 'EQUIPE')),
	pilotage    varchar(16) not null check (pilotage in ('MANUEL', 'AUTOMATIQUE')),
	etat        varchar(16) not null check (etat in ('PREPARATION', 'OUVERTE', 'EN_COURS', 'TERMINEE')),
	-- Six caractères sans 0, O, 1 ni I ; attribué à l'ouverture, conservé ensuite.
	code_acces  varchar(6)  check (code_acces ~ '^[A-HJ-NP-Z2-9]{6}$'),
	creee_le    timestamptz not null,
	ouverte_le  timestamptz,
	debutee_le  timestamptz,
	terminee_le timestamptz,
	constraint soiree_code_des_l_ouverture check ((etat = 'PREPARATION') = (code_acces is null))
);

-- Un code d'accès ne désigne qu'une seule Soirée qu'on peut encore rejoindre.
-- Une fois la Soirée terminée, son code peut resservir.
create unique index soiree_code_acces_actif
	on soiree (code_acces)
	where etat in ('OUVERTE', 'EN_COURS');

create table joueur (
	id              uuid        primary key,
	soiree_id       uuid        not null references soiree (id),
	-- La longueur (1 à 30 caractères) est vérifiée par l'application.
	pseudo          varchar(30) not null,
	-- Empreinte SHA-256 du jeton de reconnexion : le jeton lui-même n'est jamais stocké.
	empreinte_jeton bytea       not null unique check (length(empreinte_jeton) = 32),
	arrive_le       timestamptz not null,
	exclu           boolean     not null default false
);

create index joueur_soiree_arrivee on joueur (soiree_id, arrive_le);
