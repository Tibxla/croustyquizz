---
status: accepted
---

# Lire les Instrumentales de Freestyle sur l’ordinateur de l’Écran de salle

Le 24 novembre 2026, Tibxla amende le catalogue prévu dans [#70](https://github.com/Tibxla/croustyquizz/issues/70). Le Freestyle suit l’[ADR 0007](0007-source-audio-du-karaoke.md) pour la musique. Les fichiers audio restent sur l’ordinateur de l’Écran de salle et hors du dépôt.

L’Écran de salle cherche un sous-dossier direct « Instrumentales » dans le dossier de musique choisi, sans tenir compte de la casse ni des accents. Il lit le titre et le tempo dans un nom comme `Titre [92 bpm].mp3`, avec un départ du beat à 0 ms par défaut. Une entrée de `instrumentales.json`, associée au nom exact du fichier audio, a priorité sur ces valeurs. La fiche déclare un titre, un tempo entier en battements par minute et un départ du beat en millisecondes depuis le début du fichier. Un fichier sans fiche exploitable est écarté.

L’Écran de salle déclare les fiches disponibles au serveur, dans la Soirée. Chaque fiche porte un identifiant opaque qui permet à l’écran de retrouver le fichier local. La déclaration transporte cet identifiant et les trois champs de la fiche. Le serveur compose la Grille de rimes et garde la fin du Passage avec l’`Horloge`. La balle et l’affichage des mots suivent la position de lecture locale de l’Instrumentale, selon la décision du 15 novembre.
