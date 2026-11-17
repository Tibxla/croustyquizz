# Contrats du Quiz et de la File d’attente du Karaoké

Issues de référence : [#34](https://github.com/Tibxla/croustyquizz/issues/34) et [#82](https://github.com/Tibxla/croustyquizz/issues/82), tranche de contrat de [#21](https://github.com/Tibxla/croustyquizz/issues/21). Ces fichiers décrivent une **cible à relire par les propriétaires**, pas des contrôleurs déjà déployés. L’application Spring Boot et les pages HTML/JavaScript restent dans le même projet.

| Fichier | Contenu |
|---|---|
| [openapi.yaml](openapi.yaml) | HTTP JSON : accès, préparation, pilotage, Réponses, vues et résultats |
| [asyncapi.yaml](asyncapi.yaml) | Événements du serveur vers les trois destinations STOMP |
| [schemas.yaml](schemas.yaml) | Schémas JSON partagés pour éviter une divergence entre les vues HTTP et les messages |
| [exemples/parcours-quiz.yaml](exemples/parcours-quiz.yaml) | Échanges fictifs, événements, refus de données sensibles et exemple de reconnexion |
| [exemples/parcours-karaoke.yaml](exemples/parcours-karaoke.yaml) | Recherche, inscription, File d’attente, refus, reçus et reconnexion du Karaoké |

Les opérations HTTP sont identifiées par leur `operationId` : les exemples utilisent ces identifiants pour rester vérifiables lorsque les routes évoluent. OpenAPI 3.1 et AsyncAPI 3.1 sont retenus ; les schémas partagés sont compatibles avec JSON Schema 2020-12. Les références de schéma restent locales au dépôt.

## Périmètre

Le premier parcours est : inscription du premier Animateur d’un Lieu, connexion, création d’une Soirée **Solo / Manuel**, préparation d’une Manche de **Quiz**, ouverture aux Joueurs, lancement, trois Questions et Classement général final. Les Questions de ce parcours sont rédigées par l’Animateur ; la banque française et l’import relèvent des extensions du Quiz.

Le code d’accès est créé à l’ouverture, reste utilisable pour les arrivées tardives et n’est pas une preuve d’identité. La préparation et le programme restent modifiables en `PREPARATION` et `OUVERTE`, conformément à #9. Le lancement vérifie que le programme est non vide et que chaque Manche de Quiz dispose du nombre de Questions prévu, sinon `409 PROGRAMME_INCOMPLET`. Les Manches de Karaoké et de Freestyle n’ont pas de Questions. Chaque Question a quatre propositions distinctes et une seule solution.

La File d’attente du Karaoké étend ce parcours ci-dessous. Le Blind test, le Freestyle, le Format Équipe, le Pilotage Automatique, les invitations d’Animateurs, la modération et le serveur MCP étendront ce contrat. Le Buzz est inclus uniquement pour le scénario du socle #7/#8, avant le lancement d’une Manche ; il est refusé pendant le Quiz. Son arbitrage en Blind test relève de #17. Les contrats n’imposent aucune formule de points : les règles de dégressivité et de départage du PRD restent à implémenter dans `quiz` et `classement`.

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

## Vues par blocs

Selon l’[ADR 0006](../adr/0006-commandes-de-manche-et-vues-par-blocs.md), les trois vues partagent une partie commune : Soirée, Manche active, Joueurs, Classement général et dernier Buzz ; la Console porte aussi le programme et la Manette ses informations personnelles. Le champ obligatoire `modeEnCours` vaut `QUIZ`, `BLIND_TEST`, `KARAOKE`, `FREESTYLE` ou `null` hors Manche.

La Manche en cours remplit le seul bloc de son Mode de jeu (`quiz`, `blindtest`, `karaoke` ou `freestyle`). Ce bloc peut rester `null` sans fournisseur ou après la fin. Les autres blocs valent `null` ou sont absents. Hors Manche, `modeEnCours` vaut `null` et tous les blocs sont nuls ou absents ; les anciens exemples `quiz: {phase: ATTENTE}` hors Manche deviennent donc `quiz: null`. Une vue porte au plus un bloc non nul, correspondant à `modeEnCours` : cette règle est validée par le schéma partagé `BlocsDeMode` sur les trois destinations. Le serveur compose une vue dont `modeEnCours` correspond à la Manche active.

`quiz` est facultatif, de type `EtatQuiz` ou `null`. `karaoke` est facultatif, de type `BlocKaraokeSalle`, `BlocKaraokeConsole` ou `BlocKaraokeManette` selon la destination, ou `null`. Un bloc Karaoké non nul exige une Manche active de Karaoké en `EN_COURS` ou `EN_PAUSE`. Les blocs facultatifs `blindtest` et `freestyle` n’acceptent encore que `null`. La création et la description des Manches acceptent `QUIZ` et `KARAOKE`, avec les réglages propres à chaque mode. La confidentialité du Quiz reste inchangée : si le bloc `quiz` est présent et non nul, aucun résultat personnel n’est révélé en `ATTENTE`, `QUESTION_OUVERTE` ou `EN_PAUSE`. En `KARAOKE`, `derniereReponse` et `resultatPersonnel` restent obligatoires et nuls, même sans bloc fourni.

Dans la partie commune de la Manette, `eligiblePourMancheActive` dit si le Joueur prend part à la Manche active. Pour un retardataire, le serveur le met à `true` quand le Mode de jeu de cette Manche admet les retardataires (Karaoké, Freestyle), à `false` sinon (Quiz, Blind test). Les pages n’en déduisent rien d’autre : le message « Tu participeras à la prochaine Manche » s’appuie sur ce seul champ, sans regarder `modeEnCours`. La règle de jeu reste au serveur, et un futur Mode de jeu n’a rien à ajouter côté navigateur.

Les captures HTTP et les messages STOMP utilisent les mêmes schémas de vue. Les trois canaux restent inchangés et chaque message remplace toujours la vue complète, partie commune et blocs compris.

## Reconnexion sans fenêtre de perte

Un simple « lire l’état puis s’abonner » peut perdre un événement entre les deux opérations. Le contrat utilise la séquence suivante pour chaque écran :

1. Ouvrir la connexion authentifiée, générer un UUID neuf pour l’abonnement et envoyer `SUBSCRIBE` avec cet UUID dans le header `id`. Le serveur rattache l’abonnement installé à cette connexion, à son acteur et à sa destination.
2. Mettre immédiatement les messages reçus en tampon. Appeler la route `/etat/salle`, `/etat/console` ou `/etat/manette` avec `?abonnementId=<cet UUID>`.
3. Tant que l’abonnement n’est pas installé ou autorisé pour cet acteur et cette vue, le serveur répond `409 ABONNEMENT_INACTIF`. Réessayer après une courte attente, avec un nombre de tentatives borné ; si la connexion tombe, recommencer avec un nouvel UUID. On ne suppose pas que le broker simple Spring implémente les `RECEIPT` de STOMP.
4. Après vérification de l’abonnement, le serveur capture une vue et sa révision au même point cohérent. La réponse restitue l’UUID d’abonnement, cette révision et l’heure du serveur.
5. Installer la vue reçue, ignorer les événements de révision inférieure ou égale à la capture, puis appliquer dans l’ordre ceux qui la suivent. Ignorer également les doublons. Si une révision manque, abandonner cette synchronisation et récupérer une nouvelle vue cohérente.
6. Une réponse HTTP appartenant à un ancien abonnement ne peut remplacer la vue du nouvel abonnement. Borner le tampon ; si sa capacité est dépassée, recommencer la synchronisation. L’écran reste en état de reconnexion tant que cette procédure n’a pas réussi.

L’ordre « abonnement effectivement installé avant capture » empêche la fenêtre de perte, y compris lorsque la réponse HTTP est retardée et qu’un événement plus récent arrive avant elle. Les détails de registre d’abonnement appartiennent à l’adaptateur temps réel ; l’interface promise aux écrans est la vérification de l’UUID et la capture cohérente. Sans paramètre `abonnementId`, les routes d’état restent des lectures ponctuelles, sans garantie de raccordement à un flux.

La Manette retrouve ainsi ses points et sa Réponse déjà enregistrée. Les Questions manquées ne sont pas rejouées ; pour le Quiz, un Joueur arrivé au milieu d’une Manche voit `eligiblePourMancheActive: false` et attend la suivante. Au Karaoké, le serveur met ce champ à `true` pour le retardataire, qui s’inscrit dès son arrivée (voir « Vues par blocs »). Les actions périmées ne sont jamais rejouées automatiquement.

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

Une commande visant un objet porte sa cible et la version sur laquelle l’acteur a décidé : si la cible a changé et rend la commande sans objet, le serveur répond `409 COMMANDE_PERIMEE` sans mutation ([ADR 0006](../adr/0006-commandes-de-manche-et-vues-par-blocs.md)). La Révision d’un écran ne sert jamais de garde de commande. Le Quiz conserve sa garde `etapeId` : absent ou non courant, il entraîne `409 ETAPE_INACTIVE`. Ce champ est facultatif dans `CommandeManche` pour permettre les Manches sans étape courante (Karaoké, Freestyle).

Une base ou une transaction indisponible produit `503 SERVICE_INDISPONIBLE` au format `Probleme`, décrit par la réponse réutilisable `Erreur503`. L’envoi peut être réessayé avec le même `envoiId`, ou la même clé `Idempotency-Key` pour les commandes de pilotage et d’ouverture, et le même contenu.

Un accusé HTTP retrouvé après répétition est un reçu de l’opération initiale : il ne doit pas remplacer une projection temps réel plus récente. Les écrans prennent leur état courant dans les captures cohérentes et événements révisés, même lorsqu’une ancienne réponse HTTP finit par arriver.

La pause gèle le temps restant ; la reprise crée une nouvelle échéance serveur. En Manuel, `PASSER_ETAPE` clôt d’abord une Question ouverte et montre sa correction ; un nouvel appel, avec un nouvel identifiant d’envoi, passe à la Question suivante. Après la dernière correction, la Manche est terminée. L’Animateur demande ensuite `MANCHE_SUIVANTE`, qui clôt la Soirée si le programme est épuisé. L’arrêt explicite d’une Manche abandonne l’étape non corrigée sans lui attribuer de points.

## Karaoké

Cette tranche fixe la recherche de Chansons, l’inscription personnelle et la gestion de la File d’attente. Le mécanisme commun vit dans `passage`, le contenu dans `karaoke` (ADR 0006). Un seul Interprète par Passage ici ; les tableaux acceptent déjà un à quatre Interprètes pour #66. Aucun Point n’est attribué au Karaoké, même en Format Équipe. Appel, lecture et fin de Passage (#22), Vote du public (#23), choix audio (#20), Délégation et ajout pour autrui sont hors périmètre. Le futur port `LecteurDeChanson` couvre charger, lire, pause, reprise, position, fin et erreur ; aucune route de lecture n’est ajoutée ici.

### Rotation et cycle

Le nouvel inscrit rejoint la fin du Tour ouvert s’il n’y a pas chanté, sinon le suivant. Son deuxième Passage vient après le premier de chacun des autres. L’Animateur place un Passage dans son Tour ; pour le faire passer juste après le courant, il pose une priorité unique, qui compte comme le Tour de l’Interprète. La priorité conserve l’ordre normal sous-jacent ; le retirer ou le remplacer restaure cet ordre. `true` remplace la priorité, `false` retire celle de la cible ou reste sans effet. Seuls les Passages affectés changent de compteur.

La limite porte sur les Passages `EN_ATTENTE` de chaque Interprète. La baisser ne retire rien. Une désinscription suivie d’une réinscription rejoint la fin du Tour admissible. Pas de doublon de Chanson personnel en attente ; deux Joueurs peuvent choisir la même Chanson. Un retardataire présent peut s’inscrire ; sa vue porte `eligiblePourMancheActive: true`, mais ce champ n’est jamais une garde de l’inscription.

Les transitions sont absent → `EN_ATTENTE` → `RETIRE` (retrait ou désinscription), ou `ANNULE` à la fin de la Manche. La future extraction (#22) fera `EN_ATTENTE` → `APPELE`, avec le courant séparé de la File d’attente. Un appel sauté après 30 secondes sans « Je suis prêt » compte comme chanté. Aucun nombre de Passages n’est fixé. L’Animateur termine la Manche ; en Automatique, la fin naturelle sera raccordée par #22 quand la File d’attente est vide à la fin d’un Passage, jamais au seul retrait. File vide au démarrage ou après le dernier retrait : elle reste ouverte. Cette tranche n’ajoute aucun minuteur.

Recherche et gestion exigent une Soirée `EN_COURS` et sa Manche Karaoké active `EN_COURS` ou `EN_PAUSE`. Après consultation du reçu, une mutation sur une Manche connue `TERMINEE` donne `ETAT_INCOMPATIBLE` avant toute garde du Passage ; les autres cas inactifs donnent `ETAPE_INACTIVE`. Sans courant, le pilotage omet `etapeId` ; `PASSER_ETAPE` donne alors `ETAPE_INACTIVE`, comme le Quiz sans `etapeId`. La Manche passe de `EN_COURS` à `EN_PAUSE` et inversement, puis à `TERMINEE`. Après fin, `karaoke:null`, même si `mancheActive` reste terminée : cette asymétrie avec le Quiz final est assumée.

### Recherche et inscription

`chansonId` est une chaîne opaque stable attribuée par le serveur, sans identifiant ni préfixe de fournisseur public. Inconnu ou disparu : `404 RESSOURCE_INTROUVABLE`. Le détail de la Chanson est figé à l’inscription. Aucune source audio ni présence d’un fichier local sur l’Écran de salle n’est exigée pour s’inscrire ; seuls comptent les paroles synchronisées et une durée connue.

La saisie brute de recherche est bornée par le schéma à 1..120 points de code. Le serveur applique NFC, regroupe les espaces Unicode, supprime ceux des extrémités, puis applique la limite configurée entre 1 et 120, qui ne peut que réduire la limite. Une recherche vide, sans lettre ou chiffre Unicode ou trop longue donne `REQUETE_INVALIDE`, sans troncature. `rechercheId` est un UUID client renvoyé tel quel, sans reçu ; la Manette ignore une réponse tardive qui ne correspond plus à sa recherche. Aucun résultat donne `200` avec `chansons:[]` ; une panne reste distincte.

Le classement normalise par NFKD, retire les diacritiques, passe en minuscules sans locale, remplace œ par oe et æ par ae, puis remplace les caractères autres que lettres/chiffres Unicode par des espaces regroupées, sans bordures. Rang 0 : égalité avec le titre ou l’artiste normalisé ; rang 1 : chaque mot de recherche préfixe un mot du titre et de l’artiste joints ; rang 2 : sinon. Trier par rang, titre normalisé, artiste normalisé, puis `chansonId`, par points de code. Dédupliquer par `chansonId` ; garder les interprétations distinctes et les Chansons non inscriptibles ; exclure seulement les entrées malformées. Cache, pagination, historique et correction de faute sont différés.

L’inscription suit accès, reçu et état, consulte la source hors verrou, puis revalide sous verrou. Ordre des gardes : instrumentale (`SANS_VOIX`), paroles non synchronisées (absentes et inconnues incluses), durée inconnue, doublon personnel en attente, puis quota `EN_ATTENTE`. La fiche Joueur expose le premier motif dans cet ordre, ou `null` si l’inscription est possible. Pour l’Animateur, elle donne toujours `false/ANIMATEUR`. La fiche est indicative : le serveur revalide à l’inscription. Un contenu inutilisable donne `422 CHANSON_INDISPONIBLE`, avec un `detail` motivé. Un 404 externe de fiche ou d’inscription donne `RESSOURCE_INTROUVABLE` ; les autres échecs externes (délai, taille, protocole, HTTP) donnent `503 SOURCE_PAROLES_INDISPONIBLE`.

### Commandes, compteurs et reçus

L’identité et la portée proviennent du Bearer Joueur ou de la session Animateur, jamais du corps ; hors Soirée/Lieu autorisé : 404. L’Écran de salle ne lit que la capture salle de ces parcours. Les commandes n’acceptent ni acteur, ni pseudo, URL, paroles, date client ou Révision. Les mutations Animateur exigent session, `X-CSRF-TOKEN` et `Idempotency-Key` UUID ; les mutations Joueur exigent Bearer et `envoiId` JSON. Aucun header `Location` n’est ajouté. Succès en `application/json`, refus `Probleme` en `application/problem+json`, tous avec `Cache-Control:no-store`.

Pour les nouvelles mutations, la clé est acteur(type/id) + route concrète + `envoiId` ; l’empreinte est méthode + JSON canonique. Après validation du schéma et de l’accès, consulter le reçu avant le métier : même contenu, statut et corps initiaux, même après retrait ou fin ; contenu différent, `ENVOI_REUTILISE`. Succès et refus définitifs sont conservés jusqu’à expiration de l’accès, au minimum jusqu’à la fin de la Soirée ; expiration inconnue : conserver. **Accès refusé, 503 et 409 `ETAPE_INACTIVE` sont sans reçu**, car cette dernière est transitoire : la Manche peut redevenir active. Réessayer avec la même clé et le même contenu. Un reçu retrouvé reste distinct de la vue courante.

`numeroModification` est un compteur persistant par Passage, initialement 0. Il augmente sur état, Tour, placement explicite ou priorité ; jamais sur position calculée, estimation, pseudo ou inscription étrangère. La garde est propre à chaque objet. Cible/ancre connue sortie de l’attente ou compteur différent : `COMMANDE_PERIMEE` ; inconnue ou hors portée : 404. Une cible retirée, désinscrite ou appelée donne `COMMANDE_PERIMEE` quel que soit son compteur. Le geste déjà satisfait (accepté sans incrément ni événement) concerne seulement la priorité ou le placement d’une cible encore en attente.

Pour le placement, `avant:null` signifie fin du Tour ; ancre égale à la cible : `DONNEES_INVALIDES` ; changement de Tour ou cible/ancre prioritaire : `ROTATION_PROTEGEE` ; ancre sortie du Tour depuis l’affichage : `COMMANDE_PERIMEE`. La limite se règle par valeur, sans compteur attendu ; concurrence dans l’ordre serveur, dernière valeur retenue, valeur courante sans effet. `KaraokeCommun` expose la limite courante ; `Manche.reglages` conserve la limite initiale résolue immuable.

Une transaction unique enregistre la mutation. Après validation de cette transaction et toujours sous le verrou de Soirée, le serveur publie les vues complètes. Le déroulement rend l’accusé, les destinations et le `typeEvenement` commun ; chaque destination possède sa Révision. Refus, rejeu, geste sans effet et rollback ne publient rien. Base ou transaction indisponible : `503 SERVICE_INDISPONIBLE`. Toute autre exception produit un 500 hors contrat, journalisé, sans `ContratApi`.

### Routes et codes de refus

`M` désigne `/api/soirees/{soireeId}/manches/{mancheId}`. UUID de chemin requis. A = Animateur du Lieu, J = Joueur présent de la Soirée (inscripteur pour la désinscription). Les codes de chaque groupe ci-dessous font partie de chaque ligne qui le cite ; OpenAPI conserve les réponses partagées `Erreur4xx` et `Erreur503`.

| Groupe | Statut, code et condition |
|---|---|
| N | 400 `REQUETE_INVALIDE` (saisie/schéma), 401 `AUTHENTIFICATION_REQUISE` (identité), 403 `ACCES_REFUSE` (rôle), 404 `RESSOURCE_INTROUVABLE` (inconnu/hors portée, attente d’autrui, Chanson disparue) |
| V | 409 `ETAPE_INACTIVE` : hors portée active, sauf Manche terminée pour une mutation |
| X | 409 `ETAT_INCOMPATIBLE` : Manche connue terminée, après reçu et avant garde du Passage |
| D | 409 `ENVOI_REUTILISE` : même clé avec empreinte différente |
| T | 503 `SERVICE_INDISPONIBLE` : base ou transaction |
| S | 503 `SOURCE_PAROLES_INDISPONIBLE` : délai, taille, protocole ou HTTP externe en échec, sauf 404 de fiche/inscription |
| P | 409 `COMMANDE_PERIMEE` : compteur différent, cible/ancre sortie de l’attente, ancre sortie du Tour affiché |
| C | 403 `CSRF_INVALIDE` : contrôle CSRF invalide en session mutante |

| Méthode et route | operationId | Acteur et entrée | Succès | Refus |
|---|---|---|---|---|
| POST `/api/soirees/{soireeId}/manches` | `ajouterManche` | A, `CreationManche` accepte KARAOKE | 201 `Manche` | Inchangés ; aucun Idempotency-Key ajouté |
| GET `M/chansons` | `rechercherChansons` | A/J, query `recherche` et `rechercheId` requis | 200 `ResultatsChansons` | N,V,T,S ; aucun 422 |
| GET `M/chansons/{chansonId}` | `consulterChanson` | A/J, `chansonId` opaque, sans corps | 200 `FicheChanson` | N,V,T,S ; aucun 422 |
| POST `M/passages` | `inscrirePassage` | J, `InscriptionPassage` | 201 `AccusePassage` | N,V,X,D,T,S ; 409 `PASSAGE_DEJA_INSCRIT`/`LIMITE_ATTEINTE` ; 422 `CHANSON_INDISPONIBLE` |
| POST `M/passages/{passageId}/desinscription` | `desinscrirePassage` | J, `DesinscriptionPassage` | 200 `AccusePassage` | N,V,X,D,T,P |
| POST `M/passages/{passageId}/retrait` | `retirerPassage` | A, `RetraitPassage` | 200 `AccusePassage` | N,V,X,D,T,P,C |
| PUT `M/passages/{passageId}/placement` | `deplacerPassage` | A, `PlacementPassage` | 200 `AccusePassage` | N,V,X,D,T,P,C ; 409 `ROTATION_PROTEGEE` ; 422 `DONNEES_INVALIDES` |
| PUT `M/file-attente/priorite` | `definirPrioritePassage` | A, `PrioritePassage` | 200 `AccuseFileAttente` | N,V,X,D,T,P,C |
| PUT `M/file-attente/limite` | `reglerLimitePassages` | A, `LimitePassages` | 200 `AccuseFileAttente` | N,V,X,D,T,C ; aucune garde P |

Les paramètres de chemin et de requête sont des schémas en ligne dans OpenAPI pour être compilés seuls par le vérificateur ; `RechercheChanson` sert aussi dans `ResultatsChansons`. Les autres opérations Soirée/Quiz gardent leur contrat du socle #78, exercé sans extension.

### Projections et interfaces

Les nombres de la tranche sont des entiers sûrs JavaScript, de 0 à 9007199254740991 (dès 1 pour limites, Tours, numéros, positions et durées de Chanson). Les dates sont UTC. Les objets sont fermés ; les extensions par `allOf` utilisent `unevaluatedProperties:false`, et `KaraokeCommun` reste ouvert pour les composer. Les tableaux vides sont permis, sauf les Interprètes (1 à 4). Les schémas ne calculent pas la rotation.

Les compteurs excluent le courant, les positions couvrent 1..N, et `nombreAutresPassages = nombreEnAttente - prochainsPassages.length`. `numero` est immuable, commence à 1, continue entre les Manches de la Soirée et n’est jamais réutilisé. La teinte se calcule dans l’interface par `numero mod 4` : orange (0), eau (1), bleu (2), rose (3) ; aucune teinte dans le JSON. Dans cette tranche, `courant` reste nul ; `CourantPourFile` réserve les états `APPELE/EN_COURS/EN_PAUSE/VOTE` aux #22/#23.

La salle montre le courant séparé et jusqu’à trois prochains Passages, plus le nombre des autres. La Console reçoit toute la File d’attente et peut gérer/rechercher/régler la limite même en pause ou file vide. La Manette reçoit les trois prochains et tous ses Passages en attente. `peutRechercher` reste vrai pour A/J autorisé, même en pause ou quota atteint ; `peutInscrire` est vrai pour un Joueur présent sous quota, avec motif nul, sinon faux avec `LIMITE_ATTEINTE`.

L’attente vaut `max(0, prochainAppelPrevuLe - maintenant)` (0 sans courant), plus les durées et 30 secondes pour chaque attente précédente. En pause : `SUSPENDUE/null`. Prévision du courant manquante : `INCONNUE/null`. `ESTIMEE` porte une durée non nulle ; les autres états portent null. Afficher « environ » ; aucun événement par seconde.

Les canaux existants salle, Console et Joueur portent respectivement `BlocKaraokeSalle`, `BlocKaraokeConsole`, `BlocKaraokeManette` dans la vue complète. Les causes ajoutées sont `file-attente-modifiee` (inscription, désinscription, retrait, placement, priorité, future extraction) et `limite-passages-modifiee` (changement utile de limite), sur les trois destinations, dont toutes les Manettes de la Soirée. Le cycle garde les causes existantes. Recherche sans publication ; ni paroles ni secrets dans les blocs ; aucun nouveau canal ou `SEND` métier. Révisions et reconnexion suivent l’ADR 0004.

La Manette propose recherche, fiche, inscription/retrait personnels et tickets ; vide, panne et envoi incertain restent distincts. Le succès apparaît après réponse (`.ticket`, `.envoi--en-cours|accepte|refuse`, `.coupure` dans DESIGN.md). La Console propose catalogue, file complète, courant séparé, retrait/placement/priorité/limite (`.rouleau`, boutons) et affiche le `detail`. La salle reste en lecture seule (`.ticket`, `.talon`) : prochain Interprète ou invitation si vide, coquille seule après fin. Les textes sont indicatifs et tutoient sur la Manette et la salle.

### Configuration par défaut

| Clé | Valeur |
|---|---|
| `croustyquizz.passage.limitePassagesEnAttente` | 1 |
| `croustyquizz.passage.delaiAppelSecondes` | 30 |
| `croustyquizz.karaoke.longueurRechercheMax` | 120 points de code, réduction seule |
| `croustyquizz.karaoke.delaiConsultationNavigateurMs` | 8000 |
| `croustyquizz.karaoke.delaiConfirmationNavigateurMs` | 10000 |

Les trois réglages navigateur sont exposés dans `configurationInterface`. Une consultation bloquée est bornée à 8 secondes ; une confirmation inconnue à 10 secondes ne signifie pas que le serveur a annulé l’envoi. Le réglage externe de cette configuration est différé.

## Vérifier les fichiers

La validation est indépendante du serveur et de PostgreSQL : Node.js 24 est nécessaire uniquement pour les outils de contrats. Elle n’ajoute ni compilation front ni bundler et ne modifie pas les commandes Maven de l’application.

```sh
npm ci --prefix tools/api --ignore-scripts --no-fund --no-audit
npm test --prefix tools/api
```

Le job `contrats-api` de GitHub Actions exécute les mêmes commandes. Les versions des validateurs et leurs dépendances sont verrouillées dans `tools/api/package-lock.json`. Les vérifications portent sur la structure OpenAPI/AsyncAPI, les références locales, la compilation de tous les schémas, les requêtes et réponses des deux parcours, les événements, les cas de refus de données sensibles et leur raccordement entre capture et tampon via le vrai `suivreVue`. Chaque YAML paramètre `destination`, `routeEtat` et `typeEvenementFinal`, avec ses captures, événements couverts et doublon final. Projections, chronologies et reçus sont isolés par parcours ; la couverture des `operationId` porte sur leur union. Les reçus 200/201 sont comparés par acteur, route concrète et envoiId, statut et corps compris. Les cas couvrent les trois blocs Karaoké, la confidentialité du Quiz, les motifs, limites de recherche et de configuration, commandes fermées, fin et réessais transitoires. Les exemples utilisent des UUID, secrets et adresses fictifs.

Ces contrôles **ne prouvent pas la conformité d’un serveur non implémenté**. Les futures tâches #5, #6, #7, #8, #10, #12 et #14 devront vérifier les mêmes échanges contre Spring Boot : isolement entre Lieux et Joueurs, abonnements non autorisés, CSRF, autorité temporelle, pause/reprise, perte de confirmation, répétition après clôture, modification refusée et événement survenant pendant la reconnexion. Les tests utiliseront PostgreSQL via Testcontainers, une Horloge factice, des commandes HTTP et des clients STOMP ; la charge de soixante Joueurs reste à mesurer sur ce parcours réel.

## Relecture avant implémentation

- **Piyakabib** : accès Animateur, préparation et règles du Quiz, résultats.
- **nRayen** : cycle de Soirée, programme, accès Joueur et Pilotage Manuel.
- **Tibxla** : contrat de Manche, destinations, autorisations STOMP, ordre et resynchronisation.

Pour #82, nRayen relit aussi `CreationManche`/`Manche`, et Piyakabib les énumérations d’événements et les vues partagées. Une approbation collective du contrat reste attendue. Le PRD reste la référence fonctionnelle. Le contrat borne une Manche de Quiz à 100 Questions et une étape à 3 600 secondes pour cette première version.

Références : [OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0), [AsyncAPI 3.1](https://www.asyncapi.com/docs/reference/specification/v3.1.0), [validation AsyncAPI](https://www.asyncapi.com/docs/guides/validate), [Swagger Parser](https://github.com/APIDevTools/swagger-parser).

## Socle technique distinct

Le [prototype #7](prototype/README.md) possède ses propres contrats, limités à la
démonstration en profil `dev`. Il utilise HTTP pour les commandes et STOMP pour
les événements. La tranche salle #47 y applique les Révisions et la capture synchronisée. Ses
accès anonymes ne constituent pas une implémentation des autorisations métier
décrites ci-dessus.

## Implémentation du socle #79

`ServiceDeManche<R, E>` porte le verrou, la transaction et les reçus de `manche_envoi`.
Les modes fournissent leur fabrique typée, leur persistance JPA et leur bloc de vue.
`restaurer` reçoit l'état commun persisté, notamment `EN_PAUSE`. Les minuteurs
repassent par ce service ; `typeEvenementMinuteur` précise la cause selon la phase du mode.
Une commande préparée hors verrou revalide sous verrou ; son refus peut être enregistré
sans appeler le déroulement. Un rollback ne publie rien et évince l'instance du registre.
Les captures de repli utilisent le même assembleur que les publications.

En `dev` et `recette`, le résolveur reconnaît `Authorization: Bearer demo:TYPE:soireeId:acteurId`,
avec `TYPE` dans `ANIMATEUR`, `JOUEUR`, `ECRAN`. #80 peut déposer la valeur
`demo:TYPE:soireeId:acteurId` dans `sessionStorage` sous `croustyquizz.jeton`,
puis la transmettre aussi dans CONNECT STOMP. Le Joueur fictif du fournisseur commun
porte un UUID stable propre à sa Soirée, fourni par `FournisseurCommunDemonstration.joueurId(soireeId)` :
`UUID.nameUUIDFromBytes` appliqué aux octets UTF-8 de `croustyquizz:demo:joueur:` suivi de `soireeId`.
Les projections et le jeton `demo:JOUEUR:soireeId:acteurId` utilisent tous cet UUID comme `acteurId`.
Ces identités fictives ne sont pas
une authentification ; en production le résolveur refuse systématiquement avec `401`.
Le port conserve l'accès à la session et aux en-têtes CSRF pour #5/#10 ; les jetons
Bearer de démonstration n'utilisent pas de session ni de cookie d'authentification.

Les deux routes temporaires de création et de démarrage sont décrites dans
[le contrat du prototype](prototype/openapi.yaml), réservées aux profils `dev`/`recette`
et à l'Animateur de démonstration. Elles nécessitent qu'un service du mode soit installé ;
aucun vrai mode n'est livré par #79. Le Buzz de test et son format historique restent disponibles.
Le cycle interne `PRETE` apparaît sous le nom HTTP existant `EN_ATTENTE` dans le programme.
Les schémas des Manches restent limités au Quiz jusqu'aux contrats des autres modes.
