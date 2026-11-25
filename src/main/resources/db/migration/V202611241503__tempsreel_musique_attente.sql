-- Les Soirées de démonstration sont identifiées par leurs Manches en dev et recette.
create table tempsreel_musique_attente (
	soiree_id uuid primary key,
	etat varchar(6) not null check (etat in ('JOUE', 'COUPEE')),
	morceaux_disponibles boolean
);
