# Contrats du Quiz, du Blind test et du Karaoké

Issues de référence : [#34](https://github.com/Tibxla/croustyquizz/issues/34), [#82](https://github.com/Tibxla/croustyquizz/issues/82), tranche de contrat de [#21](https://github.com/Tibxla/croustyquizz/issues/21), [#98](https://github.com/Tibxla/croustyquizz/issues/98), tranche de contrat de [#22](https://github.com/Tibxla/croustyquizz/issues/22), et [#174](https://github.com/Tibxla/croustyquizz/issues/174), tranche de contrat de [#17](https://github.com/Tibxla/croustyquizz/issues/17). Ces fichiers décrivent une **cible à relire par les propriétaires**, pas des contrôleurs déjà déployés. L’application Spring Boot et les pages HTML/JavaScript restent dans le même projet.

| Fichier | Contenu |
|---|---|
| [openapi.yaml](openapi.yaml) | HTTP JSON : accès, préparation, pilotage, Réponses, vues et résultats |
| [asyncapi.yaml](asyncapi.yaml) | Événements du serveur vers les trois destinations STOMP |
| [schemas.yaml](schemas.yaml) | Schémas JSON partagés pour éviter une divergence entre les vues HTTP et les messages |
| [exemples/parcours-quiz.yaml](exemples/parcours-quiz.yaml) | Échanges fictifs, événements, refus de données sensibles et exemple de reconnexion |
| [exemples/parcours-karaoke.yaml](exemples/parcours-karaoke.yaml) | Recherche, inscription, File d’attente, refus, reçus et reconnexion du Karaoké |
| [exemples/parcours-passage-karaoke.yaml](exemples/parcours-passage-karaoke.yaml) | Cycle du Passage : appel, « Je suis prêt », lecture de l’Écran de salle, calage, erreur, fin, refus, reçus et reconnexion de la salle |
| [exemples/parcours-pilotage-manuel.yaml](exemples/parcours-pilotage-manuel.yaml) | Lancement, enchaînement des Manches et fin de la Soirée sur les trois écrans |
| [exemples/parcours-vote-karaoke.yaml](exemples/parcours-vote-karaoke.yaml) | Vote du public, pause, refus, rejeu et reconnexion de la Manette |
| [exemples/parcours-pilotage-automatique.yaml](exemples/parcours-pilotage-automatique.yaml) | Capture et reconnexion de la Console d’une Soirée en Pilotage Automatique |
| [exemples/parcours-blindtest.yaml](exemples/parcours-blindtest.yaml) | Blind test : extrait, Buzz, brouillon et Proposition de titre, partie trouvée, blocage, Révélation, refus, confidentialité et reconnexion de la salle |

Les opérations HTTP sont identifiées par leur `operationId` : les exemples utilisent ces identifiants pour rester vérifiables lorsque les routes évoluent. OpenAPI 3.1 et AsyncAPI 3.1 sont retenus ; les schémas partagés sont compatibles avec JSON Schema 2020-12. Les références de schéma restent locales au dépôt.

## Périmètre

Le premier parcours est : inscription du premier Animateur d’un Lieu, connexion, création d’une Soirée **Solo / Manuel**, préparation d’une Manche de **Quiz**, ouverture aux Joueurs, lancement, trois Questions et Classement général final. Les Questions de ce parcours sont rédigées par l’Animateur ; la banque française et l’import relèvent des extensions du Quiz.

Le code d’accès est créé à l’ouverture, reste utilisable pour les arrivées tardives et n’est pas une preuve d’identité. La préparation et le programme restent modifiables en `PREPARATION` et `OUVERTE`, conformément à #9. Le lancement vérifie que le programme est non vide et que chaque Manche de Quiz dispose du nombre de Questions prévu, sinon `409 PROGRAMME_INCOMPLET`. Les Manches de Karaoké et de Freestyle n’ont pas de Questions : `preparerQuestions` sur une telle Manche répond `409 ETAT_INCOMPATIBLE`, sans modification. Chaque Question a quatre propositions distinctes et une seule solution.

Le [parcours de pilotage manuel](exemples/parcours-pilotage-manuel.yaml) illustre le refus d’un programme vide, la répétition d’un envoi, l’enchaînement de deux Manches dans l’ordre du programme et la fin de la Soirée. La fin publie `soiree-terminee` aux trois types d’écran. La vue finale ne porte aucune Manche active.

La File d’attente du Karaoké étend ce parcours ci-dessous. Une Soirée peut être créée en Format `EQUIPE` et en Pilotage `AUTOMATIQUE` (#6), qui sont enregistrés et restitués tels quels : le déroulement en Équipe relève de #26 et l’enchaînement automatique de #25. Le Blind test étend aussi ce parcours ci-dessous. Le Freestyle, la modération et le serveur MCP étendront ce contrat. Le Buzz sert au scénario du socle #7/#8, avant le lancement d’une Manche, et au Blind test ; il est refusé pendant le Quiz. Les contrats n’imposent aucune formule de points : les règles de dégressivité et de départage du PRD restent à implémenter dans `quiz` et `classement`.

## Accès aux vues

| Acteur | Identité HTTP | Abonnements STOMP autorisés | Vues HTTP |
|---|---|---|---|
| Animateur | Cookie de session `JSESSIONID`, Lieu déduit par le serveur | Salle et Console des Soirées de son Lieu | Salle, Console, Questions de préparation et résultats de son Lieu |
| Joueur | `Authorization: Bearer <jetonReconnexion>` | Salle de sa Soirée et son propre canal Joueur | Salle, sa Manette et résultats de sa Soirée |
| Écran de salle | `Authorization: Bearer <cleEcran>` | Salle de la Soirée associée à cette capacité | Salle et résultats de cette Soirée ; il n’envoie que les commandes de son lecteur, sans aucune action de jeu |

Les jetons sont opaques. Le serveur en déduit l’identité et la portée ; une route ou un corps contenant un UUID ne confère aucun droit. Un Joueur ne peut agir pour un autre, un Animateur ne peut accéder à un autre Lieu et une clé d’Écran n’envoie que les commandes de son lecteur (désignation, déclaration, fin, erreur et relevé de position), sans aucune action de jeu. Les ressources hors de la portée autorisée répondent `404`, les identités absentes ou expirées `401`.

Le jeton Joueur doit rester utilisable pour la reconnexion pendant la Soirée. La page d’arrivée conserve le jeton et l’UUID du Joueur renvoyés par le serveur pour la Soirée concernée. La Manette utilise le jeton pour ses requêtes HTTP et le CONNECT STOMP, et l’UUID pour choisir son canal. La clé d’Écran indique sa propre expiration. Une clé expirée nécessite un nouvel accès depuis la préparation. Les capacités ne sont transmises ni dans les événements, ni dans une chaîne de requête, ni dans les journaux. La préparation ouvre `ecran.html?soiree=<UUID>#cle=<clé>` ; l’Écran de salle conserve sa clé dans le stockage de l’onglet et retire le fragment après lecture. Tous les secrets et vues sont servis avec `Cache-Control: no-store` ; HTTPS est requis hors développement local.

Les opérations sans Soirée, comme la création, reçoivent l’identité de session `AnimateurConnecte` (`animateurId`, `lieuId`) côté serveur. La création enregistre ce Lieu. Pour une opération sur une Soirée, le serveur résout un Acteur Animateur pour cette Soirée après avoir vérifié son Lieu. Les anciennes Soirées sans Lieu donnent aussi `404 RESSOURCE_INTROUVABLE` aux Animateurs connectés.

Pour la session Animateur, obtenir un jeton par `GET /api/csrf`, puis envoyer `X-CSRF-TOKEN` avec les opérations mutantes. L’inscription et la connexion sont également protégées contre les requêtes intersites. Après connexion/déconnexion, récupérer un nouveau jeton. Le cookie de session est `HttpOnly`, `SameSite=Lax` et `Secure` en HTTPS.

Sur les routes de compte, un en-tête `Authorization: Bearer …` entraîne un refus sans contrôle CSRF : `403 ACCES_REFUSE` pour l’inscription et la connexion, `401 AUTHENTIFICATION_REQUISE` pour la consultation et la déconnexion. Pour les requêtes de la session navigateur, un jeton CSRF absent ou invalide entraîne `403 CSRF_INVALIDE`.

Un Animateur connecté peut créer une invitation à son Lieu par `creerInvitationAnimateur`, avec l’e-mail de l’invité et le CSRF de la session. Il transmet lui-même le lien `/invitation.html#cle=…`. La clé expire au bout de sept jours et le serveur conserve uniquement son empreinte. `consulterInvitationAnimateur` restitue le nom du Lieu, sa ville et l’e-mail de l’invité. `accepterInvitationAnimateur` reçoit la clé, le nom et le mot de passe avec un CSRF, accessible avant connexion. Il crée le compte au Lieu de l’invitation et ouvre la session. Tous les Animateurs du Lieu ont les mêmes droits.

Les deux opérations publiques reçoivent la clé dans leur corps JSON. La clé est exclue de l’URL des requêtes, des journaux et des événements. Les trois opérations répondent avec `Cache-Control: no-store` et refusent un en-tête Bearer en `403 ACCES_REFUSE`. Une clé expirée, inconnue ou déjà utilisée donne `404 INVITATION_INVALIDE`. L’e-mail est unique sans distinction de casse et un compte existant donne `409 EMAIL_DEJA_UTILISE` à la création ou à l’acceptation. La création du compte consomme l’invitation dans la même transaction.

Pour STOMP 1.2, le sous-protocole est `v12.stomp`. Le navigateur envoie le jeton CSRF dans `CONNECT`, ainsi que `Authorization: Bearer …` pour un Joueur ou un Écran de salle ; la Console utilise la session du handshake. À chaque `SUBSCRIBE` d’un Animateur connecté, le serveur vérifie le Lieu de la Soirée visée par le canal. Il s’agit de **headers STOMP de CONNECT**, pas d’un header HTTP arbitraire ajouté par le constructeur WebSocket du navigateur. Si un jeton Bearer est présent, l’identité est celle de ce jeton ; une session Animateur présente dans le même navigateur ne permet pas d’élargir ses droits. Le serveur vérifie l’origine, l’identité et chaque `SUBSCRIBE`, refuse les commandes métier `SEND`, et ferme les connexions dont l’accès a expiré ou été invalidé. L’utilisation de la clé d’Écran est limitée au canal et aux vues décrits dans le tableau, et aux commandes HTTP de son lecteur décrites dans « Passage : appel, lecture et calage ».

## Vues et événements

Les vues publiques ne contiennent aucune solution pendant `QUESTION_OUVERTE` ou `EN_PAUSE`. Les résultats personnels et les Points visibles sont publiés **après correction**. Les Questions privées de préparation ont leur propre schéma et une route réservée à l’Animateur ; elles ne sont jamais sérialisées directement sur un canal public.

Les événements portent `type`, `soireeId`, `revision`, `emisLe` et `donnees`. `donnees` est la **projection complète** de la vue concernée ; le navigateur la remplace atomiquement. Cela facilite la reprise après coupure et évite d’exiger la relecture de tous les deltas. Le `type` décrit la cause du changement. La version initiale `{type, donnees}` de la PR #30 devra donc être enrichie.

La salle suit `/topic/soirees/{soireeId}/salle`, la Console `/topic/soirees/{soireeId}/console` et la Manette `/topic/joueurs/{joueurId}`. La Manette utilise son canal personnel comme source complète de sa vue ; elle n’a pas besoin de combiner deux flux pour retrouver son état. La diffusion du serveur met à jour les projections concernées par chaque mutation, notamment tous les canaux Joueur lorsque le Quiz avance.

L’ordre est garanti **par destination et par Soirée**, avec une révision contiguë croissante pour chaque événement publié sur cette destination. Deux destinations ont des compteurs indépendants : une révision de salle n’est pas comparable à une révision de Manette. Les captures HTTP, mutations et attributions de révision doivent être cohérentes avec la sérialisation d’une même Soirée. Les messages ne constituent pas un journal durable ; un écran resynchronise sa vue après toute coupure, erreur d’application ou rupture dans la suite des révisions. Les heartbeats STOMP détectent les connexions inactives ; le rythme prévu dans le contrat est de dix secondes.

## Vues par blocs

Selon l’[ADR 0006](../adr/0006-commandes-de-manche-et-vues-par-blocs.md), les trois vues partagent une partie commune : Soirée, Manche active, Joueurs, Classement général et dernier Buzz ; la Console porte aussi le programme et la Manette ses informations personnelles. Le champ obligatoire `modeEnCours` vaut `QUIZ`, `BLIND_TEST`, `KARAOKE`, `FREESTYLE` ou `null` hors Manche.

La Manche en cours remplit le seul bloc de son Mode de jeu (`quiz`, `blindtest`, `karaoke` ou `freestyle`). Ce bloc peut rester `null` sans fournisseur ou après la fin. Les autres blocs valent `null` ou sont absents. Hors Manche, `modeEnCours` vaut `null` et tous les blocs sont nuls ou absents ; les anciens exemples `quiz: {phase: ATTENTE}` hors Manche deviennent donc `quiz: null`. Une vue porte au plus un bloc non nul, correspondant à `modeEnCours` : cette règle est validée par le schéma partagé `BlocsDeMode` sur les trois destinations. Le serveur compose une vue dont `modeEnCours` correspond à la Manche active.

`quiz` est facultatif, de type `EtatQuiz` ou `null`. `blindtest` est facultatif, de type `BlocBlindTestSalle`, `BlocBlindTestConsole` ou `BlocBlindTestManette` selon la destination, ou `null`. `karaoke` est facultatif, de type `BlocKaraokeSalle`, `BlocKaraokeConsole` ou `BlocKaraokeManette` selon la destination, ou `null`. Un bloc Karaoké non nul exige une Manche active de Karaoké en `EN_COURS` ou `EN_PAUSE`, et un bloc Blind test une Manche active de Blind test dans les mêmes états. Le bloc facultatif `freestyle` n’accepte encore que `null`. La création et la description des Manches acceptent `QUIZ`, `BLIND_TEST` et `KARAOKE`, avec les réglages propres à chaque mode (section Blind test pour `ReglagesBlindTest`). La confidentialité du Quiz reste inchangée : si le bloc `quiz` est présent et non nul, aucun résultat personnel n’est révélé en `ATTENTE`, `QUESTION_OUVERTE` ou `EN_PAUSE`. En `KARAOKE`, `derniereReponse` et `resultatPersonnel` restent obligatoires et nuls, même sans bloc fourni.

Dans la partie commune de la Manette, `eligiblePourMancheActive` dit si le Joueur prend part à la Manche active. Pour un Joueur arrivé pendant une Manche, le serveur le met à `true` quand le Mode de jeu admet les arrivées en cours (Quiz, décision de #14 ; Blind test, ADR 0009 ; Karaoké ; Freestyle), à `false` sinon. Au Blind test, le retardataire peut buzzer dès le Morceau en cours. Les pages n’en déduisent rien d’autre : le message « Tu participeras à la prochaine Manche » s’appuie sur ce seul champ, sans regarder `modeEnCours`. La règle de jeu reste au serveur, et un futur Mode de jeu n’a rien à ajouter côté navigateur.

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

La Manette retrouve ainsi ses points et sa Réponse déjà enregistrée. Les Questions manquées ne sont pas rejouées ; un Joueur arrivé au milieu d’une Manche de Quiz voit `eligiblePourMancheActive: true` et joue aussitôt, y compris la Question en cours s’il reste du temps. Au Karaoké, le serveur met ce champ à `true` pour le retardataire, qui s’inscrit dès son arrivée (voir « Vues par blocs »). Les actions périmées ne sont jamais rejouées automatiquement.

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

Une commande visant un objet porte sa cible et la version sur laquelle l’acteur a décidé : si la cible a changé et rend la commande sans objet, le serveur répond `409 COMMANDE_PERIMEE` sans mutation ([ADR 0006](../adr/0006-commandes-de-manche-et-vues-par-blocs.md)). La Révision d’un écran ne sert jamais de garde de commande. Le Quiz conserve sa garde `etapeId` : absent ou non courant, il entraîne `409 ETAPE_INACTIVE`. Ce champ est facultatif dans `CommandeManche`, mais seulement quand la Manche n’a pas d’étape courante : un Passage actif du Karaoké en a une, créée à l’appel, que le pilotage doit citer.

Une base ou une transaction indisponible produit `503 SERVICE_INDISPONIBLE` au format `Probleme`, décrit par la réponse réutilisable `Erreur503`. L’envoi peut être réessayé avec le même `envoiId`, ou la même clé `Idempotency-Key` pour les commandes de pilotage et d’ouverture, et le même contenu.

Un accusé HTTP retrouvé après répétition est un reçu de l’opération initiale : il ne doit pas remplacer une projection temps réel plus récente. Les écrans prennent leur état courant dans les captures cohérentes et événements révisés, même lorsqu’une ancienne réponse HTTP finit par arriver.

Le Buzz du socle est disponible dans une Soirée ouverte ou en cours hors Manche active. Son UUID peut être envoyé comme `envoiId` JSON ou comme `Idempotency-Key` ; les deux formes désignent le même envoi. Un Buzz accepté reçoit un rang croissant à partir de 1 dans la Soirée. Le reçu est conservé, et `buzz-recu` transporte ce Buzz dans `dernierBuzz` des vues complètes de la salle, de la Console et des Manettes. Les captures et événements ultérieurs conservent le dernier Buzz accepté.

La pause gèle le temps restant ; la reprise crée une nouvelle échéance serveur. En Manuel, `PASSER_ETAPE` clôt d’abord une Question ouverte et montre sa correction ; un nouvel appel, avec un nouvel identifiant d’envoi, passe à la Question suivante. Après la dernière correction, la Manche est terminée. L’Animateur demande ensuite `MANCHE_SUIVANTE`, qui clôt la Soirée si le programme est épuisé. L’arrêt explicite d’une Manche abandonne l’étape non corrigée sans lui attribuer de points.

## Blind test

Les règles sont celles de l’[ADR 0009](../adr/0009-deroulement-du-blind-test.md). Ce qui suit les décrit vues du réseau ; [exemples/parcours-blindtest.yaml](exemples/parcours-blindtest.yaml) les illustre.

**Préparation.** `ajouterManche` accepte `BLIND_TEST` avec `ReglagesBlindTest` : une source (`PLAYLIST` ou `GENRE` Deezer, par son identifiant) et un nombre de Morceaux de 1 à 50. Le serveur tire les Morceaux qui ont un extrait, hors du verrou de la Soirée. Ces réglages sont publics : la partie commune les publie tels quels à toutes les destinations (`mancheActive.reglages`). La sélection tirée reste donc dans l’état privé du mode et n’y figure jamais. Deezer indisponible, ou une source sans assez de Morceaux jouables : `503 SOURCE_MUSICALE_INDISPONIBLE`, sans Manche ajoutée.

**Phases d’un Morceau.** `PREPARATION` (le serveur demande le lien d’extrait hors du verrou), `EXTRAIT`, `PROPOSITION` (un Joueur a la main), `REVELATION`. `etapeId` identifie le Morceau courant ; il est attribué par le serveur et n’est jamais l’identifiant Deezer. `lecture` décrit les 30 s d’extrait menées par l’Horloge du serveur : en lecture depuis `positionMs` à l’instant `depuisLe`, ou en pause à `positionMs`. Les 30 s partent de la mise en lecture et s’arrêtent pendant la saisie et les pauses de la Manche. L’Écran de salle s’aligne sur `lecture` sans rien signaler ; un Écran rechargé reprend à la bonne position.

**Confidentialité.** La vue salle est lisible par un Joueur : elle ne porte jamais l’identifiant Deezer, ni le titre, ni l’artiste avant leur Révélation, hors partie déjà trouvée. Elle porte `lienExtrait`, le lien signé rendu par Deezer, en phases `EXTRAIT` et `PROPOSITION` seulement. `revelation` (titre, artiste, pochette) n’est non nul qu’en phase `REVELATION`, sur les trois destinations. La Console porte en plus `reponse`, la réponse attendue à tout moment, et `joueursBloques`. La Manette porte en plus `peutBuzzer`, `bloque`, `aLaMain` (jamais en même temps que `bloque`), `champsDemandes`, et `monBrouillon`, visible de son seul auteur quand il a la main.

**Buzz.** Pendant une Manche de Blind test, `envoyerBuzz` exige `mancheId` et l’`etapeId` du Morceau visé (`400 REQUETE_INVALIDE` sinon) et transmet le Buzz au déroulement de la Manche. Le premier Buzz accepté d’un Joueur non bloqué, en phase `EXTRAIT`, lui donne la main : l’extrait se met en pause, `main` porte son échéance de saisie (20 s), puis `buzz-recu`. `rang` est l’ordre du Buzz parmi les Buzz acceptés du Morceau, et `dernierBuzz` reprend le Buzz qui a pris la main. Une cible qui n’est plus la Manche ou le Morceau courant reçoit `409 COMMANDE_PERIMEE`, distinct des refus de jeu : Joueur bloqué, main déjà prise ou phase autre que `EXTRAIT` donnent `409 BUZZ_NON_AUTORISE`. Le même envoi rend son reçu initial, même après la fin du Morceau.

**Proposition de titre.** Le Joueur qui a la main dépose son brouillon par `deposerBrouillonProposition` au fil de la saisie : chaque envoi porte un `numero` croissant, à partir de 1 pour chaque main, et remplace le brouillon retenu (`204`, sans reçu). La vue est alors republiée à sa seule Manette (`brouillon-depose`) : après un rechargement, la capture rend `monBrouillon`, et la Manette reprend la numérotation depuis son `numero`. Un brouillon dont le `numero` ne dépasse pas celui du brouillon retenu est ignoré sans publication, si bien qu’un envoi rejoué en retard n’écrase jamais un plus récent ; c’est l’idempotence du brouillon, sans `Idempotency-Key`. `validerProposition` est dédupliquée par l’`envoiId` du corps, comme le Buzz. `validerProposition` fait juger aussitôt ; à l’échéance comptée par le serveur, le dernier brouillon reçu est jugé de la même façon. Seuls les champs encore demandés sont jugés ; une partie déjà trouvée vaut `NON_DEMANDE`. Un champ est juste s’il égale le titre court Deezer ou le nom de l’artiste, sans tenir compte de la casse, des accents ni des blancs répétés ; la tolérance aux articles et aux fautes de frappe viendra avec #18. Le titre rapporte 70 points, l’artiste 30, au premier qui trouve chaque partie, sans point négatif. Une partie trouvée apparaît aussitôt dans `partiesTrouvees` ; un champ demandé faux ou vide bloque le Joueur jusqu’au Morceau suivant, l’extrait reprend et le Buzz se rouvre. L’accusé porte le verdict de chaque champ et les points gagnés. Chaque jugement publie une seule vue : `proposition-jugee`, ou `morceau-revele` quand il termine le Morceau. Les refus d’un dépôt ou d’une validation suivent cet ordre : un Morceau qui n’est plus le Morceau affiché reçoit `409 COMMANDE_PERIMEE` (un Morceau révélé reste affiché jusqu’au suivant) ; un Joueur déjà jugé sur ce Morceau, ou dont l’échéance est atteinte même si le serveur ne l’a pas encore jugé, `409 HORS_DELAI` ; un Joueur sans la main `409 ETAT_INCOMPATIBLE`. Un Buzz reçu quand l’extrait a atteint 30 s reçoit `409 BUZZ_NON_AUTORISE`, même avant la Révélation.

**Fin et suite.** La Révélation arrive quand les deux parties sont trouvées, quand les 30 s sont écoulées ou quand plus aucun Joueur ne peut buzzer (`morceau-revele`). En Pilotage Automatique, le Morceau suivant est préparé 8 s après (`morceau-en-preparation`), puis lu (`morceau-ouvert`). En Manuel, un premier `PASSER_ETAPE` sur un Morceau en cours, en préparation compris, le termine et le révèle ; seul un `PASSER_ETAPE` pendant la Révélation passe au suivant, ou termine la Manche après le dernier Morceau. Il nomme toujours l’`etapeId` du Morceau affiché (`409 ETAPE_INACTIVE` sinon), si bien qu’un envoi destiné au Morceau 1 ne révèle jamais le Morceau 2.

**Pause.** `METTRE_EN_PAUSE` gèle la lecture et, pendant une saisie, le temps restant du Joueur qui a la main : `main.echeanceLe` devient `null` et `main.tempsRestantMs` porte le reste. `REPRENDRE` relance l’extrait et la saisie d’où ils étaient. Pendant la pause, le Joueur garde la main et peut encore déposer son brouillon ; valider sa Proposition ou `PASSER_ETAPE` reçoit `409 ETAT_INCOMPATIBLE`, et un Buzz `409 BUZZ_NON_AUTORISE`. Un Morceau dont le lien d’extrait ne peut être obtenu est sauté sans points (`morceau-saute`) ; après le dernier Morceau, la Manche se termine. Si le dernier Morceau est sauté, seul `manche-terminee` est publié : la salle n’annonce pas ce saut, le bloc `blindtest` passant aussitôt à `null`.

**Joueurs et points.** Un Joueur arrivé pendant la Manche peut buzzer aussitôt : `eligiblePourMancheActive` vaut `true`. Un Morceau donne au plus une `AttributionDePoints` par Joueur : `points` est la somme de ses parties trouvées, `bonneReponse` vaut `true`, et `tempsMs` est la position de l’extrait à son Buzz gagnant, donc le temps d’extrait écoulé hors pauses de la Manche et hors saisies des autres Joueurs. Un doublé titre et artiste donne une seule attribution de 100 points.

## Karaoké

Le contrat fixe la recherche de Chansons, l’inscription personnelle, la gestion de la File d’attente (#82) puis le cycle du Passage : appel, « Je suis prêt », lecture, calage et fin (#98, voir « Passage : appel, lecture et calage »). Le mécanisme commun (File d’attente, appel, « Je suis prêt », fin du Passage, Vote du public) vit dans `passage` ; la Chanson, ses paroles, la lecture et le calage vivent dans `karaoke` (ADR 0006). Un seul Interprète par Passage ici ; les tableaux acceptent déjà un à quatre Interprètes pour #66. Aucun Point n’est attribué au Karaoké, même en Format Équipe. Les sections suivantes décrivent aussi l’appel en Pilotage Automatique (#120) et le résultat du Vote du public (#116). Le port `LecteurDeChanson` de l’Écran de salle couvre charger, lire, pause, reprise, position, fin, volume et erreur (ADR 0007).

### Rotation et cycle

Le nouvel inscrit rejoint la fin du Tour ouvert s’il n’y a pas chanté, sinon le suivant. Son deuxième Passage vient après le premier de chacun des autres. L’Animateur place un Passage dans son Tour ; pour le faire passer juste après le courant, il pose une priorité unique, qui compte comme le Tour de l’Interprète. La priorité conserve l’ordre normal sous-jacent ; le retirer ou le remplacer restaure cet ordre. `true` remplace la priorité, `false` retire celle de la cible ou reste sans effet. Seuls les Passages affectés changent de compteur.

La limite, de 1 à 20, porte sur les Passages `EN_ATTENTE` de chaque Interprète. La baisser ne retire rien. Une désinscription suivie d’une réinscription rejoint la fin du Tour admissible. Pas de doublon de Chanson personnel en attente ; deux Joueurs peuvent choisir la même Chanson. Un retardataire présent peut s’inscrire ; sa vue porte `eligiblePourMancheActive: true`, mais ce champ n’est jamais une garde de l’inscription.

Les transitions sont absent → `EN_ATTENTE` → `RETIRE` (retrait ou désinscription), ou `ANNULE` à la fin de la Manche. L’appel fait `EN_ATTENTE` → `APPELE` et sépare le courant de la File d’attente ; la suite du cycle est décrite dans « Passage : appel, lecture et calage ». Un Passage sauté ou annulé garde son Tour. L’appel dure trente secondes ; son saut automatique relève du Pilotage Automatique (#25) : d’ici là, l’échéance ne publie rien et le Pilotage Manuel s’applique. Aucun nombre de Passages n’est fixé. L’Animateur termine la Manche ; en Automatique, la fin naturelle sera raccordée par #22 quand la File d’attente est vide à la fin d’un Passage, jamais au seul retrait. File vide au démarrage ou après le dernier retrait : elle reste ouverte. Deux minuteurs gouvernent la fin prévue du Passage et la clôture du Vote du public.

Recherche et gestion exigent une Soirée `EN_COURS` et sa Manche Karaoké active `EN_COURS` ou `EN_PAUSE`. Après consultation du reçu, une mutation sur une Manche connue `TERMINEE` donne `ETAT_INCOMPATIBLE` avant toute garde du Passage ; les autres cas inactifs donnent `ETAPE_INACTIVE`. Sans courant actif, le pilotage omet `etapeId` ; `PASSER_ETAPE` donne alors `ETAPE_INACTIVE`, comme le Quiz sans `etapeId`, même si un Passage `TERMINE` ou `SAUTE` reste affiché. Avec un courant actif, `etapeId` est celui de son étape. La Manche passe de `EN_COURS` à `EN_PAUSE` et inversement, puis à `TERMINEE`. Après fin, `karaoke:null`, même si `mancheActive` reste terminée : cette asymétrie avec le Quiz final est assumée.

### Recherche et inscription

`chansonId` est une chaîne opaque stable attribuée par le serveur, sans identifiant ni préfixe de fournisseur public, de 1 à 128 caractères ; en chemin comme dans `InscriptionPassage`, une valeur plus longue donne `400 REQUETE_INVALIDE`. Inconnu ou disparu : `404 RESSOURCE_INTROUVABLE`. Le détail de la Chanson est figé à l’inscription. Aucune source audio ni présence d’un fichier local sur l’Écran de salle n’est exigée pour s’inscrire ; seuls comptent les paroles synchronisées et une durée connue.

La saisie brute de recherche est bornée par le schéma à 1..120 points de code. Le serveur applique NFC, regroupe les espaces Unicode, supprime ceux des extrémités, puis applique la limite configurée entre 1 et 120, qui ne peut que réduire la limite. Une recherche vide, sans lettre ou chiffre Unicode ou trop longue donne `REQUETE_INVALIDE`, sans troncature. `rechercheId` est un UUID client renvoyé tel quel, sans reçu ; la Manette ignore une réponse tardive qui ne correspond plus à sa recherche. Aucun résultat donne `200` avec `chansons:[]` ; une panne reste distincte.

Le classement normalise par NFKD, retire les diacritiques, passe en minuscules sans locale, remplace œ par oe et æ par ae, puis remplace les caractères autres que lettres/chiffres Unicode par des espaces regroupées, sans bordures. Rang 0 : égalité avec le titre ou l’artiste normalisé ; rang 1 : chaque mot de recherche préfixe un mot du titre et de l’artiste joints ; rang 2 : sinon. Trier par rang, titre normalisé, artiste normalisé, puis `chansonId`, par points de code. Dédupliquer par `chansonId` ; garder les interprétations distinctes et les Chansons non inscriptibles ; exclure seulement les entrées malformées. Cache, pagination, historique et correction de faute sont différés.

L’inscription suit accès, reçu et état, consulte la source hors verrou, puis revalide sous verrou. Ordre des gardes : instrumentale (`SANS_VOIX`), paroles non synchronisées (absentes et inconnues incluses), durée inconnue, doublon personnel en attente, puis quota `EN_ATTENTE`. La fiche Joueur expose le premier motif dans cet ordre, ou `null` si l’inscription est possible. Pour l’Animateur, elle donne toujours `false/ANIMATEUR`. La fiche est indicative : le serveur revalide à l’inscription. Un contenu inutilisable donne `422 CHANSON_INDISPONIBLE`, avec un `detail` motivé. Un 404 externe de fiche ou d’inscription donne `RESSOURCE_INTROUVABLE` ; les autres échecs externes (délai, taille, protocole, HTTP) donnent `503 SOURCE_PAROLES_INDISPONIBLE`.

### Commandes, compteurs et reçus

L’identité et la portée proviennent du Bearer Joueur ou de la session Animateur, jamais du corps ; hors Soirée/Lieu autorisé : 404. Pour la File d’attente, l’Écran de salle ne lit que la capture salle ; ses seules commandes sont celles de son lecteur, sans action de jeu (« Passage : appel, lecture et calage »). Les commandes n’acceptent ni acteur, ni pseudo, URL, paroles, date client ou Révision. Les mutations Animateur exigent session, `X-CSRF-TOKEN` et `Idempotency-Key` UUID ; les mutations Joueur exigent Bearer et `envoiId` JSON. Aucun header `Location` n’est ajouté. Succès en `application/json`, refus `Probleme` en `application/problem+json`, tous avec `Cache-Control:no-store`.

Pour les nouvelles mutations, la clé est acteur(type/id) + route concrète + `envoiId` ; l’empreinte est méthode + JSON canonique. Après validation du schéma et de l’accès, consulter le reçu avant le métier : même contenu, statut et corps initiaux, même après retrait ou fin ; contenu différent, `ENVOI_REUTILISE`. Succès et refus définitifs sont conservés jusqu’à expiration de l’accès, au minimum jusqu’à la fin de la Soirée ; expiration inconnue : conserver. **Accès refusé, 503 et 409 `ETAPE_INACTIVE` sont sans reçu**, car cette dernière est transitoire : la Manche peut redevenir active. Réessayer avec la même clé et le même contenu. Un reçu retrouvé reste distinct de la vue courante.

`numeroModification` est un compteur persistant par Passage, initialement 0. Il augmente sur état, Tour, placement explicite ou priorité ; jamais sur position calculée, estimation, pseudo ou inscription étrangère. Pour le Passage courant, il change à l’appel, à la confirmation et à la clôture, jamais à la pause, à la reprise, à l’échéance, à la lecture ou au calage. La garde est propre à chaque objet. Cible/ancre connue sortie de l’attente ou compteur différent : `COMMANDE_PERIMEE` ; inconnue ou hors portée : 404. Une cible retirée, désinscrite ou appelée donne `COMMANDE_PERIMEE` quel que soit son compteur. Le geste déjà satisfait (accepté sans incrément ni événement) concerne seulement la priorité ou le placement d’une cible encore en attente.

Pour le placement, `avant:null` signifie fin du Tour ; ancre égale à la cible : `DONNEES_INVALIDES` ; changement de Tour ou cible/ancre prioritaire : `ROTATION_PROTEGEE` ; ancre sortie du Tour depuis l’affichage : `COMMANDE_PERIMEE`. La limite se règle par valeur, sans compteur attendu ; concurrence dans l’ordre serveur, dernière valeur retenue, valeur courante sans effet. `KaraokeCommun` expose la limite courante ; `Manche.reglages` conserve la limite initiale résolue immuable.

Une transaction unique enregistre la mutation. Après validation de cette transaction et toujours sous le verrou de Soirée, le serveur publie les vues complètes. Le déroulement rend l’accusé, les destinations et le `typeEvenement` commun ; chaque destination possède sa Révision. Refus, rejeu, geste sans effet et rollback ne publient rien. Base ou transaction indisponible : `503 SERVICE_INDISPONIBLE`. Toute autre exception produit un 500 hors contrat, journalisé, sans `ContratApi`.

### Routes et codes de refus

`M` désigne `/api/soirees/{soireeId}/manches/{mancheId}`. UUID de chemin requis. A = Animateur du Lieu, J = Joueur présent de la Soirée (inscripteur pour la désinscription). Les codes de chaque groupe ci-dessous font partie de chaque ligne qui le cite ; OpenAPI conserve les réponses partagées `Erreur4xx` et `Erreur503`. Chaque opération OpenAPI nomme ses propres codes et renvoie à ce tableau pour leurs conditions.

| Groupe | Statut, code et condition |
|---|---|
| N | 400 `REQUETE_INVALIDE` (saisie/schéma, dont `chansonId` de plus de 128 caractères et limite hors 1..20), 401 `AUTHENTIFICATION_REQUISE` (identité), 403 `ACCES_REFUSE` (rôle), 404 `RESSOURCE_INTROUVABLE` (inconnu/hors portée, attente d’autrui, Chanson disparue) |
| V | 409 `ETAPE_INACTIVE` : hors portée active, sauf Manche terminée pour une mutation |
| X | 409 `ETAT_INCOMPATIBLE` : Manche connue terminée, après reçu et avant garde du Passage |
| D | 409 `ENVOI_REUTILISE` : même clé avec empreinte différente |
| T | 503 `SERVICE_INDISPONIBLE` : base ou transaction |
| S | 503 `SOURCE_PAROLES_INDISPONIBLE` : délai, taille, protocole ou HTTP externe en échec, sauf 404 de fiche/inscription |
| P | 409 `COMMANDE_PERIMEE` : compteur différent, cible/ancre sortie de l’attente, ancre sortie du Tour affiché |
| C | 403 `CSRF_INVALIDE` : contrôle CSRF invalide en session mutante |

| Méthode et route | operationId | Acteur et entrée | Succès | Refus |
|---|---|---|---|---|
| POST `/api/soirees/{soireeId}/manches` | `ajouterManche` | A, `CreationManche` accepte KARAOKE | 201 `Manche` | Inchangés ; limite hors 1..20 : 400 `REQUETE_INVALIDE` ; aucun Idempotency-Key ajouté |
| GET `M/chansons` | `rechercherChansons` | A/J, query `recherche` et `rechercheId` requis | 200 `ResultatsChansons` | N,V,T,S ; aucun 422 |
| GET `M/chansons/{chansonId}` | `consulterChanson` | A/J, `chansonId` opaque de 1 à 128 caractères, sans corps | 200 `FicheChanson` | N,V,T,S ; aucun 422 |
| POST `M/passages` | `inscrirePassage` | J, `InscriptionPassage` | 201 `AccusePassage` | N,V,X,D,T,S ; 409 `PASSAGE_DEJA_INSCRIT`/`LIMITE_ATTEINTE` ; 422 `CHANSON_INDISPONIBLE` |
| POST `M/passages/{passageId}/desinscription` | `desinscrirePassage` | J, `DesinscriptionPassage` | 200 `AccusePassage` | N,V,X,D,T,P |
| POST `M/passages/{passageId}/retrait` | `retirerPassage` | A, `RetraitPassage` | 200 `AccusePassage` | N,V,X,D,T,P,C |
| PUT `M/passages/{passageId}/placement` | `deplacerPassage` | A, `PlacementPassage` | 200 `AccusePassage` | N,V,X,D,T,P,C ; 409 `ROTATION_PROTEGEE` ; 422 `DONNEES_INVALIDES` |
| PUT `M/file-attente/priorite` | `definirPrioritePassage` | A, `PrioritePassage` | 200 `AccuseFileAttente` | N,V,X,D,T,P,C |
| PUT `M/file-attente/limite` | `reglerLimitePassages` | A, `LimitePassages` (1 à 20) | 200 `AccuseFileAttente` | N,V,X,D,T,C ; aucune garde P |

Les paramètres de chemin et de requête sont des schémas en ligne dans OpenAPI pour être compilés seuls par le vérificateur ; `RechercheChanson` sert aussi dans `ResultatsChansons`. Les autres opérations Soirée/Quiz gardent leur contrat du socle #78, exercé sans extension, sauf `preparerQuestions` : sur une Manche de Karaoké, `409 ETAT_INCOMPATIBLE`.

### Projections et interfaces

Les nombres de la tranche sont des entiers sûrs JavaScript, de 0 à 9007199254740991 (dès 1 pour Tours, numéros, positions et durées de Chanson). La limite de Passages en attente va de 1 à 20, à la création comme au réglage. Les dates sont UTC. Les objets sont fermés ; les extensions par `allOf` utilisent `unevaluatedProperties:false`, et `KaraokeCommun` reste ouvert pour les composer. Les tableaux vides sont permis, sauf les Interprètes (1 à 4). Les schémas ne calculent pas la rotation.

Les compteurs excluent le courant, les positions couvrent 1..N, et `nombreAutresPassages = nombreEnAttente - prochainsPassages.length`. `numero` est immuable, commence à 1, continue entre les Manches de la Soirée et n’est jamais réutilisé. La teinte se calcule dans l’interface par `numero mod 4` : orange (0), eau (1), bleu (2), rose (3) ; aucune teinte dans le JSON. `courant` est le courant affiché : le courant actif (`APPELE`, `EN_COURS` ou `EN_PAUSE`), sinon le dernier Passage `TERMINE` ou `SAUTE` jusqu’au prochain appel, sinon `null`. L’état `VOTE` est retiré ; le Vote du public (#23) le décrira.

La salle montre le courant séparé et jusqu’à trois prochains Passages, plus le nombre des autres. La Console reçoit toute la File d’attente et peut gérer/rechercher/régler la limite même en pause ou file vide. La Manette reçoit les trois prochains et tous ses Passages en attente. `peutRechercher` reste vrai pour A/J autorisé, même en pause ou quota atteint ; `peutInscrire` est vrai pour un Joueur présent sous quota, avec motif nul, sinon faux avec `LIMITE_ATTEINTE`.

L’attente vaut `max(0, prochainAppelPrevuLe - maintenant)` (0 sans courant actif), plus les durées et 30 secondes pour chaque attente précédente ; `maintenant` est l’instant de la mutation projetée. Pendant l’appel, `prochainAppelPrevuLe` vaut l’échéance plus la durée d’inscription du courant ; pendant le Passage, sa fin prévue. En pause : `SUSPENDUE/null`. Prévision du courant actif manquante (délai d’appel épuisé) : `INCONNUE/null`. `ESTIMEE` porte une durée non nulle ; les autres états portent null. Afficher « environ » ; aucun événement par seconde.

Les canaux existants salle, Console et Joueur portent respectivement `BlocKaraokeSalle`, `BlocKaraokeConsole`, `BlocKaraokeManette` dans la vue complète. Les causes de la File d’attente sont `file-attente-modifiee` (inscription, désinscription, retrait, placement, priorité) et `limite-passages-modifiee` (changement utile de limite), sur les trois destinations, dont toutes les Manettes de la Soirée ; l’appel, qui retire le Passage de la File, publie `passage-appele`. Les causes du Passage sont décrites dans « Passage : appel, lecture et calage » ; pause, reprise et fin de Manche gardent les causes existantes. Recherche sans publication ; les paroles ne vont qu’à la salle et le fichier local qu’à la Console ; aucun secret dans les blocs ; aucun nouveau canal ou `SEND` métier. Révisions et reconnexion suivent l’ADR 0004.

La Manette propose recherche, fiche, inscription/retrait personnels et tickets ; vide, panne et envoi incertain restent distincts. Le succès apparaît après réponse (`.ticket`, `.envoi--en-cours|accepte|refuse`, `.coupure` dans DESIGN.md). La Console propose catalogue, file complète, courant séparé, retrait/placement/priorité/limite (`.rouleau`, boutons) et affiche le `detail`. La salle n’envoie aucune action de jeu (`.ticket`, `.talon`) : prochain Interprète ou invitation si vide, coquille seule après fin ; seul son lecteur envoie ses commandes, dont l’interface relève de la tranche 4 de #22. Les textes sont indicatifs et tutoient sur la Manette et la salle.

### Passage : appel, lecture et calage

Tranche de contrat de #22 (#98), selon l’[ADR 0007](../adr/0007-source-audio-du-karaoke.md) : la musique vient d’un fichier local choisi sur l’ordinateur de l’Écran de salle et trouvé par son nom ; sans fichier ou avec plusieurs, le Passage se joue en paroles seules. Le serveur ne reçoit aucun son, seulement le nom, la taille et la durée du fichier. La Manette confirme, la Console pilote et cale, l’Écran de salle lit. En Pilotage Manuel, la Console appelle le Passage suivant. En Pilotage Automatique, le serveur l’appelle. L’appel dure trente secondes. Un Passage sauté ou annulé garde son Tour. L’Animateur n’a ni volume, ni démarrage forcé, ni déplacement dans le son ; une capture ne relance ni musique ni animation.

L’identité vient du résolveur d’acteur (`jetonJoueur`, `cleEcran`, `sessionAnimateur` avec `X-CSRF-TOKEN`), jamais d’un corps. Le Joueur met son `envoiId` dans le corps ; l’Animateur et l’Écran envoient l’en-tête `Idempotency-Key`. Les reçus de « Commandes, compteurs et reçus » s’appliquent : un refus métier, « Manche en pause » et `409 LECTEUR_NON_DESIGNE` compris, est conservé comme reçu.

**Courant.** Le courant actif est un courant non nul `APPELE`, `EN_COURS` ou `EN_PAUSE`. Le courant affiché, valeur de `courant`, est le courant actif, sinon le dernier Passage `TERMINE` ou `SAUTE` jusqu’au prochain appel, sinon `null`. Le serveur crée l’`etapeId` à l’appel ; `piloterManche` exige l’`etapeId` du courant actif s’il existe, sinon l’omet. `calculeLe` est l’instant où le serveur a projeté la vue lors de la mutation : une capture de même Révision rend la même vue, sans événement par seconde.

| Départ | Déclencheur | Arrivée et cause |
|---|---|---|
| `EN_ATTENTE`, position 1 | `appelerPassage` en Manuel ou appel automatique | `APPELE`, `passage-appele` |
| `APPELE` | `confirmerPresencePassage` | `EN_COURS`, `passage-demarre` |
| `APPELE`, `EN_COURS`, `EN_PAUSE` | `PASSER_ETAPE` | `SAUTE`, `passage-saute` |
| `EN_COURS`, `EN_PAUSE` (fin locale seulement) | fin prévue ou fin locale | `TERMINE`, `passage-termine` |
| `APPELE`, `EN_COURS`, `EN_PAUSE` | `TERMINER` | `ANNULE`, `manche-terminee` |
| Lecture `PREPAREE` | confirmation | `DEMARREE` |
| Lecture `DEMARREE`, fichier local | erreur | `EN_ERREUR`, Manche `EN_PAUSE` |
| Lecture `EN_ERREUR` | reprise en paroles seules | `DEMARREE`, `PAROLES_SEULES`, Manche `EN_COURS` |

Après une fin locale ou `PASSER_ETAPE` en pause, la Manche reste en pause. `TERMINER`, de `piloterManche` comme de `piloterSoiree`, annule le courant actif (`ANNULE`, Tour conservé, lecture arrêtée) et le bloc devient `null`. `numeroModification` du Passage change à l’appel, à la confirmation et à la clôture seulement.

**Appel.** L’appel vise le Passage en position 1 ; un seul événement `passage-appele` publie l’appel et la File d’attente recalculée. La pause fige le délai d’appel restant (`echeanceAppelLe` nulle, `delaiAppelRestantMs` figé), la reprise le restitue avec une nouvelle échéance. La confirmation reste acceptée après l’échéance, qui ne publie rien.

**Pilotage Automatique.** En Manche `EN_COURS` et sans courant actif, le serveur prend le premier Passage de l’ordre effectif de la File d’attente, priorité comprise, et crée son étape uniquement s’il l’appelle. Il vérifie ces conditions après chaque commande acceptée et après chaque minuteur de Passage ou de Vote du public. La première inscription, une fin de lecture ou une fin prévue, `PASSER_ETAPE`, une clôture de vote ou la reprise peuvent ainsi déclencher l’appel. Une mutation qui appelle le suivant publie uniquement `passage-appele` à la salle, à la Console et à toutes les Manettes. Le courant devient `APPELE` dans cette même vue et remplace, le cas échéant, le Passage précédent clos. Le statut et l’accusé HTTP gardent le résultat de la commande initiale (par exemple `201`, `EN_ATTENTE` et compteur 0 pour l’inscription). Un refus conserve la File et le courant.

Le démarrage laisse la File vide. En pause, une fin de lecture ou `PASSER_ETAPE` peut clore le courant, puis la reprise appelle le premier Passage en attente. Après une erreur de lecture, la reprise en paroles seules poursuit le courant. Si la File est vide à la clôture, une inscription pendant le vote appelle aussitôt son Passage. La restauration réarme les minuteurs de fin prévue et de vote sans appeler de Passage. Les estimations d’attente supposent déjà l’enchaînement des Passages.

**Lecteur et lecture.** Le serveur désigne un seul lecteur par Manche : la dernière fenêtre qui a choisi son dossier, identifiée par un `lecteurId` engendré par la page. Elle garde son dossier et se redésigne à la Manche Karaoké suivante ; `PUT /lecteur` exige une Manche Karaoké active. Pendant l’appel, le lecteur déclare la lecture préparée ; une déclaration répétée par le même lecteur remplace la précédente, avec un nouveau `lectureId`. Une désignation pendant l’appel efface la lecture préparée par un autre lecteur, jamais celle d’un Passage démarré, dont les signaux restent réservés à son lecteur. « Je suis prêt » démarre aussitôt la chronologie (`demarreLe`) : une lecture préparée est figée, sinon le Passage démarre en paroles seules, motif `ECRAN_NON_PRET`, `lecteurId` nul. Une déclaration arrivée après la confirmation est périmée et ne lance jamais le fichier. `lecture` est non nulle de la première déclaration acceptée, ou de la confirmation, jusqu’à la clôture, sauf retrait par une désignation.

**Paroles et fin.** `origineParoles` vaut `VERSION_ADAPTEE` pour la version de paroles dont la durée est la plus proche du fichier, `REPLI_INSCRIPTION` en fichier local quand ce choix échoue (durée du fichier inconnue, version disparue ou source indisponible), et `INSCRIPTION` seulement quand le Passage part directement en paroles seules, avec les paroles figées à l’inscription. La durée de référence est fixée au départ : en fichier local, `fichier.dureeMs` si elle est positive ; sinon, et en paroles seules, `ParolesDuPassage.dureeMs`. `chanson.dureeMs` reste la durée d’inscription. La fin prévue vaut `demarreLe + durée de référence + tempsPauseMs` ; la vraie fin du fichier peut la devancer. Quand fin locale et fin prévue se croisent, une seule clôture a lieu et le signal perdant reçoit `COMMANDE_PERIMEE`.

**Erreur.** Après « Je suis prêt », un départ refusé ou une erreur du fichier met la Manche en pause et la lecture `EN_ERREUR`. `REPRENDRE` est alors refusé ; l’Animateur choisit `reprendreEnParolesSeules`, qui garde `lectureId`, `lecteurId`, paroles, `origineParoles` et décalage et pose le motif `ERREUR_LECTURE`, sans fichier ni demande de position, ou `PASSER_ETAPE`. Seules les opérations dont les refus citent « Manche en pause » la refusent.

**Calage.** La ligne active est la dernière ligne `i` telle que `position >= lignes[i].positionMs + decalageMs`, où `position` est la position du lecteur local ou `positionChronologiqueMs`. Avec une première ligne non blanche à 12 000 ms et « ça commence » mesuré à 13 500 ms, `decalageMs` vaut +1 500 : les paroles sont retardées de 1,5 seconde. `RETARDER` ajoute 100 ms, `AVANCER` en retire 100. « ça commence » remplace le décalage précédent : en fichier local, le serveur publie une demande identifiée dans la vue salle, le lecteur relève sa position à réception et la renvoie, puis le serveur calcule et publie ; en paroles seules, le serveur prend `positionChronologiqueMs` à réception. Une nouvelle demande remplace celle en attente ; la clôture l’efface ; une demande sans réponse reste en attente jusqu’à un nouveau geste. Une correction ne supprime pas une demande en attente : le relevé qui arrive ensuite recalcule le décalage depuis la première ligne et remplace la correction. `numeroModificationCalage` vaut 0 à la création de la lecture et ne change que si `decalageMs` change. En fichier local, le geste et les corrections sont mémorisés pour toutes les Soirées sous une clé interne (`chansonId` d’inscription, version de paroles retenue, nom et taille du fichier), absente de toute vue ; le décalage mémorisé devient le `decalageMs` initial de la lecture suivante de même clé. En paroles seules, rien n’est mémorisé.

**Capacités.** Dans la Console, `lecteurDesigne` est vrai exactement quand `lecteurDesigneId` de la salle est non nul ; `peutAppeler` exactement en Pilotage Manuel, si la Manche est `EN_COURS`, sans courant actif et avec au moins un Passage en attente ; `peutPasser` avec un courant actif, pause comprise ; `peutReprendre` si la Manche est `EN_PAUSE` et la lecture n’est pas `EN_ERREUR` ; `peutCaler` si le Passage est `EN_COURS`, la Manche `EN_COURS` et la lecture `DEMARREE` ; `peutReprendreEnParolesSeules` si la lecture est `EN_ERREUR`. Dans la Manette, `peutConfirmerPresence` exige un courant `APPELE` dont le Joueur est Interprète, Manche `EN_COURS`. La salle porte `lecteurDesigneId`, `lecture` (non nulle dès qu’une lecture est déclarée, appel compris) et `paroles` (non nulles seulement pour un courant actif `EN_COURS` ou `EN_PAUSE`) ; la Console porte `lecture` et `fichierLocal`, seul endroit où figure le nom du fichier ; la Manette ne porte ni lecture ni paroles, pour la légèreté de sa vue et non pour le secret, puisque la vue salle est lisible par un Joueur.

`P` désigne `M/passages/{passageId}`. Toutes ces routes exigent la Manche Karaoké active en Soirée `EN_COURS` et partagent : 400 `REQUETE_INVALIDE`, 401 `AUTHENTIFICATION_REQUISE`, 403 `ACCES_REFUSE`, 403 `CSRF_INVALIDE` (Animateur), 404 `RESSOURCE_INTROUVABLE` (Manche ou Passage inconnus, hors portée), 409 `ENVOI_REUTILISE`, 409 `ETAPE_INACTIVE` (sans reçu), 409 `ETAT_INCOMPATIBLE` (Manche terminée), 503 `SERVICE_INDISPONIBLE` (sans reçu). L’ordre est : accès, état de la Manche (pause comprise), puis gardes d’objet. Pour `appelerPassage`, les refus pour Manche ou Passage inconnus précèdent l’état de la Manche, et le refus du Pilotage Automatique vient après l’état de la Manche (pause comprise), avant les gardes d’objet. Un Passage clos, une étape, un compteur, un `lectureId` ou un `demandeId` qui ne sont plus les actuels donnent `409 COMMANDE_PERIMEE`, jamais `404`. Un succès rend `200 application/json` et `Cache-Control: no-store`, sans `Location`.

| Méthode et route | operationId | Acteur et entrée | Succès | Codes propres, dans l’ordre |
|---|---|---|---|---|
| POST `P/appel` | `appelerPassage` | A, `AppelPassage` | `AccuseCyclePassage` | `ETAT_INCOMPATIBLE` : Manche en pause, puis Pilotage Automatique (« En Pilotage Automatique, le Passage suivant est appelé seul. »), puis courant actif ; `COMMANDE_PERIMEE` : compteur, Passage sorti de l’attente ou plus en position 1 |
| POST `P/presence` | `confirmerPresencePassage` | J Interprète, `ConfirmationPresencePassage` | `AccuseCyclePassage` | 404 : Joueur non Interprète, avant tout reçu ; `ETAT_INCOMPATIBLE` : Manche en pause ; `COMMANDE_PERIMEE` : compteur, étape ou état `APPELE` |
| PUT `M/lecteur` | `designerLecteurChanson` | Écran, `DesignationLecteur` | `AccuseLectureChanson` | Aucun ; le lecteur déjà désigné réussit sans effet |
| POST `P/lecture` | `declarerLectureChanson` | Écran lecteur, `DeclarationLectureChanson` | `AccuseLectureChanson`, nouveau `lectureId` | `COMMANDE_PERIMEE` : Passage non `APPELE`, compteur ou étape ; `LECTEUR_NON_DESIGNE` : autre `lecteurId` que le désigné ; jamais `SOURCE_PAROLES_INDISPONIBLE` |
| POST `P/fin-lecture` | `signalerFinLectureChanson` | Écran lecteur de la lecture, `SignalFinLectureChanson` | `AccuseLectureChanson` | `COMMANDE_PERIMEE`, puis `LECTEUR_NON_DESIGNE` (autre `lecteurId` que celui de la lecture), puis `ETAT_INCOMPATIBLE` : lecture `PREPAREE`, en paroles seules ou `EN_ERREUR` |
| POST `P/erreur-lecture` | `signalerErreurLectureChanson` | Écran lecteur de la lecture, `SignalErreurLectureChanson` | `AccuseLectureChanson` ; sans effet si déjà `EN_ERREUR` | Comme la fin, sans `EN_ERREUR` |
| POST `P/paroles-seules` | `reprendreEnParolesSeules` | A, `RepriseParolesSeules` | `AccuseCyclePassage` | `COMMANDE_PERIMEE`, puis `ETAT_INCOMPATIBLE` : lecture non `EN_ERREUR` |
| POST `P/calage/debut` | `marquerDebutParoles` | A, `MarqueDebutParoles` | `AccuseCalage` | `ETAT_INCOMPATIBLE` : Manche en pause ; `COMMANDE_PERIMEE` ; `ETAT_INCOMPATIBLE` : Passage non `EN_COURS` ou lecture non `DEMARREE` |
| POST `P/calage/correction` | `corrigerCalage` | A, `CorrectionCalage` | `AccuseCalage` | Comme le geste, `numeroModificationCalage` compris dans `COMMANDE_PERIMEE` |
| POST `P/calage/position` | `releverPositionLecture` | Écran lecteur de la lecture, `RelevePositionLecture` | `AccuseLectureChanson` | Comme la fin |
| POST `M/commandes` existant | `piloterManche` | A, `CommandeManche` | `AccuseCommande` | `ETAPE_INACTIVE` si `etapeId` est absent ou différent avec un courant actif, ou présent sans courant actif, et pour `PASSER_ETAPE` sans courant actif, `etapeId` omis ou non, même si le courant affiché est `TERMINE` ou `SAUTE` ; `ETAT_INCOMPATIBLE` pour `REPRENDRE` pendant une lecture `EN_ERREUR` |

| Cause | Mutation | Destinations |
|---|---|---|
| `passage-appele` | appel manuel ou mutation déclenchant un appel automatique | salle, Console, toutes les Manettes |
| `passage-demarre`, `passage-termine`, `passage-saute` | cycle du Passage hors enchaînement vers un nouvel appel | salle, Console, toutes les Manettes |
| `lecture-chanson-modifiee` | désignation, déclaration | salle, Console |
| `lecture-chanson-modifiee` | erreur, reprise en paroles seules | salle, Console, toutes les Manettes |
| `calage-chanson-modifie` | demande, relevé, correction, geste | salle, Console |

Destinations et captures existantes restent inchangées ; vues complètes après validation, sans delta ni événement par seconde ; refus, rejeu et succès sans effet ne publient rien.

### Vote du public

Un Passage chanté jusqu’au bout (état `TERMINE`) ouvre un vote indépendant de trente secondes.
Un saut ou une annulation n’en ouvre aucun. Une pause fige chaque vote ouvert, y compris un vote
ouvert par une fin de lecture reçue pendant la pause. La reprise restitue le délai restant.
L’horloge clôt chaque vote à son échéance. Terminer la Manche clôt tous les votes avec les notes reçues
et conserve la cause `manche-terminee`.

Le Joueur présent envoie `VotePassage` sur `POST M/passages/{passageId}/vote`, avec son jeton,
un `envoiId`, le compteur du Passage et une note entière de 1 à 5. Le serveur applique les gardes
de rôle, de portée et de présence avant de traiter la commande. Après les gardes communes de Manche
et de rejeu, il refuse dans l’ordre un Passage inconnu, la pause, son propre Passage, un compteur
périmé, un vote absent ou clos (échéance incluse) et un second vote du même Joueur. Ces refus
métier sont conservés comme le reçu `200 AccuseVote` (`Cache-Control: no-store`). Un rejeu identique
rend le reçu initial sans publication. Un autre contenu avec le même identifiant donne
`ENVOI_REUTILISE`. Aucun vote ne rapporte de Point.

`passage-termine` ouvre le vote, et `vote-clos` signale l’échéance à la salle, la Console et toutes
les Manettes. `vote-enregistre` est publié vers la salle, la Console et la Manette du votant.
En Pilotage Automatique, si la clôture appelle un Passage, la vue complète arrive avec
`passage-appele`. Les trois blocs Karaoké portent une liste `votes` avec les votes ouverts et le
vote clos le plus récemment, triés par date d’ouverture décroissante.

Pendant le vote, la salle et la Console reçoivent le nombre de votes. Sur la Manette,
`nombreVotes` reste nul, avec la note personnelle et la capacité de voter disponibles.
Sur les trois écrans, `moyenneDixiemes` reste nul jusqu’à la clôture. À la clôture,
`nombreVotes` est renseigné sur les trois écrans et, si des votes ont été reçus, la moyenne
est arrondie au dixième le plus proche, une moitié vers le haut. En division entière,
`moyenneDixiemes = (20 × sommeNotes + nombreVotes) / (2 × nombreVotes)`. Sans vote, la moyenne
reste nulle et les écrans affichent « Aucun vote ».

La salle affiche jusqu’à deux bandeaux au-dessus du rouleau, dans l’ordre de la liste. Un vote
ouvert reste visible. Pour un vote clos encore dans la liste, le bandeau reste visible jusqu’au
plus tardif de deux instants, le démarrage d’un Passage suivant et la fin de
`configurationInterface.dureeAffichageMoyenneMs` après `clotureLe`. Ce délai court aussi pendant
la pause, d’après l’heure du serveur. Le champ persistant `suivantDemarre`, disponible dans
`VoteDuPassage` (salle et Console), devient vrai au démarrage d’un Passage suivant et le reste
après sa fin ou son saut. Un Passage simplement appelé ne le change pas.
`configurationInterface.dureeVoteMs` expose le réglage `croustyquizz.passage.dureeVoteSecondes`.

### Configuration par défaut

| Clé | Valeur |
|---|---|
| `croustyquizz.passage.limitePassagesEnAttente` | 1 (de 1 à 20) |
| `croustyquizz.passage.delaiAppelSecondes` | 30 |
| `croustyquizz.passage.dureeVoteSecondes` | 30 |
| `croustyquizz.passage.dureeAffichageMoyenneSecondes` | 10 |
| `croustyquizz.karaoke.longueurRechercheMax` | 120 points de code, réduction seule |
| `croustyquizz.karaoke.delaiConsultationNavigateurMs` | 8000 |
| `croustyquizz.karaoke.delaiConfirmationNavigateurMs` | 10000 |

Les trois réglages navigateur et la durée de l’appel (`delaiAppelMs`, tirée de `croustyquizz.passage.delaiAppelSecondes`) sont exposés dans `configurationInterface`. Une consultation bloquée est bornée à 8 secondes ; une confirmation inconnue à 10 secondes ne signifie pas que le serveur a annulé l’envoi. Le réglage externe de cette configuration est différé.

## Vérifier les fichiers

La validation est indépendante du serveur et de PostgreSQL : Node.js 24 est nécessaire uniquement pour les outils de contrats. Elle n’ajoute ni compilation front ni bundler et ne modifie pas les commandes Maven de l’application.

```sh
npm ci --prefix tools/api --ignore-scripts --no-fund --no-audit
npm test --prefix tools/api
```

Le job `contrats-api` de GitHub Actions exécute les mêmes commandes. Les versions des validateurs et leurs dépendances sont verrouillées dans `tools/api/package-lock.json`. Les vérifications portent sur la structure OpenAPI/AsyncAPI, les références locales, la compilation de tous les schémas, les requêtes et réponses des trois parcours, les événements, les cas de refus de données sensibles et leur raccordement entre capture et tampon via le vrai `suivreVue`. Chaque YAML paramètre `destination`, `routeEtat` et `typeEvenementFinal`, avec ses captures, événements couverts et doublon final. Projections, chronologies et reçus sont isolés par parcours ; la couverture des `operationId` porte sur leur union. Les reçus 200/201 sont comparés par acteur, route concrète et envoiId, statut et corps compris. Les cas couvrent les trois blocs Karaoké, la confidentialité du Quiz, les motifs, limites de recherche et de configuration, bornes de la limite de Passages et de `chansonId`, commandes fermées, fin et réessais transitoires, ainsi que le cycle du Passage : états du courant, lecture, paroles et fichier par destination, calage et rejeu. Le vérificateur interdit `LectureDeChanson`, `ParolesDuPassage` et `FichierAudioLocal` dans les vues et événements de la Manette, `FichierAudioLocal` dans ceux de la salle et `ParolesDuPassage` dans ceux de la Console ; il contrôle dans toutes les vues des exemples que l’échéance d’appel est nulle exactement en pause et qu’un Passage `EN_PAUSE` n’apparaît que dans une Manche en pause. Les exemples utilisent des UUID, secrets et adresses fictifs.

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
et la transmettre dans CONNECT STOMP. Le fournisseur commun projette quatre Joueurs de
démonstration, chacun avec un UUID stable propre à sa Soirée, fourni par
`FournisseurCommunDemonstration.joueurId(soireeId, rang)`. Le calcul applique
`UUID.nameUUIDFromBytes` aux octets UTF-8 de `croustyquizz:demo:joueur:<soireeId>`
pour le rang 1, et de `croustyquizz:demo:joueur:<soireeId>:<rang>` pour les rangs 2 à 4.
`joueurId(soireeId)` renvoie toujours le rang 1. Sur la page d’identité de démonstration,
le Joueur choisit son rang. La page lit l’UUID correspondant dans la projection et
l’utilise comme `acteurId` dans le jeton `demo:JOUEUR:soireeId:acteurId`.

En production, le résolveur refuse les jetons de démonstration avec `401`. En `dev` et `recette`,
`demo:JOUEUR` et `demo:ECRAN` sont réservés aux Soirées fictives. Sur une Soirée enregistrée, ces jetons
reçoivent `401 AUTHENTIFICATION_REQUISE` en HTTP et une trame `ERROR` suivie de la fermeture de la
connexion au CONNECT STOMP. Le canal de la Manette et sa dernière vue dépendent de l’UUID du Joueur.
Un jeton `demo:JOUEUR` qui porte l’UUID d’un vrai Joueur reçoit le même refus, quelle que soit la Soirée
déclarée. Pour une Soirée enregistrée, la Manette utilise son jeton de reconnexion et l’Écran de salle sa clé.
L’Animateur de démonstration vérifie son jeton par la capture de la Console. Un Bearer `demo:ANIMATEUR`
donne `401 AUTHENTIFICATION_REQUISE` sur une Soirée enregistrée, même dans un navigateur connecté :
l’Animateur y utilise sa session. Sur une Soirée absente de la base, les parcours de démonstration
continuent d’utiliser ce Bearer sans CSRF.
Le [prototype #7](prototype/README.md) peut s’abonner anonymement au canal de salle uniquement en `dev`
et `recette`, pour un UUID de Soirée absent de la base (#181). Le serveur vérifie cette absence à chaque
`SUBSCRIBE` anonyme. Si la Soirée est enregistrée, le broker n’installe aucun abonnement pour ce `SUBSCRIBE`.
La coquille STOMP `/ecran.html#soiree=<UUID>&cle=<cleEcran>` retire le fragment après lecture et conserve la
clé dans le stockage de l’onglet, pour cette seule Soirée.

Les jetons Bearer de démonstration n'utilisent pas de session ni de cookie d'authentification.

Les trois routes temporaires de création, de démarrage et de choix du Pilotage sont
réservées aux profils `dev`/`recette`. Une Soirée enregistrée exige une session
du même Lieu et un jeton CSRF ; une Soirée de démonstration utilise le Bearer.
La création et le démarrage d'une Manche nécessitent un service du mode installé.
Le choix du Pilotage d'une Soirée de démonstration se fait avant le démarrage de toute Manche.
Il est perdu au redémarrage du serveur. Si une Manche a démarré ou si la Soirée est
enregistrée, la route répond `409 ETAT_INCOMPATIBLE`. Le Pilotage d'une Soirée enregistrée
reste celui choisi à sa création. Le Buzz de test et son format historique restent disponibles.
Voir [le contrat du prototype](prototype/openapi.yaml).
Le cycle interne `PRETE` apparaît sous le nom HTTP existant `EN_ATTENTE` dans le programme.
Les schémas des Manches restent limités au Quiz jusqu'aux contrats des autres modes.

## Photos de la salle (#72)

Un Joueur envoie un JPEG brut par HTTP. Le serveur vérifie sa signature, sa taille
(1 500 000 octets maximum) et ses dimensions (2048 pixels maximum de chaque côté).
Un `Content-Length` supérieur à la borne est refusé (`422 DONNEES_INVALIDES`) sans
lire le corps. Le serveur lit au plus quatre envois à la fois : au-delà, il répond
`503 SERVICE_INDISPONIBLE` sans reçu. Avant de lire le corps, il vérifie sous le
verrou de Soirée l'état de la Soirée, le réglage, l'exclusion du Joueur, les files
et le budget, puis les revérifie au moment de l'ajout.
Il conserve les JPEG en mémoire uniquement, dans la limite de 64 000 000 octets
pour l'ensemble des Soirées (`503 SERVICE_INDISPONIBLE`) et de 16 000 000 octets par
Soirée, photos en attente, acceptées et à l'affiche comprises (`409 LIMITE_ATTEINTE`).
La file de modération est bornée à trois photos par Joueur et à trente par Soirée,
et la file des photos acceptées à trente (`409 LIMITE_ATTEINTE` à la modération,
la photo reste en attente). Un refus ou la fin de l'affichage rend sa place
au JPEG suivant. La fin de Soirée efface toutes ses photos, le réglage et ses
reçus. Une Soirée sans commande photo ni changement d'affiche pendant douze heures
perd ses photos et ses reçus ; son réglage reste. Un redémarrage perd ces données
et remet les envois ouverts par défaut.

Les mutations exigent un `Idempotency-Key` UUID. Pour une Soirée ouverte ou en
cours, une commande réussie, rejouée par le même Acteur avec la même clé et le
même contenu, rend le même reçu. Si la publication a échoué, le rejeu peut
achever la première publication. Réutiliser la clé pour une
autre commande ou un autre contenu donne `409 ENVOI_REUTILISE`. Un refus
ne crée pas de reçu. Sur une Soirée enregistrée, les opérations de l'Animateur
utilisent sa session et son jeton CSRF, pour une Soirée de son Lieu (`404` sinon) ;
l'identité de démonstration reste réservée aux Soirées absentes de la base, en dev et recette.

Le bloc `photos` contient le réglage sur la Manette, et sur la Console le
réglage et la file d'attente avec le pseudo des auteurs. La salle ne reçoit
que l'UUID et l'échéance de la photo à l'affiche, ou `null`. Si une Manche
démarre avant la fin des huit secondes, la photo retourne en tête de file
et aura huit secondes consécutives au prochain créneau. Une Manche en pause
occupe toujours la scène. Couper les envois laisse les deux files intactes.
Un Écran de salle ne peut télécharger que le JPEG à l'affiche. Aucun Joueur
n'a accès au téléchargement. Les octets ne figurent dans aucune vue.

Voir [le parcours complet](exemples/parcours-photos.yaml).
