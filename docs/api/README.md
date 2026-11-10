# Contrats du premier parcours de Quiz

Issue de référence : [#34](https://github.com/Tibxla/croustyquizz/issues/34). Ces fichiers décrivent une **cible à relire par les propriétaires**, pas des contrôleurs déjà déployés. L’application Spring Boot et les pages HTML/JavaScript restent dans le même projet.

| Fichier | Contenu |
|---|---|
| [openapi.yaml](openapi.yaml) | HTTP JSON : accès, préparation, pilotage, Réponses, vues et résultats |
| [asyncapi.yaml](asyncapi.yaml) | Événements du serveur vers les trois destinations STOMP |
| [schemas.yaml](schemas.yaml) | Schémas JSON partagés pour éviter une divergence entre les vues HTTP et les messages |
| [exemples/parcours-quiz.yaml](exemples/parcours-quiz.yaml) | Échanges fictifs, événements, refus de données sensibles et exemple de reconnexion |

Les opérations HTTP sont identifiées par leur `operationId` : les exemples utilisent ces identifiants pour rester vérifiables lorsque les routes évoluent. OpenAPI 3.1 et AsyncAPI 3.1 sont retenus ; les schémas partagés sont compatibles avec JSON Schema 2020-12. Les références de schéma restent locales au dépôt.

## Périmètre

Le premier parcours est : inscription du premier Animateur d’un Lieu, connexion, création d’une Soirée **Solo / Manuel**, préparation d’une Manche de **Quiz**, ouverture aux Joueurs, lancement, trois Questions et Classement général final. Les Questions de ce parcours sont rédigées par l’Animateur ; la banque française et l’import relèvent des extensions du Quiz.

Le code d’accès est créé à l’ouverture, reste utilisable pour les arrivées tardives et n’est pas une preuve d’identité. La préparation et le programme restent modifiables en `PREPARATION` et `OUVERTE`, conformément à #9. Le lancement vérifie que le programme est non vide et que chaque Manche dispose du nombre de Questions prévu. Chaque Question a quatre propositions distinctes et une seule solution.

Les autres Modes de jeu, le Format Équipe, le Pilotage Automatique, les invitations d’Animateurs, la modération et le serveur MCP étendront ce contrat. Le Buzz est inclus uniquement pour le scénario du socle #7/#8, avant le lancement d’une Manche ; il est refusé pendant le Quiz. Son arbitrage en Blind test relève de #17. Les contrats n’imposent aucune formule de points : les règles de dégressivité et de départage du PRD restent à implémenter dans `quiz` et `classement`.

## Accès aux vues

| Acteur | Identité HTTP | Abonnements STOMP autorisés | Vues HTTP |
|---|---|---|---|
| Animateur | Cookie de session `JSESSIONID`, Lieu déduit par le serveur | Salle et Console des Soirées de son Lieu | Salle, Console, Questions de préparation et résultats de son Lieu |
| Joueur | `Authorization: Bearer <jetonReconnexion>` | Salle de sa Soirée et son propre canal Joueur | Salle, sa Manette et résultats de sa Soirée |
| Écran de salle | `Authorization: Bearer <cleEcran>` | Salle de la Soirée associée à cette capacité | Salle et résultats de cette Soirée, en lecture seule |

Les jetons sont opaques. Le serveur en déduit l’identité et la portée ; une route ou un corps contenant un UUID ne confère aucun droit. Un Joueur ne peut agir pour un autre, un Animateur ne peut accéder à un autre Lieu et une clé d’Écran ne peut commander le jeu. Les ressources hors de la portée autorisée répondent `404`, les identités absentes ou expirées `401`.

Le jeton Joueur doit rester utilisable pour la reconnexion pendant la Soirée ; sa durée de conservation après la fin sera précisée avec l’implémentation des accès. La clé d’Écran indique sa propre expiration. Une clé expirée nécessite un nouvel accès créé depuis la Console. Les capacités ne sont transmises ni dans les événements, ni dans une chaîne de requête, ni dans les journaux. Le transfert initial d’une clé d’Écran peut utiliser un fragment URL, retiré par la page après lecture. Tous les secrets et vues sont servis avec `Cache-Control: no-store` ; HTTPS est requis hors développement local.

Pour la session Animateur, obtenir un jeton par `GET /api/csrf`, puis envoyer `X-CSRF-TOKEN` avec les opérations mutantes. L’inscription et la connexion sont également protégées contre les requêtes intersites. Après connexion/déconnexion, récupérer un nouveau jeton. Le cookie de session est `HttpOnly`, `SameSite=Lax` et `Secure` en HTTPS.

Pour STOMP 1.2, le sous-protocole est `v12.stomp`. Le navigateur envoie le jeton CSRF dans `CONNECT`, ainsi que `Authorization: Bearer …` pour un Joueur ou un Écran de salle ; la Console utilise la session du handshake. Il s’agit de **headers STOMP de CONNECT**, pas d’un header HTTP arbitraire ajouté par le constructeur WebSocket du navigateur. Si un jeton Bearer est présent, l’identité est celle de ce jeton ; une session Animateur présente dans le même navigateur ne permet pas d’élargir ses droits. Le serveur vérifie l’origine, l’identité et chaque `SUBSCRIBE`, refuse les commandes métier `SEND`, et ferme les connexions dont l’accès a expiré ou été invalidé. L’utilisation de la clé d’Écran est limitée au canal et aux vues décrits dans le tableau.

## Vues et événements

Les vues publiques ne contiennent aucune solution pendant `QUESTION_OUVERTE` ou `EN_PAUSE`. Les résultats personnels et les Points visibles sont publiés **après correction**. Les Questions privées de préparation ont leur propre schéma et une route réservée à l’Animateur ; elles ne sont jamais sérialisées directement sur un canal public.

Les événements portent `type`, `soireeId`, `revision`, `emisLe` et `donnees`. `donnees` est la **projection complète** de la vue concernée ; le navigateur la remplace atomiquement. Cela facilite la reprise après coupure et évite d’exiger la relecture de tous les deltas. Le `type` décrit la cause du changement. La version initiale `{type, donnees}` de la PR #30 devra donc être enrichie.

La salle suit `/topic/soirees/{soireeId}/salle`, la Console `/topic/soirees/{soireeId}/console` et la Manette `/topic/joueurs/{joueurId}`. La Manette utilise son canal personnel comme source complète de sa vue ; elle n’a pas besoin de combiner deux flux pour retrouver son état. La diffusion du serveur met à jour les projections concernées par chaque mutation, notamment tous les canaux Joueur lorsque le Quiz avance.

L’ordre est garanti **par destination et par Soirée**, avec une révision contiguë croissante pour chaque événement publié sur cette destination. Deux destinations ont des compteurs indépendants : une révision de salle n’est pas comparable à une révision de Manette. Les captures HTTP, mutations et attributions de révision doivent être cohérentes avec la sérialisation d’une même Soirée. Les messages ne constituent pas un journal durable ; un écran resynchronise sa vue après toute coupure, erreur d’application ou rupture dans la suite des révisions. Les heartbeats STOMP détectent les connexions inactives ; le rythme prévu dans le contrat est de dix secondes.

## Reconnexion sans fenêtre de perte

Un simple « lire l’état puis s’abonner » peut perdre un événement entre les deux opérations. Le contrat utilise la séquence suivante pour chaque écran :

1. Ouvrir la connexion authentifiée, générer un UUID neuf pour l’abonnement et envoyer `SUBSCRIBE` avec cet UUID dans le header `id`. Le serveur rattache l’abonnement installé à cette connexion, à son acteur et à sa destination.
2. Mettre immédiatement les messages reçus en tampon. Appeler la route `/etat/salle`, `/etat/console` ou `/etat/manette` avec `?abonnementId=<cet UUID>`.
3. Tant que l’abonnement n’est pas installé ou autorisé pour cet acteur et cette vue, le serveur répond `409 ABONNEMENT_INACTIF`. Réessayer après une courte attente, avec un nombre de tentatives borné ; si la connexion tombe, recommencer avec un nouvel UUID. On ne suppose pas que le broker simple Spring implémente les `RECEIPT` de STOMP.
4. Après vérification de l’abonnement, le serveur capture une vue et sa révision au même point cohérent. La réponse restitue l’UUID d’abonnement, cette révision et l’heure du serveur.
5. Installer la vue reçue, ignorer les événements de révision inférieure ou égale à la capture, puis appliquer dans l’ordre ceux qui la suivent. Ignorer également les doublons. Si une révision manque, abandonner cette synchronisation et récupérer une nouvelle vue cohérente.
6. Une réponse HTTP appartenant à un ancien abonnement ne peut remplacer la vue du nouvel abonnement. Borner le tampon ; si sa capacité est dépassée, recommencer la synchronisation. L’écran reste en état de reconnexion tant que cette procédure n’a pas réussi.

L’ordre « abonnement effectivement installé avant capture » empêche la fenêtre de perte, y compris lorsque la réponse HTTP est retardée et qu’un événement plus récent arrive avant elle. Les détails de registre d’abonnement appartiennent à l’adaptateur temps réel ; l’interface promise aux écrans est la vérification de l’UUID et la capture cohérente. Sans paramètre `abonnementId`, les routes d’état restent des lectures ponctuelles, sans garantie de raccordement à un flux.

La Manette retrouve ainsi ses points et sa Réponse déjà enregistrée. Les Questions manquées ne sont pas rejouées ; un Joueur arrivé au milieu d’une Manche voit `eligiblePourMancheActive: false` et attend la suivante. Les actions périmées ne sont jamais rejouées automatiquement.

## Réponses, délais et envois répétés

Le corps d’une Réponse contient uniquement `envoiId` et `propositionId`. L’identité vient du jeton, et l’étape est identifiée dans la route. Le serveur horodate et ordonne les actions ; une date ou un nombre de points transmis par le téléphone est refusé par le schéma.

Après contrôle d’identité et de portée, le serveur traite les répétitions **avant** de vérifier si la Question est encore ouverte :

| Cas | Résultat attendu |
|---|---|
| Première Réponse éligible, reçue strictement avant l’échéance | `200`, accusé immuable sans indication de justesse |
| Même auteur, même route, même `envoiId` et même contenu | Même statut et même accusé initial ; aucun nouveau point ou événement, même après clôture |
| Même envoi réutilisé avec un autre contenu | `409 ENVOI_REUTILISE` |
| Nouvel envoi tentant de modifier une Réponse acceptée | `409 REPONSE_DEJA_ENREGISTREE` |
| Première réception à l’échéance ou après | `409 HORS_DELAI` |
| Autre étape, Manche inactive ou Joueur non éligible | `409 ETAPE_INACTIVE` ou `ETAT_INCOMPATIBLE` |

Un retry après perte de confirmation réutilise donc l’UUID et le corps conservés avant le premier envoi. La déduplication reste disponible jusqu’à expiration de l’accès Joueur, au minimum jusqu’à la fin de la Soirée ; les garanties après clôture supposent une identité encore valide. Les commandes de pilotage et d’ouverture utilisent le même principe via le header `Idempotency-Key`. Les codes et corps d’erreur sont décrits par `Probleme` dans `schemas.yaml`.

Un accusé HTTP retrouvé après répétition est un reçu de l’opération initiale : il ne doit pas remplacer une projection temps réel plus récente. Les écrans prennent leur état courant dans les captures cohérentes et événements révisés, même lorsqu’une ancienne réponse HTTP finit par arriver.

La pause gèle le temps restant ; la reprise crée une nouvelle échéance serveur. En Manuel, `PASSER_ETAPE` clôt d’abord une Question ouverte et montre sa correction ; un nouvel appel, avec un nouvel identifiant d’envoi, passe à la Question suivante. Après la dernière correction, la Manche est terminée. L’Animateur demande ensuite `MANCHE_SUIVANTE`, qui clôt la Soirée si le programme est épuisé. L’arrêt explicite d’une Manche abandonne l’étape non corrigée sans lui attribuer de points.

## Vérifier les fichiers

La validation est indépendante du serveur et de PostgreSQL : Node.js 24 est nécessaire uniquement pour les outils de contrats. Elle n’ajoute ni compilation front ni bundler et ne modifie pas les commandes Maven de l’application.

```sh
npm ci --prefix tools/api --ignore-scripts --no-fund --no-audit
npm test --prefix tools/api
```

Le job `contrats-api` de GitHub Actions exécute les mêmes commandes. Les versions des validateurs et leurs dépendances sont verrouillées dans `tools/api/package-lock.json`. Les vérifications portent sur la structure OpenAPI/AsyncAPI, les références locales, la compilation de tous les schémas, les requêtes et réponses du parcours, les événements, les cas de refus de données sensibles et le scénario de raccordement entre capture et tampon. Les exemples utilisent des UUID, secrets et adresses fictifs.

Ces contrôles **ne prouvent pas la conformité d’un serveur non implémenté**. Les futures tâches #5, #6, #7, #8, #10, #12 et #14 devront vérifier les mêmes échanges contre Spring Boot : isolement entre Lieux et Joueurs, abonnements non autorisés, CSRF, autorité temporelle, pause/reprise, perte de confirmation, répétition après clôture, modification refusée et événement survenant pendant la reconnexion. Les tests utiliseront PostgreSQL via Testcontainers, une Horloge factice, des commandes HTTP et des clients STOMP ; la charge de soixante Joueurs reste à mesurer sur ce parcours réel.

## Relecture avant implémentation

- **Piyakabib** : accès Animateur, préparation et règles du Quiz, résultats.
- **nRayen** : cycle de Soirée, programme, accès Joueur et Pilotage Manuel.
- **Tibxla** : contrat de Manche, destinations, autorisations STOMP, ordre et resynchronisation.

Une approbation collective du contrat reste attendue. Les noms de routes, formes de messages et limites de saisie sont des choix proposés à cette relecture ; le PRD reste la référence fonctionnelle. Le contrat borne une Manche à 100 Questions et une étape à 3 600 secondes pour cette première version.

Références : [OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0), [AsyncAPI 3.1](https://www.asyncapi.com/docs/reference/specification/v3.1.0), [validation AsyncAPI](https://www.asyncapi.com/docs/guides/validate), [Swagger Parser](https://github.com/APIDevTools/swagger-parser).
