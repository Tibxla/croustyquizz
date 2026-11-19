-- Un pseudo ne désigne qu'un Joueur dans une Soirée (#10), sans tenir compte des
-- accents, de la casse ni des espaces. Il reste pris après le départ ou
-- l'exclusion de son Joueur : personne ne peut se faire passer pour lui.
alter table joueur add column pseudo_normalise varchar(40);

-- Les Joueurs déjà présents n'avaient pas cette règle : leur clé, « # » suivi de
-- leur identifiant (37 caractères), ne peut ni se répéter ni égaler un pseudo
-- (30 caractères au plus), et la migration passe malgré d'anciens doublons.
update joueur set pseudo_normalise = '#' || id::text;

alter table joueur alter column pseudo_normalise set not null;

create unique index joueur_pseudo_par_soiree on joueur (soiree_id, pseudo_normalise);
