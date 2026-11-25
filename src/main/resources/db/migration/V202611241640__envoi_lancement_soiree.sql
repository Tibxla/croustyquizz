-- Permet de reprendre le même lancement si la validation de la Soirée a
-- réussi mais que la transaction de démarrage de sa première Manche a échoué.
ALTER TABLE soiree ADD COLUMN envoi_lancement UUID;
