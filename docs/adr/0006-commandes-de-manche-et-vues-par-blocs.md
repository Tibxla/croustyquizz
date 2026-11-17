---
status: accepted
---

# Les commandes d'une Manche passent par un service sérialisé par Soirée, et chaque vue est faite de blocs

Les Modes de jeu reçoivent des commandes de deux sources : les Joueurs depuis leur Manette (Réponse, Buzz, inscription d'un Passage, Vote…) et l'Animateur, ou un Joueur ayant une Délégation, depuis la Console (passer un Passage, réordonner la File d'attente, régler une limite…). Le contrat `DeroulementDeManche` n'offrait qu'une entrée pour les Joueurs et imposait un seul fil ; il ne disait ni où vit la transaction, ni comment refuser une commande devenue sans objet, ni qui compose la vue complète de chaque écran. Quatre décisions comblent ce manque.

## 1. Un service d'application par Mode de jeu, sérialisé par Soirée

Toute commande, d'un Joueur ou de la Console, arrive par HTTP sur le service d'application du Mode de jeu concerné. Ce service :

1. prend le verrou de la Soirée (`VerrousSoiree`, rendu public par `tempsreel`, le même que pour la publication et la capture des vues) ;
2. appelle le déroulement de la Manche, qui reste du Java pur, appelé depuis un seul fil à la fois, sans thread ni minuteur à lui (le temps passe par l'`Horloge` du contexte) ;
3. enregistre le nouvel état en base dans une seule transaction ;
4. publie, après validation de la transaction et toujours sous le verrou, la vue complète de chaque destination concernée par `DiffuseurTempsReel` ;
5. rend le résultat HTTP calculé par le déroulement lui-même, jamais recalculé à côté.

Le contrat `DeroulementDeManche` reçoit une entrée pour les commandes de pilotage propres à un mode (`commander(CommandeDeManche)`, à côté de `recevoir(ActionJoueur)`), qui rend un résultat : accepté, avec ses effets, ou refusé, avec un code. Les minuteurs de l'`Horloge` déclenchent le même chemin : verrou, déroulement, transaction, publication.

## 2. Une commande périmée se reconnaît à sa cible, pas à la Révision d'un écran

Une commande qui vise un objet (un Passage, une étape, une Question) porte l'identifiant de cet objet et la version sur laquelle l'acteur a décidé. Le déroulement compare cette version à l'état courant : si l'objet a changé d'une manière qui rend la commande sans objet, la réponse est `409` avec le code `COMMANDE_PERIMEE`, et rien ne change. Une commande sur l'étape en cours garde son `etapeId`, comme le contrat du Quiz le fait déjà.

Les Révisions de l'ADR 0004 restent ce qu'elles sont : l'ordre des vues d'une destination, en mémoire du processus. Elles ne servent pas de garde, parce qu'elles repartent de zéro au redémarrage (une vieille commande pourrait retomber sur la même valeur) et parce que toute publication sur la Console, une simple arrivée de Joueur comprise, périmerait des commandes qui n'ont rien à voir.

## 3. Chaque vue est une partie commune plus un bloc par Mode de jeu

La vue de la salle, de la Console et de chaque Manette se compose :

- d'une partie commune : la Soirée, son programme, les Joueurs, le Classement général, la Manche en cours et son mode ;
- d'un bloc par Mode de jeu (`quiz`, `blindtest`, `karaoke`, `freestyle`), présent et rempli seulement pour la Manche en cours, `null` sinon.

`tempsreel` assemble la vue sous le verrou de la Soirée : il demande la partie commune à `soiree` et le bloc du mode au package de ce mode, par une interface de fourniture de bloc par destination. Le repli de capture de l'ADR 0004 suit le même chemin. Dans `docs/api/schemas.yaml`, le bloc `quiz` devient facultatif et chaque mode ajoute le sien.

## 4. Les refus utilisent le corps `Probleme` existant

Toute réponse d'erreur suit le schéma `Probleme` de `docs/api/schemas.yaml` (`application/problem+json`) avec un champ `code` en majuscules (`LIMITE_ATTEINTE`, `COMMANDE_PERIMEE`, `ETAPE_INACTIVE`…) et un `detail` affichable tel quel à l'acteur. Chaque route documente les codes qu'elle peut rendre. Les commandes d'un Joueur gardent l'`envoiId` de l'ADR 0003 : une répétition identique rend le résultat initial.

## Code commun aux Passages

Le Karaoké et le Freestyle partagent le Passage, la File d'attente et sa rotation, l'appel, « Je suis prêt », la fin du Passage et le Vote du public. Ce code vit dans un package `passage` (propriétaire Tibxla) ; `karaoke` et `freestyle` n'apportent que leur contenu (Chanson et paroles, Instrumentale et Grille de rimes).

## Alternatives écartées

- **Faire muter l'état par `recevoir` et calculer la réponse HTTP dans le service** : deux endroits auraient porté les mêmes règles et auraient fini par diverger.
- **Utiliser la Révision de la Console comme garde de commande** : fragile au redémarrage et trop large (voir 2).
- **Un schéma de vue par Mode de jeu** : chaque écran aurait dû connaître quatre formes de vue et changer de forme à chaque Manche ; la partie commune (Joueurs, classement) aurait été dupliquée.
- **Un corps d'erreur propre à chaque mode** : les écrans auraient dû traiter plusieurs formats pour afficher un refus.
- **Le code des Passages dans `karaoke`** : `freestyle` aurait dépendu d'un autre Mode de jeu.

## Conséquences

- `tempsreel` expose `VerrousSoiree` et une interface de fourniture de bloc ; `manche` ajoute `commander` au contrat ; `docs/api/schemas.yaml` rend `quiz` facultatif. Ces changements sont faits par une issue de socle avant tout Mode de jeu.
- Chaque issue de Mode de jeu suit le même découpage : contrat, domaine en Java pur, service et routes, interface.
- Les tests de déroulement restent en Java pur (`ContratDeroulementDeManche`) ; les tests de service vérifient la transaction, le verrou et la publication après validation.
