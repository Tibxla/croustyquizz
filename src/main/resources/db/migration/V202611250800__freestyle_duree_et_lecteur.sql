-- La fenêtre d’Écran de salle qui a déclaré les fiches en dernier joue seule les Instrumentales.
ALTER TABLE freestyle_catalogue ADD COLUMN lecteur_id UUID;

-- Une déclaration sans durée sera renvoyée au prochain choix du dossier de musique.
DELETE FROM freestyle_catalogue
WHERE EXISTS (SELECT 1 FROM jsonb_array_elements(fiches) AS fiche WHERE fiche -> 'dureeMs' IS NULL);

-- Une fiche figée sans durée reçoit la fin de sa Grille, que son fichier couvrait déjà.
UPDATE freestyle_contenu_passage
SET contenu = jsonb_set(contenu, '{instrumentale,dureeMs}', to_jsonb(
    (contenu #>> '{instrumentale,departBeatMs}')::bigint
    + round((contenu #>> '{grille,nombreMesures}')::numeric * 240000
        / (contenu #>> '{instrumentale,tempoBpm}')::numeric)::bigint))
WHERE contenu #> '{instrumentale,dureeMs}' IS NULL;
