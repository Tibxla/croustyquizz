create table lieu (
	id    uuid primary key,
	nom   varchar(120) not null,
	ville varchar(120) not null
);

create table animateur (
	id                 uuid primary key,
	lieu_id            uuid not null references lieu (id),
	nom                varchar(120) not null,
	email              varchar(254) not null,
	mot_de_passe_hache varchar(60) not null
);

create unique index animateur_email_unique on animateur (lower(email));
create index animateur_lieu_id on animateur (lieu_id);
