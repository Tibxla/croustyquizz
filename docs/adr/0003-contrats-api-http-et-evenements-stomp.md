---
status: accepted
---

# Concevoir les contrats HTTP et les événements avant leur implémentation

La Console d'animation, les Manettes et l'Écran de salle doivent partager des échanges explicites pour permettre aux trois membres de l'équipe de développer leurs fonctionnalités sans deviner le comportement des autres packages. Nous adoptons une démarche API-first : les contrats OpenAPI pour HTTP et AsyncAPI pour les événements STOMP sont versionnés et relus avant l'implémentation, puis leur respect est vérifié par les tests.

Les consultations et les commandes métier des interfaces web passent par une API HTTP JSON, y compris les Réponses, les Buzz et les Votes. WebSocket et STOMP servent à diffuser les événements du serveur vers les écrans. Cette répartition donne un seul chemin réseau aux commandes et rend leurs résultats et leurs erreurs explicites ; le serveur conserve l'autorité sur l'identité, l'horodatage, l'ordre des actions et les règles de jeu. La latence du Buzz devra être mesurée sur le parcours réel.

Le choix prolonge l'ADR 0001 : les pages HTML et JavaScript restent servies par l'application Spring Boot unique. Conformément à l'ADR 0002, le serveur MCP adapte les mêmes services métier pour l'Assistant ; il n'introduit pas de seconde implémentation des règles. Les consommateurs à couvrir sont les trois interfaces web et l'Assistant ; une API publique destinée à d'autres développeurs n'est pas un objectif de ce périmètre.

## Reconnexion et envois répétés

Après une coupure, une Manette récupère par HTTP une vue de l'état actuel adaptée au Joueur authentifié, comprenant ses points et l'état de sa Réponse déjà enregistrée, sans révéler de bonne réponse avant sa correction. Elle reprend le suivi des événements STOMP en se resynchronisant avec cette vue ; le contrat devra couvrir la cohérence entre cette récupération et les événements reçus pendant la reconnexion. Les Questions manquées ne sont pas rejouées et les actions périmées ne sont pas renvoyées automatiquement.

La réception d'une Réponse doit être idempotente : répéter le même envoi restitue le résultat initial sans nouvel effet, même si la Question a été clôturée entre les deux tentatives. Le contrat identifiera l'envoi et son auteur pour reconnaître cette répétition. Une nouvelle tentative visant à modifier une Réponse déjà acceptée est refusée. L'admissibilité de la première réception dépend du délai et de l'état constatés par le serveur, jamais de l'heure déclarée par le téléphone ; les tests devront couvrir perte de confirmation, répétition, modification refusée et réception hors délai.

## Alternative et conséquences

- Envoyer certaines commandes par HTTP et d'autres par STOMP aurait prolongé le Buzz de la PR #30, mais aurait imposé deux chemins d'entrée à documenter, sécuriser et tester. L'entrée du Buzz proposée dans cette PR devra donc être adaptée ; la diffusion des événements reste utile.
- Le premier parcours couvert est une Soirée en Format Solo et Pilotage Manuel avec une Manche de Quiz : connexion de l'Animateur, création et préparation, accès du Joueur, lancement, Réponse et résultat. Les autres fonctionnalités étendront les contrats progressivement.
- Cette décision, acceptée le 10 novembre 2026, décrit la cible. Les contrats et exemples sont proposés à la relecture dans `docs/api/` ; les contrôleurs HTTP métier restent à implémenter.
