-- Les Soirées créées avant la connexion des Animateurs restent sans Lieu.
alter table soiree add column lieu_id uuid references lieu (id);
