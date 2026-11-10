# Prototype du canal temps réel (#7, #47)

Ce socle mesure HTTP → serveur → clients STOMP sans accès métier ni Mode de jeu.
Les pages `/test/ecran.html` et `/test/manette.html` suivent la destination salle
avec `suivreVue`. Il n'existe pas de troisième page Console dans cette branche.

- [OpenAPI](openapi.yaml) : heure serveur, destinations, commande Buzz et capture de salle.
- [AsyncAPI](asyncapi.yaml) et [schémas](schemas.yaml) : vues complètes révisées.
- Les routes `/api/test/...` restent en profil `dev` seulement ; `/api/heure` reste publique.
- La vue conserve les 64 derniers Buzz, du plus ancien au plus récent. Une commande
  acceptée publie toute la vue, horodatée avec la Clock du serveur.

La salle suit les six étapes de [reconnexion](../README.md#reconnexion-sans-fenêtre-de-perte).
La capture et la publication partagent le verrou de la Soirée. La première capture
utilise un adapter de repli vide, Révision zéro. Les vues et compteurs restent en
mémoire jusqu'au redémarrage, sans persistance ni éviction pendant une connexion.

Le registre observe le traitement effectif du broker, sans accès à son registre
interne ni hypothèse de RECEIPT. Il lie l'UUID d'abonnement à la connexion STOMP
et à sa destination, et le retire à UNSUBSCRIBE ou déconnexion. Un UUID ambigu
sur plusieurs connexions est inactif. Un UUID neuf est obligatoire à chaque reprise.
Le prototype est anonyme : l'UUID permet le raccordement au flux, pas l'authentification
de l'appelant HTTP. Le contrôle de l'acteur et des droits reste à intégrer avec #5/#10.
Toutes les trames client STOMP SEND sont refusées.

Les canaux Console et Joueur restent des sorties techniques sans route de capture
ni projection métier. Le canal Joueur ne connaît pas encore sa Soirée ; son enveloppe
porte `soireeId: null` jusqu'à l'intégration des accès. Leurs Révisions sont indépendantes.
Le Buzz n'a ni idempotence ni arbitrage Blind test, son pseudo n'est pas une identité.

`suivreVue(options, adaptations)` reçoit facultativement un transport (connexion,
abonnement, capture HTTP), des minuteries, une horloge monotone et un générateur
UUID. Les limites sont réglables : cinq tentatives HTTP à 250 ms sur
`409 ABONNEMENT_INACTIF`, tampon de 64 messages, reconnexion complète après échec.
Une requête de capture sans réponse est abandonnée après dix secondes (réglable).
Une capture appelle `surVue(vue, { type: 'capture' })` ; les événements transmettent
leur type. `maintenantServeur()` retourne des millisecondes depuis l'époque, ou
`null` avant la première capture, recalées à chaque capture sans dépendre des
changements de l'heure locale. `arreter()` annule transport, requêtes et minuteries.

Validation : `npm test --prefix tools/api` (contrats et vrai module ES via Node 24).
Trajet STOMP réel et concurrence : `./mvnw verify` (Docker requis).
