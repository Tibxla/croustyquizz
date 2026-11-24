# Dictionnaire de rimes du Freestyle

Les listes **Soirée**, **Ville** et **Sport**, ainsi que le filtre de vulgarité, ont été écrits pour CroustyQuizz. Ils sont distribués sous la [licence MIT du dépôt](../../../../LICENSE). Chaque thème a deux Familles de rimes. Chacune fournit au moins 32 suggestions distinctes pour une Grille de rimes en AAAA, même après retrait du mot de départ.

## Écrire une liste

Les fichiers TSV sont en UTF-8. Chaque ligne associe `mot`, `terminaison` et `fréquence`, séparés par des tabulations.

- Le mot garde son orthographe française et ses accents.
- La terminaison note les derniers sons prononcés en alphabet phonétique international simplifié, séparés par des espaces. Inscrire la dernière voyelle et tous les sons qui la suivent, ainsi que les sons qui la précèdent pour avoir au moins trois sons si le mot le permet. Un son nasal comme `ɔ̃` forme un seul élément. Le `e` final muet est omis.
- La fréquence de 1 à 100 est une appréciation de l'usage courant, estimée à la rédaction. Plus elle est élevée, plus le mot est courant.

`lumière` et `pierre` ont la terminaison `j ɛ ʁ`. Avec `serre` (`s ɛ ʁ`), ils partagent la Famille de rimes `ɛ ʁ`. Pour `lumière`, `pierre` passe en tête grâce aux trois sons communs. Dans chaque groupe, la fréquence décroissante détermine l'ordre, puis l'orthographe départage les égalités.

`mots-filtres.txt` contient une forme par ligne. Les mots sont écartés avant l'indexation, y compris leurs variantes de casse et d'accentuation. Chaque flexion à écarter doit figurer dans le fichier.

## Ajouter Lexique 3.83 plus tard

Le futur lecteur de Lexique 3.83 pourra convertir `ortho`, `phon` et `freqfilms2` en `EntreePhonetique`, le format Java commun du dictionnaire. Il devra convertir les symboles de `phon` en sons séparés et définir une échelle comparable pour la fréquence. Les entrées du lexique général pourront rejoindre l'index sans thème et passer par le même filtre. Le port `DictionnaireDeRimes` restera le même.

Ce lot utilise uniquement les listes du projet. Ajouter la licence CC BY-SA 4.0 et l'attribution de Lexique 3.83 au moment de son intégration.
