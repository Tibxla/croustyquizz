<!--
Mise à jour au gel, juste avant le tag v0.2.0 (issue #213) :
- commit mesuré : git log -1 --format=%h origin/develop ;
- tests et couverture : ./mvnw -q -o verify, puis target/surefire-reports et target/site/jacoco/jacoco.csv ;
- tests Node et contrats : npm test --prefix tools/api (ligne « schémas, opérations, échanges ») ;
- issues, PR et relectures : gh issue list / gh pr list --state all, et les relectures par l'API GraphQL ;
- état des features : relire « En relecture au moment du tag » et déplacer ce qui a été fusionné.
-->

# CroustyQuizz

**Rendu 3 · Version intermédiaire v0.2.0 · M1 MIAGE, Université Paris Nanterre**\
Année 2026-2027 · Révision du 25 novembre 2026

<div class="encadre">

**Version livrée :** tag `v0.2.0`, release <https://github.com/Tibxla/croustyquizz/releases/tag/v0.2.0>\
**Commit figé :** le commit tagué sur `main` est celui qu’indique la page de la release.\
**Image Docker :** `ghcr.io/tibxla/croustyquizz:0.2.0`, publiée par la livraison du tag\
**Dépôt unique, front, API et back :** <https://github.com/Tibxla/croustyquizz>\
**Recette de `develop` :** <https://croustyquizz-recette.onrender.com> · **Production :** <https://croustyquizz-prod.onrender.com>\
**Dossiers précédents :** `doc/rendu-1/` (10 novembre 2026) et `doc/rendu-2/` (v0.1.0, 18 novembre 2026)

</div>

Ce dossier fait évoluer celui du Rendu 2. Il en garde l’ossature et dit, section par section, ce qui a changé depuis la `v0.1.0`. Une section s’ajoute : la **conteneurisation**, que le Rendu 2 ne décrivait qu’en passant.

Sauf mention contraire, les chiffres sont mesurés le 25 novembre 2026 sur `develop`, au commit `3061e8f`. Ils seront relevés une dernière fois au gel, juste avant le tag. Comme au Rendu 2, une PR qui n’est pas fusionnée au moment du tag reste « en relecture », et son code n’est pas dans la version.

## Équipe, comptes développeurs et fonctionnalités majeures

L’équipe n’a pas changé : **trois étudiants**, chacun responsable d’une fonctionnalité métier majeure.

| Étudiant | Compte GitHub personnel | Fonctionnalité majeure | Feature de la v0.2.0 |
|---|---|---|---|
| Thibaud Thomas-Lamotte | [Tibxla](https://github.com/Tibxla) | **Karaoké** | Karaoké jouable de bout en bout : File d’attente, Passage, Vote du public |
| Harris Hamadache | [Piyakabib](https://github.com/Piyakabib) | **Quiz** | Première Manche de Quiz jouable (#14) |
| Rayen Ndiaye | [nRayen](https://github.com/nRayen) | **Blind test** | Manche de Blind test : contrat, domaine, catalogue Deezer, service et écrans (#17) |

La `v0.2.0` est le jalon des **Modes de jeu** annoncé au Rendu 1 : la feature de chacun est désormais son Mode de jeu, et plus une feature du socle. La section suivante dit ce qui en est réellement fusionné.

Deux évolutions depuis le Rendu 2 :

- **nRayen est passé de la Soirée au Blind test.** La Soirée (création, code, QR code, arrivée sans accroc) est fusionnée depuis le 18/11 et le 24/11 ; le Blind test, découpé en cinq tranches (#174 à #178), avance tranche par tranche.
- **Tibxla a repris plusieurs issues du socle**, réassignées à son nom : le compte du Lieu et la connexion (#5), le déroulement manuel des Manches (#12), la préparation du programme (#9) et le Classement général (#24). Le tableau des propriétaires de packages d’`AGENTS.md` n’a pas changé : les PR qui touchent le package d’un autre le disent et demandent sa relecture.

Chaque commit est fait depuis le compte de la personne qui a réalisé le travail. Un agent d’IA committe pour la personne qui le pilote, jamais au nom d’un autre membre.


<!-- page -->

# Feature majeure de chaque étudiant : état au 25/11

État relevé sur GitHub le 25 novembre en fin de matinée. Le tableau sera remis à jour au gel.

<div class="tableau-large">

| Étudiant | Feature | Issues | PR fusionnées depuis la v0.1.0 | PR en relecture | Ce qui reste |
|---|---|---|---|---|---|
| Tibxla | Karaoké | #21 et #22 fermées ; #23 (Vote et Palmarès) en tickets #115 à #118 | 35, dont l’interface de la File (#94), le Passage (#104, #127, #128, #129), le Vote et sa note moyenne (#154, #165), l’appel automatique (#158) | Palmarès et suite de la pile (#168, #172, #184, #189), suggestions et favoris (#199), Enregistrement (#204), Passages à plusieurs (#206) | Palmarès en fin de Soirée (#118), enchaînement automatique des Manches (#123) |
| nRayen | Blind test | #17, en tranches #174 à #178 | 8, dont la Soirée (#38, #113), les règles (#173), le contrat (#186), le domaine (#192) et le catalogue Deezer (#201) | service, routes et persistance (#210, approuvée), trois écrans (#212, en brouillon) | fusionner #210 et #212 ; correction tolérante (#18), Blind test sur mesure (#19), Format Équipe (#26) |
| Piyakabib | Quiz | #13 et #14 | aucune | ADR 0008 sur la source des Questions (#105), première Manche (#147, changements demandés le 24/11), variantes et thématiques (#156) | corriger et fusionner #147, puis #156 ; répartition des Réponses (#16) |

</div>

## Tibxla : Karaoké jouable de bout en bout

**Fusionné dans `develop`.** Le Karaoké se joue maintenant sur les trois écrans, sans passer par l’API à la main :

- la **File d’attente** (#21) : son interface sur la Manette, la Console et l’Écran de salle (#94), fusionnée juste après le tag `v0.1.0` ;
- le **Passage** (#22) : contrat de l’appel, de la lecture et du calage (#104), domaine (#127), service et persistance (#128), puis les trois écrans (#129). Le Joueur appelé confirme « Je suis prêt », l’Écran de salle lit le fichier choisi sur son ordinateur (ADR 0007) et affiche les paroles synchronisées de LRCLIB ;
- les **Passages à plusieurs Interprètes** côté File d’attente (#150), base des duos et des groupes ;
- le **Vote du public** (#154) : chaque Joueur du public note de 1 à 5 le Passage qui vient de finir, jamais le sien, et l’Écran de salle montre la note moyenne (#165) ;
- en **Pilotage Automatique**, l’appel du Passage suivant se fait seul (#158, #131).

**En relecture.** La pile du Karaoké attend la relecture de Piyakabib, dans l’ordre proposé sur #168 : le Palmarès de la Manche (#168), le saut d’un Passage sans « Je suis prêt » après 30 secondes (#172), la fin de la Manche sur une File vide (#184) et la hiérarchie des lignes du Palmarès (#189). Sur cette pile s’empilent les suggestions de Chansons et les favoris de l’Animateur (#199), l’Enregistrement d’un Passage sur la Manette (#204) et les Invitations des Passages à plusieurs (#206).

**Hors Karaoké, fusionné depuis la v0.1.0** : le programme de la Soirée depuis la Console (#167), le déroulement manuel d’une Soirée (#183, qui ferme #12), le Classement général (#190, #182), le Buzz de bout en bout dans une Soirée avec un test de charge à 60 Manettes (#194), la Musique d’attente de l’Écran de salle (#152, #155), les photos de la salle validées par l’Animateur (#203), la clé d’Écran et le QR code dans l’Écran de salle (#185), et plusieurs correctifs du socle et du temps réel (#134, #148, #149, #179, #188).

**En relecture, hors Karaoké** : le Freestyle, quatrième Mode de jeu du palier 3 (#197, #200, #207), le compte du Lieu et la session de l’Animateur (#166, #180, #196), le dictionnaire de rimes (#195), le podium final (#193), deux correctifs (#187, #198).

## nRayen : Blind test

**Fusionné dans `develop`.**

- la **Soirée** : création, code d’accès, QR code et arrivée d’un Joueur (#38, fusionnée le 18/11 juste après le tag), puis l’arrivée sans accroc (#113) : pseudos injurieux ou déjà pris refusés, reconnexion avec le jeton, arrivée en cours de Manche ;
- deux correctifs de sécurité et de robustesse de la Soirée (#151, #163) ;
- les **règles du Blind test** consignées dans l’ADR 0009 avant le découpage (#173) ;
- le **contrat** de la Manche (#186), relu par Tibxla avec trois corrections ;
- le **domaine** du déroulement en Java pur (#192) : Buzz, saisie, Révélation, Morceaux sautés ;
- le **catalogue Deezer** (#201) : recherche, tirage des Morceaux et lien d’extrait, sans appel réseau dans les tests.

**En relecture.** **#210** (tranche 4) joue une Manche de Blind test de bout en bout côté serveur : tirage hors verrou, préparation du lien de chaque Morceau, Buzz aiguillé vers la Manche, refus `503` quand Deezer est en panne. La relecture publiée par Tibxla le 25/11 demandait cinq points ; ils sont corrigés avec un test chacun, et la PR est approuvée. **#212** (tranche 5), en brouillon, ajoute les trois écrans.

**Relectures.** nRayen a approuvé 33 PR depuis la `v0.1.0`, et demandé des changements sur #147 et #179.

## Piyakabib : Quiz

**En relecture.** **#105** propose l’ADR 0008 : OpenQuizzDB comme source des Questions en français. **#147** livre la première Manche de Quiz jouable ; nRayen y a demandé des changements le 24/11. **#156** ajoute les variantes et les thématiques du Quiz. Aucune de ces PR n’est encore fusionnée au 25/11.

**Relectures.** Piyakabib est le relecteur des PR de Tibxla dans le tourniquet de `CONTRIBUTING.md`. Il a approuvé 10 PR depuis la `v0.1.0`, 36 en tout. Au 25/11, 17 PR de Tibxla attendent sa relecture.

**Reste** : faire fusionner #147 avant le gel pour que le Quiz soit dans la `v0.2.0`, puis la répartition des Réponses de la salle (#16) et le Quiz sur mesure (#15).


# Ce que contient la version v0.2.0

Le Rendu 1 annonçait `v0.2.0` comme le jalon des **Modes de jeu**. Au 25/11, le **Karaoké** se joue de bout en bout. Le **Blind test** a son contrat, son domaine et son catalogue dans `develop`, son service est approuvé et ses écrans sont en brouillon. Le **Quiz** est en relecture. Le Freestyle, quatrième Mode de jeu, est en relecture.

## Fusionné dans `develop` au 25/11

- **Soirée** : création, code d’accès, QR code, arrivée et reconnexion des Joueurs (nRayen) ; programme des Manches composé depuis la Console ; déroulement manuel d’une Soirée, Manche après Manche, depuis la Console.
- **Karaoké** : File d’attente, Passage avec paroles synchronisées et fichier audio local, Vote du public et note moyenne, appel automatique du Passage suivant.
- **Blind test** : contrat, domaine en Java pur, catalogue Deezer.
- **Socle commun** : Buzz de Soirée de bout en bout, Classement général (points, bonnes réponses et temps de départage portés par chaque Mode de jeu), Musique d’attente de l’Écran de salle, photos de la salle validées par l’Animateur, quatre Joueurs de démonstration par Soirée pour la recette.
- **Contrats réseau** (`docs/api/`) : le vérificateur contrôle 125 schémas, 54 opérations, 254 échanges HTTP, 241 événements et 215 cas de schéma, contre 77, 34, 123, 85 et 143 à la `v0.1.0`.
- **Base** : 16 migrations Flyway, contre 4 à la `v0.1.0`. Flyway applique désormais les migrations hors ordre dans tous les profils (#179) : avec des PR empilées, une migration plus ancienne peut arriver après une plus récente.
- **Décisions** : ADR 0009, déroulement du Blind test.

## En relecture au moment de la rédaction

| PR | Contenu | Auteur |
|---|---|---|
| #105, #147, #156 | Source des Questions (ADR 0008), première Manche de Quiz, variantes | Piyakabib |
| #210, #212 | Blind test : service et routes, puis trois écrans | nRayen |
| #168, #172, #184, #189 | Palmarès du Karaoké et fin de Manche | Tibxla |
| #199, #204, #206 | Suggestions et favoris, Enregistrement, Passages à plusieurs | Tibxla |
| #197, #200, #207 | Freestyle : Grille de rimes, trois écrans, Couplets à plusieurs | Tibxla |
| #166, #180, #196 | Compte du Lieu, session de l’Animateur, invitation d’Animateurs | Tibxla |
| #187, #193, #195, #198 | Correctifs, podium final, dictionnaire de rimes | Tibxla |

## Ce que la v0.2.0 ne contiendra pas

- Pas de Format Équipe (#26), pas de Signalement ni d’Exclusion (#31), pas d’Assistant externe (#28), pas de Contacts du Lieu ni de Campagnes SMS (#32, #33).
- Pas d’enchaînement automatique des Manches (#123) ni de Palmarès en fin de Soirée (#118).
- Pas de test en salle sur de vrais téléphones. Le test de charge à 60 Manettes est fait côté serveur (#194).


# Conteneurisation

L’application est conteneurisée depuis la `v0.1.0`. Le Rendu 2 n’en parlait qu’à travers la livraison ; cette section la décrit en entier. Depuis la `v0.1.0`, seule la configuration de Flyway a changé (#179) : l’image, les fichiers Compose et le script de démo sont restés les mêmes, et la `v0.2.0` sera la deuxième livraison à passer par eux.

<figure class="architecture">
<div><strong>CONSTRUCTION · eclipse-temurin:21-jdk</strong><br>Wrapper Maven, <code>./mvnw -B package -DskipTests</code> : les tests ont déjà tourné dans la CI</div>
<p class="arrow">↓ seul le JAR exécutable passe à l’étape suivante</p>
<div><strong>EXÉCUTION · eclipse-temurin:21-jre</strong><br>Utilisateur système sans droits (uid 10001), port 8080, <code>java -jar application.jar</code></div>
<p class="arrow">↓ publiée sur GHCR à chaque livraison, construite par Render à chaque fusion</p>
<div><strong>ENVIRONNEMENTS</strong><br>Recette et production sur Render avec Neon · démo sur un serveur avec Docker Compose · tests avec Testcontainers</div>
<figcaption>Une seule image pour tous les environnements ; seule la configuration change.</figcaption>
</figure>

## L’image

Le `Dockerfile` construit l’image en deux étapes. La première compile l’application avec le JDK 21 et le wrapper Maven ; la seconde ne garde que le JRE 21 et le JAR. L’image d’exécution ne contient donc ni Maven, ni le code source, ni les outils de compilation. Construite le 25/11 depuis `3061e8f`, sa couche applicative, le JAR, pèse 57 Mo ; le reste vient du JRE (166 Mo) et du système de base Ubuntu de l’image Temurin (192 Mo). Elle tourne sous un utilisateur système dédié, jamais sous `root`. Le fichier `.dockerignore` écarte du contexte de construction l’historique Git, la documentation, les dossiers des agents, les dépendances Node et tout fichier `.env`.

Le `Dockerfile` ne lance pas les tests : ils ont besoin de Docker pour Testcontainers, et la CI les a déjà passés sur le même commit avant toute publication.

## Trois façons de la lancer

| Fichier | Usage | Ce qu’il démarre |
|---|---|---|
| `compose.yaml` | développement, démarré seul par `./mvnw spring-boot:run` (support Docker Compose de Spring Boot) | PostgreSQL 18, sur un port choisi par Docker et ouvert seulement sur la machine |
| `compose.prod.yaml` | démo sur un serveur, par `tools/demo.sh demarrer` puis `arreter` | l’image publiée et PostgreSQL 18, avec un volume pour les données |
| Testcontainers | `./mvnw verify`, en local et dans la CI | un PostgreSQL jetable par exécution des tests |

`compose.prod.yaml` durcit le conteneur de l’application :

- système de fichiers en lecture seule, avec un `/tmp` en mémoire ;
- aucune capacité Linux et pas d’élévation de privilèges ;
- 1 Go de mémoire, 2 processeurs et 512 processus au plus ; la JVM se cale sur 75 % de la mémoire du conteneur ;
- relance après un plantage, trois fois au plus, mais jamais au démarrage de la machine : la démo ne tourne que pendant les séances ;
- journaux bornés à trois fichiers de 10 Mo.

PostgreSQL n’est joint que par un réseau interne sans accès sortant, et l’application ne démarre qu’une fois la base prête (`pg_isready`). Les secrets viennent d’un fichier `.env` ignoré par Git, sur le modèle de `.env.example`. L’adresse publique de démo, par Tailscale Funnel, reste refusée tant que les conditions de l’issue #4 ne sont pas remplies.

## Publication et déploiement

À chaque tag `vX.Y.Z` de `main`, `livraison.yml` vérifie le tag, relance `./mvnw -B verify`, puis publie l’image sur GHCR sous les étiquettes `X.Y.Z` et `latest` ; un push sur `main` publie l’étiquette `main`. Le paquet est public : `docker pull ghcr.io/tibxla/croustyquizz:0.2.0` fonctionne sans compte. La release GitHub joint le JAR vérifié.

Render construit l’image depuis le même `Dockerfile` :

- la **recette** après chaque fusion verte dans `develop`, par un *deploy hook* que seule la CI appelle ;
- la **production** à chaque livraison taguée.

Chacune a sa base PostgreSQL 18 chez Neon. Rien dans l’image ne dépend d’un hébergeur : `docs/deploiement.md`, « Changer d’hébergeur », liste ce qu’un autre service de conteneurs doit fournir. Une seule instance doit tourner, car les Révisions et la dernière Vue synchronisée sont en mémoire (ADR 0004).


# Projet et caractère innovant

Le problème et la cible n’ont pas changé. Un **Lieu** qui accueille du public doit pouvoir préparer et animer une Soirée sans disperser les réponses, la musique et les résultats entre plusieurs outils. L’Animateur pilote depuis la **Console d’animation**, les Joueurs utilisent leur téléphone comme **Manette**, et le public suit l’**Écran de salle**.

## Ce qui a changé depuis le Rendu 2

- **Le palier 3 a commencé.** Le Freestyle, quatrième Mode de jeu proposé par l’étude de l’existant, est en relecture : une Instrumentale lue depuis le dossier de l’Écran de salle (ADR 0010, proposé dans #197), une Grille de rimes calée sur le tempo, des Couplets à plusieurs et le vote du meilleur Interprète. L’Enregistrement d’un Passage sur la Manette (ADR 0005) est en relecture aussi.
- **La salle vit entre les Manches.** La Musique d’attente joue sur l’Écran de salle hors des sons de Manche, et les photos envoyées par les Joueurs passent à l’écran après validation par l’Animateur.
- **Le Buzz devient commun.** Un Buzz de Soirée sert à toute la Soirée ; le Blind test l’aiguille vers sa Manche, sans second mécanisme.

L’innovation d’usage reste la continuité de la Soirée : **un seul accès par QR code**, une Manette qui change d’elle-même à chaque Manche, un Pilotage Automatique avec reprise en main par l’Animateur. Aucun test en salle n’a encore eu lieu.

# Dépôt principal et suivi

**Dépôt unique :** <https://github.com/Tibxla/croustyquizz>\
**Spécification de référence :** [PRD, issue nº 2](https://github.com/Tibxla/croustyquizz/issues/2)\
**Version de ce rendu :** [doc/rendu-3](https://github.com/Tibxla/croustyquizz/tree/develop/doc/rendu-3), suivie par l’issue nº 213.

Le dossier est maintenu dans **`doc/rendu-3/dossier-v0.2.0.md`** et compilé en **`doc/rendu-3/dossier-v0.2.0.pdf`** par `python3 tools/generer-dossier.py rendu-3`. Les dossiers précédents restent dans `doc/rendu-1/` et `doc/rendu-2/`.

## Le suivi en chiffres, au 25/11

- **110 issues**, dont 63 fermées, contre 66 et 26 au 18/11.
- **101 PR**, dont 79 fusionnées et 22 ouvertes. 43 PR ont été fusionnées depuis la livraison `v0.1.0` : 35 de Tibxla et 8 de nRayen.
- **Relectures** : nRayen a approuvé 42 PR (33 depuis la `v0.1.0`), Piyakabib 36 (10), Tibxla 11 (9). Des changements ont été demandés sur 6 PR : par nRayen sur #30, #88, #94, #147 et #179, par Tibxla sur #38. Tibxla publie aussi des relectures détaillées en commentaire, puis approuve une fois les points corrigés, comme sur #186, #192 et #210.

## Ce qui a changé dans la façon de travailler

- **Des piles plus hautes.** La pile du Karaoké compte quatre PR, et six autres s’empilent dessus. Pour éviter les conflits en cascade, l’ordre de fusion est écrit sur la PR de base (#168), et chaque fusion est suivie d’une vérification des PR restantes.
- **Une relecture indépendante des grosses PR**, par un second agent qui ne voit que le code. Sur #199, #200, #203 et #204, elle a trouvé de 7 à 11 défauts chacune ; ceux qui se sont confirmés ont été corrigés avec un test dans la PR.
- **Une recette dans un vrai navigateur** après tout changement visible : Console, Manettes et Écran de salle ouverts en même temps, faux micro pour l’Enregistrement, fichiers audio pour le Freestyle.


# Scénarios d’usage : cible et état

Les parcours décrivent la **cible fonctionnelle** du Rendu 1. Chacun indique son état au 25/11.

## 1. Préparer et ouvrir une Soirée

**Acteur : l’Animateur.** Il crée une Soirée, ajoute et ordonne des Manches, ouvre les inscriptions puis affiche l’Écran de salle avec le QR code.\
**État :** fusionné. La création, le code et le QR code viennent de #38 (nRayen), le programme de #167, l’ouverture de l’Écran de salle avec sa clé de #185. La connexion de l’Animateur par un compte du Lieu est en relecture (#166, #180, #196) ; d’ici là, des identités de démonstration ouvrent la Console en développement et en recette.

## 2. Rejoindre, jouer au Quiz et se reconnecter

**Acteurs : l’Animateur et les Joueurs.** Le Joueur scanne le QR code, choisit un pseudo et accède à sa Manette. Il répond à chaque Question avant la fin du délai.\
**État :** l’arrivée, les pseudos et la reconnexion sont fusionnés (#38, #113). La Manche de Quiz est en relecture (#147).

## 3. Prendre la main au Blind test

**Acteurs : les Joueurs et l’Animateur.** Les Joueurs buzzent sur un extrait ; le serveur ordonne les Buzz et donne la main au premier.\
**État :** le Buzz de Soirée est fusionné et tenu à 60 Manettes (#194). Le contrat, le domaine et le catalogue Deezer du Blind test sont fusionnés ; le service est approuvé (#210) et les écrans sont en brouillon (#212).

## 4. Chanter et voter au Karaoké

**Acteurs : le Joueur appelé, le public et l’Animateur.** Un Joueur s’inscrit dans la File d’attente, chante quand il est appelé, puis le public vote de 1 à 5.\
**État :** fusionné de bout en bout sur les trois écrans. Le Palmarès de fin de Manche, le saut après 30 secondes, les duos et l’Enregistrement sont en relecture.

## 5. Rapper au Freestyle

**Acteurs : les Interprètes, le public et l’Animateur.** Un ou plusieurs Interprètes rappent sur une Instrumentale en suivant la Grille de rimes ; le public note le Passage, ou désigne le meilleur Interprète d’un Passage à plusieurs.\
**État :** en relecture (#197, #200, #207).

## 6. Jouer en Équipe, gérer un imprévu, préparer avec un Assistant

**État :** la pause et la reprise d’une Manche sont dans le socle et sur la Console. Le Format Équipe (#26), les Signalements et l’Exclusion (#31), l’Assistant externe (#28) et les Campagnes SMS (#32, #33) ne sont pas commencés.


# Architecture et frameworks

L’architecture n’a pas changé : un **monolithe modulaire Spring Boot 4.1.2**, en **Java 21**, construit avec le wrapper **Maven**. Une seule application sert les pages et l’API.

<figure class="architecture">
<div><strong>FRONT · HTML, CSS et modules JavaScript, sans build</strong><br>Pages communes : Console d’animation · Manette · Écran de salle, un module par Mode de jeu</div>
<p class="arrow">↕ HTTP JSON : commandes et consultations · STOMP : vues complètes numérotées par Révision</p>
<div><strong>API · Spring Web MVC et WebSocket/STOMP</strong><br>Contrats OpenAPI et AsyncAPI vérifiés en CI · identités de démonstration en attendant la session de l’Animateur (#5)</div>
<p class="arrow">↕ Service de Manche : préparation externe hors verrou, verrou par Soirée, transaction, publication après validation</p>
<div><strong>BACK · Packages métier</strong><br>Livrés : manche · tempsreel · passage · karaoke · soiree · blindtest (domaine, Deezer) · classement · photos · En relecture : quiz · freestyle · animateur</div>
<p class="arrow">↕ Spring Data JPA / Hibernate · 16 migrations Flyway</p>
<div><strong>BD · PostgreSQL 18</strong><br>Docker Compose en développement · Testcontainers en test · Neon pour la recette et la production</div>
<figcaption>État au 25/11 : le serveur décide des règles et publie à chaque écran sa vue complète.</figcaption>
</figure>

## Ce qui a changé dans le back

- **Préparation externe hors verrou** (ADR 0006, précisé avec le Blind test) : un appel à un service extérieur, comme le tirage Deezer, se fait avant de prendre le verrou de la Soirée, puis son résultat est revérifié sous le verrou. Une panne de Deezer donne un refus `503` sans rien modifier.
- **Une Soirée qui se déroule** : le programme ordonne les Manches, la Console les lance une à une, et la clôture d’un Mode de jeu fait avancer la Soirée jusqu’à sa fin.
- **Des points communs** : chaque Mode de jeu transmet ses points, ses bonnes réponses et ses temps de départage au Classement général par `AttributionDePoints`.
- **Aucun son côté serveur** (ADR 0005, ADR 0007) : l’audio du Karaoké, la Musique d’attente et les Instrumentales du Freestyle sont lus par le navigateur de l’Écran de salle, depuis des fichiers choisis sur son ordinateur.

## Intégrations et décisions

| Sujet | Au 18/11 | Au 25/11 |
|---|---|---|
| Source audio du Karaoké | décidée (ADR 0007) | **livrée** : fichier local sur l’Écran de salle, sinon paroles seules |
| Paroles synchronisées | adaptateur LRCLIB fusionné | **affichées** pendant le Passage, calées sur la lecture |
| Extraits du Blind test | Deezer envisagé | **catalogue Deezer fusionné** (#201), service en relecture |
| Banque de Questions en français | OpenQuizzDB proposé | inchangé, ADR 0008 en relecture (#105) |
| Instrumentales du Freestyle | non prévu | dossier local de l’Écran de salle (ADR 0010, en relecture) |
| Prestataire SMS, Assistant | à choisir | inchangés, non commencés |

Décisions d’architecture sur `develop` : ADR 0001 à 0007 et 0009. Les ADR 0008 et 0010 sont proposés dans des PR en relecture.


# Protocole d’IA, livraisons et qualité

## Protocole d’usage de l’IA pour le développement

Le protocole du Rendu 1 reste en vigueur : l’IA aide à la conception, au développement, aux tests et à la rédaction, et les choix techniques comme la validation restent à la charge de l’équipe.

1. **Tâches et outils.** Claude Code, Codex et Antigravity CLI, à partir du glossaire, des ADR, des contrats et des conventions du dépôt.
2. **Traçabilité.** Les issues, les commits et les PR relues retracent chaque contribution et son auteur.
3. **Maîtrise et relecture.** L’auteur comprend et sait expliquer toute contribution intégrée, et obtient la relecture d’un autre membre.
4. **Vérification.** Les tests et la CI passent avant fusion ; une affirmation de l’IA ne prouve pas la réussite d’un test.
5. **Données transmises.** Exemples fictifs seulement, ni secret ni donnée personnelle réelle.

**Mise en œuvre depuis le Rendu 2 :**

- **Un brief écrit par tranche.** Quand un agent implémente une tranche, il part d’un brief qui reprend l’issue, les décisions déjà prises, les fichiers concernés et les critères de réussite. Il committe dans une branche de travail sans rien publier. Les tests sont relancés hors de l’agent, puis la PR est ouverte depuis le compte de l’étudiant qui le pilote.
- **Une relecture indépendante, avant ou pendant la PR.** Un second agent, en lecture seule, relit le code sans le raisonnement de celui qui l’a écrit. Ses constats sont vérifiés un par un ; ceux qui tiennent sont corrigés avec un test. Une PR ne se fusionne qu’après l’approbation d’un autre membre, imposée par la protection des branches.
- **Une recette dans un vrai navigateur.** Les tests ne suffisent pas pour une interface : chaque changement visible est essayé avec la Console, plusieurs Manettes et l’Écran de salle ouverts en même temps, et les captures sont regardées avant la PR.
- **Des ressources bornées.** Les tests tournent avec des limites de mémoire et de durée, et un seul `./mvnw verify` à la fois sur une même machine.

## Livraisons, intégration continue et qualité

Les jalons n’ont pas changé : **`v0.1.0` pour le socle**, **`v0.2.0` pour les Modes de jeu**, **`v1.0.0` pour le proof-of-concept final**. `develop` reçoit les PR relues avec une CI verte ; la livraison passe par une PR de `develop` vers `main`, puis le tag.

**Intégration continue** (`ci.yml`) : contrats HTTP et événements STOMP, build et tests (`./mvnw -B verify`, PostgreSQL par Testcontainers, JaCoCo et PMD bloquant), format des commits, badges mesurés, puis déploiement de la recette après chaque fusion verte. S’y ajoutent CodeQL et Dependabot. **Livraison** (`livraison.yml`) : voir « Conteneurisation ». La livraison `v0.1.0` du 18/11 a réussi tous ses jobs.

| Environnement | Où | Version |
|---|---|---|
| Développement | la machine de chacun, PostgreSQL par Docker Compose | la branche de travail |
| Recette | Render et Neon, <https://croustyquizz-recette.onrender.com> | `develop`, après chaque fusion |
| Production | Render et Neon, <https://croustyquizz-prod.onrender.com> | chaque livraison taguée, `v0.1.0` puis `v0.2.0` |
| Démo de secours | un serveur avec Docker Compose, `tools/demo.sh`, à la demande | un tag |

**Tests.** Au 25/11 sur `3061e8f`, `./mvnw verify` exécute **766 tests JUnit** dans 68 classes, sans échec, contre 308 à la `v0.1.0`. `npm test --prefix tools/api` exécute **244 tests** Node après la vérification des contrats, contre 36. Ils couvrent, en plus de ce que décrivait le Rendu 2 :

- les déroulements du Karaoké et du Blind test en Java pur, par la classe commune `ContratDeroulementDeManche` ;
- les routes HTTP et le canal STOMP de chaque parcours, sur PostgreSQL, validés contre le contrat ;
- un test de charge : 60 Manettes buzzent en même temps dans une Soirée ; les 60 Buzz sont rangés par heure de réception, et la salle, la Console et chaque Manette reçoivent toutes les Révisions dans le même ordre (#194) ;
- la logique des modules d’écran (Karaoké, Buzz, Musique d’attente, photos, programme de la Console) dans un faux DOM, par `node --test`.

Restent à mesurer l’usage sur de vrais téléphones et une Soirée complète en salle.

## README, licence et indicateurs

| Indicateur, au 25/11 sur `3061e8f` | v0.1.0 | Au 25/11 |
|---|---|---|
| Couverture des lignes (JaCoCo) | 94,6 % | **96,8 %** (4 361 lignes sur 4 506) |
| Couverture des branches (JaCoCo) | 85,9 % | **88,2 %** (2 351 sur 2 666) |
| Alertes PMD (Error Prone et Best Practices) | 0 | **0** |
| Tests JUnit et tests Node | 308 et 36 | **766** et **244**, sans échec |
| Migrations Flyway | 4 | **16** |
| Version du `pom.xml` | `0.1.0` | `0.2.0-SNAPSHOT`, passée à `0.2.0` au gel |
| Licence | MIT | **MIT**, fichier `LICENSE` |

Zéro alerte PMD signifie qu’aucun problème n’est détecté par les règles configurées, pas l’absence de bug. Le README présente l’installation, le déploiement, les dossiers de rendu et les badges de version, de licence, de couverture, d’alertes PMD et de CI.

<p class="references">Références : consignes du Rendu 3 (issue nº 213) ; dossiers des Rendus 1 et 2 ; PRD nº 2 ; GLOSSARY.md ; AGENTS.md ; CONTRIBUTING.md ; Dockerfile, compose.yaml, compose.prod.yaml, tools/demo.sh ; workflows ci.yml et livraison.yml ; docs/deploiement.md ; ADR 0001 à 0007 et 0009 ; issues et PR citées, consultées le 25/11/2026.</p>
