-- Liste des Soirées du Lieu (listerSoirees) : filtre sur le Lieu, de la plus récente à la plus ancienne.
create index soiree_lieu_creee_le_idx on soiree (lieu_id, creee_le desc, id desc);
