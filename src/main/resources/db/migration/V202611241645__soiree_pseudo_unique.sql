-- Un pseudo ne désigne qu'un Joueur dans une Soirée (#10), sans tenir compte des
-- accents, de la casse ni des espaces. Il reste pris après le départ ou
-- l'exclusion de son Joueur : personne ne peut se faire passer pour lui.
alter table joueur add column pseudo_normalise varchar(40);

-- Les Joueurs déjà présents reçoivent la clé de Joueur.cleDePseudo, pour que
-- leurs pseudos soient réservés comme ceux des nouveaux arrivants : marques
-- diacritiques retirées (NFD puis NFC), blancs Unicode (insécables compris)
-- réduits à une espace, minuscules. Œ, Æ et Ø ne se décomposent pas : ils sont
-- passés en minuscules à la main, lower() ne le faisant pas sous toutes les
-- collations. Hors de l'alphabet latin, lower() suit la collation de la base.
update joueur set pseudo_normalise = lower(btrim(regexp_replace(
	normalize(regexp_replace(normalize(translate(pseudo, 'ŒÆØ', 'œæø'), NFD),
		'[̀-ͯ᪰-᫿᷀-᷿⃐-⃿︠-︯]', '', 'g'), NFC),
	'[\s\u0085   -     　]+', ' ', 'g'), ' '));

-- D'anciens doublons (« Léa » puis « LEA » dans la même Soirée) ne peuvent pas
-- partager une clé : le premier arrivé la garde ; les suivants reçoivent « # »
-- suivi de leur identifiant (37 caractères), qui ne peut égaler aucun pseudo
-- (30 caractères au plus).
update joueur
set pseudo_normalise = '#' || joueur.id::text
from (
	select id, row_number() over (partition by soiree_id, pseudo_normalise order by arrive_le, id) as rang
	from joueur
) doublons
where doublons.id = joueur.id and doublons.rang > 1;

alter table joueur alter column pseudo_normalise set not null;

create unique index joueur_pseudo_par_soiree on joueur (soiree_id, pseudo_normalise);
