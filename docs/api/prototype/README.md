# Prototype du canal temps réel (#7)

Ce socle permet de mesurer le trajet HTTP → serveur → deux clients STOMP avant
l'intégration des accès et des Modes. Il ne prétend pas implémenter le parcours
métier de `../openapi.yaml` et `../asyncapi.yaml`.

- [OpenAPI](openapi.yaml) : heure serveur, destinations de test et commande Buzz.
- [AsyncAPI](asyncapi.yaml) : événement `buzz-recu` diffusé à la salle.
- Pages `/test/ecran.html` puis lien Manette : profil `dev`, actif par défaut.
- En profil `prod`, les deux routes `/api/test/...` sont absentes. La consultation
  `/api/heure` reste disponible. Les fichiers statiques de démonstration restent
  accessibles, mais ne peuvent envoyer de Buzz sans les routes de test.

Les UUID des pages sont arbitraires. Le pseudo vient du navigateur et n'est pas
une identité. Il n'y a ni persistance, ni arbitrage Blind test, ni idempotence,
ni récupération d'état après reconnexion. Un clic accepté produit un événement.
L'horodatage et la publication sont sérialisés par Soirée sur cette instance.
Les clients reçoivent les événements dans l'ordre de publication.

Le diffuseur peut également viser la Console (`/topic/soirees/{id}/console`)
ou une Manette (`/topic/joueurs/{id}`), avec l'enveloppe `{type, donnees}`.
Aucun événement métier n'y est encore défini. Les abonnements ne sont pas encore
protégés : les contrôles d'identité et de portée arrivent avec #5 et #10.
Toutes les trames client STOMP `SEND`, y compris vers `/topic`, sont refusées.

L'accès Joueur authentifié, les révisions et la synchronisation des projections
seront raccordés au contrat métier pendant #6, #8 et #10. Le Buzz d'un Mode
passera par son service métier ; cette route de démonstration ne doit pas devenir
un second chemin de jeu. `GET /api/heure` ne sert pas de barrière d'abonnement.
Les tests attendent explicitement l'enregistrement de chaque abonnement dans
le registre du broker, sans supposer le support de `RECEIPT`.

Validation des deux contrats : `npm test --prefix tools/api`.
Validation du trajet réel et de la concurrence : `./mvnw verify` (Docker requis).
