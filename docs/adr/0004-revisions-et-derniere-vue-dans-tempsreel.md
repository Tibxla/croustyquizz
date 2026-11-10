---
status: accepted
---

# Les Révisions et la dernière Vue synchronisée appartiennent à `tempsreel`

Le contrat de `docs/api/` impose à chaque écran une reprise sans fenêtre de perte : abonnement vérifié, capture d'une Vue synchronisée et de sa Révision au même point cohérent, puis application des seuls événements de Révision supérieure. Chaque événement STOMP transporte déjà la vue complète de sa destination (« remplacement atomique de la vue, pas un delta », `docs/api/schemas.yaml`). Il restait à décider qui numérote les Révisions et qui fournit la capture.

Nous confions les deux au package `tempsreel`. Le diffuseur numérote les Révisions par destination et garde en mémoire la dernière vue publiée sur chacune. La route d'état d'une destination rend cette vue et sa Révision sous le même verrou par Soirée que la publication : la capture est cohérente par construction, sans coordination avec `soiree`, `quiz`, `blindtest` ou `karaoke`. Côté navigateur, un module de Vue synchronisée applique la séquence du contrat et ne livre aux pages que des vues complètes.

En contrepartie, chaque Mode de jeu publie à chaque changement la vue complète de chaque destination concernée, jamais un changement partiel. La mémoire de `tempsreel` ne survit pas à un redémarrage du serveur : avant le premier événement d'une Soirée, ou après un redémarrage, la route d'état passe par un seam de capture que les propriétaires des packages métier implémentent.

## Alternatives écartées

- Faire capturer la vue par chaque package métier, avec la Révision attribuée ailleurs : chaque Mode de jeu aurait dû reproduire la même synchronisation entre mutation, Révision et capture, et la cohérence aurait reposé sur quatre implémentations.
- Publier des changements partiels : messages plus petits, mais chaque écran aurait dû appliquer des règles de jeu pour reconstruire sa vue, contre l'ADR 0003 qui laisse les règles au serveur.

## Conséquences

- Mise en œuvre suivie dans l'issue #47, après la fusion de la PR #30. Aucun changement du contrat n'est nécessaire.
- La mémoire est par processus : une seconde instance de l'application demanderait un autre stockage des Révisions. Ce n'est pas un objectif de la démo (une seule instance, issue #4).
