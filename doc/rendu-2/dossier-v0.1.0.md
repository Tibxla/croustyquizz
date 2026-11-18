# CroustyQuizz

**Rendu 2 · Version intermédiaire v0.1.0 · M1 MIAGE, Université Paris Nanterre**\
Année 2026-2027 · Révision du 18 novembre 2026

<div class="encadre">

**Version livrée :** tag `v0.1.0`, release <https://github.com/Tibxla/croustyquizz/releases/tag/v0.1.0>\
**Commit figé :** le commit tagué sur `main` est celui qu’indique la page de la release.\
**Dépôt unique, front, API et back :** <https://github.com/Tibxla/croustyquizz>\
**Recette de `develop` :** <https://croustyquizz-recette.onrender.com>\
**Dossier précédent :** `doc/rendu-1/dossier-lancement.md` (Rendu 1, 10 novembre 2026)

</div>

Ce dossier fait évoluer le dossier de lancement du Rendu 1. Il en garde l’ossature. Chaque section dit ce qui a changé depuis le 10 novembre et ce qui est réellement livré. Deux sections s’ajoutent : l’état de la feature majeure de chaque étudiant, et le contenu de la version `v0.1.0`.

Sauf mention contraire, les chiffres sont mesurés le 18 novembre 2026 sur `develop`, au commit `6ae1740`. Le tag est posé le 18 novembre, après les dernières fusions : une PR qui n’est pas fusionnée à ce moment reste « en cours » et son code n’est pas dans la version.

## Équipe, comptes développeurs et fonctionnalités majeures

L’équipe n’a pas changé : **trois étudiants**, chacun responsable d’une fonctionnalité métier majeure, de sa conception à ses tests.

| Étudiant | Compte GitHub personnel | Fonctionnalité majeure (Rendu 1) | Feature en cours pour la v0.1.0 |
|---|---|---|---|
| Thibaud Thomas-Lamotte | [Tibxla](https://github.com/Tibxla) | **Karaoké** | File d’attente du Karaoké (#21), puis Passage (#22) |
| Harris Hamadache | [Piyakabib](https://github.com/Piyakabib) | **Quiz** | Première Manche de Quiz jouable (#14) |
| Rayen Ndiaye | [nRayen](https://github.com/nRayen) | **Blind test** | Créer une Soirée et la rejoindre par code (#6) |

La répartition des packages est celle d’`AGENTS.md` et du PRD : Tibxla porte `manche` (contrat commun), `tempsreel`, `karaoke` et le Pilotage Automatique ; Piyakabib, `quiz`, `animateur` et le Classement général ; nRayen, `soiree`, `blindtest`, le déroulement manuel des Manches, le Format Équipe et `assistant`.

Deux évolutions depuis le Rendu 1 :

- **La feature en cours de nRayen est la Soirée.** Le Blind test reste sa fonctionnalité majeure. Il dépend du déroulement manuel des Manches (#12), lui-même à faire. Dans la répartition du PRD, `soiree` (création, code d’accès, QR code, reconnexion) est la part de nRayen dans le jalon du socle, et `blindtest` sa part dans le jalon des Modes de jeu.
- **Le périmètre de Tibxla s’est élargi** au package `passage`, code commun des Passages (ADR 0006), à la direction visuelle des trois interfaces (#65) et à l’étude de l’existant (#63).

Chaque commit est fait depuis le compte de la personne qui a réalisé le travail. Un agent d’IA committe pour la personne qui le pilote, jamais au nom d’un autre membre (`AGENTS.md`).


<!-- page -->

# Feature majeure de chaque étudiant : état au 18/11

État relevé sur GitHub le 18 novembre à 14 h.

<div class="tableau-large">

| Étudiant | Feature | Issues | PR fusionnées | PR en cours | Ce qui reste |
|---|---|---|---|---|---|
| Tibxla | Karaoké : File d’attente, puis Passage | #21, en tranches #82, #86, #89, #91, #93, #96 ; #22, en tranches #98 à #100 | #88 (contrat) et #87 (domaine) le 17/11, #97 (corrections du contrat) le 18/11 ; socle des Modes de jeu #81, #83, #84 | #90, #92 et #94, en relecture ; #104 en brouillon | fusionner les tranches 3a, 3b et 4 ; Passage (#22) : contrat en cours (#98), domaine, service, interface ; Vote et Palmarès (#23) |
| nRayen | Soirée : créer et rejoindre par code | #6 ; suite dans #10 | aucune pour l’instant | #38 : changements demandés le 17/11 ; branche `feature/10-rejoindre-sans-accroc`, sans PR | traiter les quatre points de relecture et fusionner #38 ; ouvrir la PR de #10 ; déroulement manuel (#12), puis Blind test (#17) |
| Piyakabib | Quiz : première Manche jouable | #14, bloquée par #12 et #13 | aucune pour le Quiz | #105 : ADR 0008, choix de la source des Questions en français (#13), ouverte le 17/11, en relecture | faire valider l’ADR et importer un premier lot de Questions (#13) ; `DeroulementQuiz`, routes et écrans de la Manche (#14) |

</div>

## Tibxla : Karaoké, File d’attente puis Passage

**Fusionné dans `develop`.** La File d’attente (#21) est découpée en tranches qui suivent l’ADR 0006 : contrat, domaine en Java pur, service et routes, interface. Les deux premières sont fusionnées le 17/11, suivies le 18/11 d’une PR de corrections du contrat :

- le **contrat** (#82, PR #88) : huit opérations HTTP de la File d’attente et le bloc `karaoke` des trois écrans dans `docs/api/`. Le vérificateur des contrats contrôle 75 échanges HTTP, 72 événements et 120 cas de schéma pour ce parcours ;
- les **corrections du contrat** demandées par nRayen à la relecture de #88 (#96, PR #97), fusionnées le 18/11 ;
- le **domaine** (#86, PR #87) : `FileAttente` et `Passage` dans `passage`, `DeroulementKaraoke` dans `karaoke`, avec les règles validées le 16/11 dans #21. Les tests du domaine couvrent la rotation par tours, la priorité unique, la limite de Passages en attente par Joueur, le refus d’une même Chanson deux fois en attente et l’accueil d’un retardataire.

**En relecture.**

- **#90** (tranche 3a) : la source de paroles derrière un port (`SourceDeParoles`), son adaptateur LRCLIB et la recherche de Chansons. Les tests n’appellent jamais Internet.
- **#92** (tranche 3b) : le service, les huit routes et deux migrations. La File d’attente devient utilisable par HTTP et les vues des trois écrans reçoivent leur bloc `karaoke`.
- **#94** (tranche 4) : l’interface de la File d’attente sur la Manette, la Console d’animation et l’Écran de salle. nRayen l’a relue trois fois, du 17 au 18/11 ; chaque point demandé est corrigé avec un test, et la recette sert cette version.

**Suite : le Passage (#22).** L’ADR 0007 a tranché la source audio. La tranche 1, le contrat de l’appel, de la lecture et du calage (#98), est écrite : PR #104 en brouillon (dix opérations, parcours d’exemple vérifié), qui sera proposée à la relecture après le Rendu 2. Le domaine (#99) et le service (#100) suivent.

**Travail transverse fusionné depuis le Rendu 1** : canal temps réel (#30), Vue synchronisée et Révisions (#49, #60), routes de test en recette (#58), vérification des réponses HTTP contre les contrats (#59), socle des Modes de jeu (#77, #81, #83, #84), direction visuelle (#75), source audio du Karaoké (#85), livraison et recette (#46, #50), outillage de l’équipe (#41, #44, #45, #52). L’étude de l’existant (#63) est en relecture dans la PR #64.

## nRayen : Soirée, créer et rejoindre par code

**PR #38, ouverte le 10/11, complétée le 17/11** : 14 commits, 54 fichiers. Elle livre le parcours de #6 :

- `Soiree` et `Joueur` en base, avec un code d’accès unique parmi les Soirées ouvertes, garanti par un index ;
- la création, l’ouverture, l’accès de l’Écran de salle et l’arrivée d’un Joueur par HTTP, avec un jeton de reconnexion dont seule l’empreinte est stockée ;
- un QR code généré par le serveur, qui mène à la Manette ;
- les pages pour préparer la Soirée, l’Écran de salle qui affiche le code, le QR code et les Joueurs, et l’arrivée sur la Manette ;
- le branchement de `soiree` sur `tempsreel`, annoncé au propriétaire avant le code.

**Relecture du 17/11 (Tibxla).** Les points de la relecture précédente sont tous traités. Quatre changements sont demandés avant fusion : le QR code mène à une adresse `http://` derrière un relais HTTPS ; la route du QR code n’applique pas la sécurité de son contrat ; le schéma de l’arrivée contredit sa description ; la Manette perd son jeton lors d’une coupure passagère.

**Suite déjà poussée.** La branche `feature/10-rejoindre-sans-accroc` (#10), empilée sur #38, ajoute 6 commits le 17/11 : filtre des pseudos injurieux, refus d’un pseudo déjà pris, tests de la reconnexion d’une Manette avec son jeton, arrivée en cours de Manche et passage de l’arrivée à la Manette en temps réel. Elle n’a pas encore de PR. Au total, les 20 commits de nRayen sont sur ces deux branches et ne sont pas encore dans `develop`.

**Relectures.** nRayen a approuvé 5 PR (#30, #36, #37, #39, #75) et demandé des changements sur 3 (#30, #88, #94). Il a coordonné avec Piyakabib la règle d’arrivée d’un Joueur pendant une Manche de Quiz, puis mis à jour le contrat en conséquence.

**Reste** : le déroulement manuel des Manches (#12), puis le Blind test (#17 à #19), le Format Équipe (#26) et l’Assistant (#28).

## Piyakabib : Quiz, première Manche jouable

**État.** L’issue #14 lui est assignée. Elle est bloquée par #12 (déroulement manuel, nRayen) et par #13 (choix de la source des Questions en français, Piyakabib). Piyakabib a commencé par #13 : la **PR #105**, ouverte le 17/11 au soir, propose l’ADR 0008. Quatre sources y sont comparées (langue, licence, volume, catégories, niveaux, quatre propositions par Question). OpenQuizzDB est retenu pour ses questions en français natif, importées dans la base par une migration Flyway, sans dépendance à un service extérieur pendant une Soirée. À la relecture du 18/11, Tibxla a demandé un chargement réel de dix Questions de la source retenue et une mention de la licence CC BY-SA 4.0 des Questions, distincte de la licence MIT du code.

**Ce qui est prêt pour démarrer.** Le contrat du premier parcours de Quiz est dans `docs/api/` depuis le 10/11 (#34, PR #37) ; le vérificateur y contrôle 47 échanges HTTP, 13 événements et 16 cas de schéma. Le socle fusionné le 17/11 fournit `DeroulementDeManche`, le service de Manche sérialisé par Soirée, la classe de test commune `ContratDeroulementDeManche` et un mode factice qui sert de modèle.

**Relectures.** Piyakabib est le relecteur des PR de Tibxla dans le tourniquet de `CONTRIBUTING.md`. Il a approuvé 24 PR et en a fusionné 17, dont le socle des Modes de jeu et les deux premières tranches du Karaoké. D’après le fil de l’issue #14, il a tranché le 17/11 la règle du Quiz pour un Joueur qui arrive en cours de Manche : il joue aussitôt, Question en cours comprise s’il reste du temps.

**Reste** : faire valider l’ADR 0008 et importer un premier lot de Questions (#13), puis `DeroulementQuiz`, ses routes et ses écrans (#14). Ses autres issues (#5, #9, #15, #16, #24, #31, #32) ne sont pas commencées.


# Ce que contient la version v0.1.0

Le Rendu 1 annonçait `v0.1.0` comme le jalon du **socle**. C’est ce que livre cette version : le socle des Modes de jeu, le temps réel, les contrats réseau, les pages communes des trois écrans, le domaine du Karaoké, l’intégration continue et la livraison. **Aucun Mode de jeu n’est jouable de bout en bout** au 18/11 : les packages `soiree`, `quiz`, `blindtest`, `animateur` et `classement` ne contiennent encore que leur description (`package-info.java`).

## Fusionné dans `develop` au 18/11

- **Socle des Modes de jeu** (ADR 0006, #78 à #80) : `DeroulementDeManche` reçoit les actions des Joueurs et les commandes de pilotage. Le `ServiceDeManche` prend le verrou de la Soirée, appelle le déroulement, enregistre l’état dans une seule transaction, puis publie les vues après validation. Une commande devenue sans objet est refusée (`COMMANDE_PERIMEE`) ; un envoi répété rend son résultat initial ; tout refus suit le format `Probleme`. Le temps passe par l’`Horloge` et ses minuteurs.
- **Temps réel** (ADR 0004) : canal WebSocket et STOMP, Révisions par destination, dernière Vue synchronisée gardée par `tempsreel`, assemblage de chaque vue en une partie commune plus un bloc par Mode de jeu, contrôle d’accès aux abonnements.
- **Pages communes des trois écrans** (#80) : `console.html`, `manette.html` et `ecran.html` chargent le module du Mode de jeu en cours et tiennent leur Vue synchronisée à jour, avec reprise après coupure. Un module factice et des identités de démonstration permettent de les essayer en développement et en recette.
- **Domaine du Karaoké** : File d’attente et Passages (`passage`), déroulement du Karaoké (`karaoke`), en Java pur.
- **Version** : `pom.xml` passe en `0.1.0` (PR #103) ; le README renvoie vers la release et vers les dossiers de rendu.
- **Contrats réseau** (`docs/api/`) : premier parcours de Quiz et File d’attente du Karaoké. Le contrat de la File d’attente intègre les corrections de #97. Le vérificateur contrôle 77 schémas, 34 opérations, 123 échanges HTTP, 85 événements et 143 cas de schéma.
- **Base** : deux migrations Flyway, le point de départ du schéma et le cycle des Manches avec les reçus des envois.
- **Décisions et conception** : ADR 0004, 0006 et 0007 ; direction visuelle (`DESIGN.md`, `PRODUCT.md`, polices et icônes sous licence libre) ; procédure de déploiement (`docs/deploiement.md`).
- **Intégration continue et livraison** : voir la section « Livraisons, intégration continue et qualité ».

## En relecture : livré seulement si fusionné avant le tag

| PR | Contenu | Auteur |
|---|---|---|
| #90 | Source de paroles LRCLIB et recherche de Chansons | Tibxla |
| #92 | Service, routes et persistance de la File d’attente | Tibxla |
| #94 | Interface de la File d’attente sur les trois écrans | Tibxla |
| #38 | Créer une Soirée et la rejoindre par code | nRayen |
| #64 | Étude de l’existant intégrée au glossaire, ADR 0005 | Tibxla |
| #105 | Choix de la source des Questions en français, ADR 0008 | Piyakabib |

## Ce que la v0.1.0 ne contient pas

- Pas de compte Animateur ni de connexion (#5) : Spring Security n’est pas encore dans le projet. En développement et en recette, des identités de démonstration ouvrent les vues.
- Pas de Manche de Quiz, de Blind test ni de Karaoké jouable, pas de Classement général, pas de Pilotage Automatique.
- Pas de test de charge : l’objectif de 60 Joueurs simultanés n’est pas encore mesuré.


# Projet et caractère innovant

Le problème et la cible n’ont pas changé. Un **Lieu** qui accueille du public doit pouvoir préparer et animer une Soirée sans disperser les réponses, la musique et les résultats entre plusieurs outils. L’Animateur pilote depuis la **Console d’animation**, les Joueurs utilisent leur téléphone comme **Manette**, et le public suit l’**Écran de salle**.

## Ce qui a changé depuis le Rendu 1

- **Une étude de l’existant** (novembre 2026, section du PRD nº 2). Deux produits du marché ont été étudiés. Nous en reprenons des parcours et des règles, réécrits dans nos propres spécifications, jamais leurs textes, visuels ou sons. Retenu : trois interfaces au travail distinct, une File d’attente qui tourne par tours avec une priorité visible, un Écran de salle qu’on peut fermer et rouvrir sans interrompre la Manche. Corrigé : des boutons de Réponse qui portent un texte et pas seulement une couleur, un score validé par le serveur avant son animation, aucun succès annoncé sans réponse du serveur. Écarté : la notation de la voix, le hors connexion, les applications natives.
- **Des paliers.** Les ajouts sont rangés en trois paliers pour pouvoir couper par le bas : palier 1, cœur du Karaoké et direction visuelle ; palier 2, Passages à plusieurs et image du Lieu ; palier 3, un quatrième Mode de jeu proposé, le Freestyle, et l’Enregistrement d’un Passage. Ces termes entrent au glossaire avec la PR #64, encore en relecture.
- **Une direction visuelle commune** (#65, `DESIGN.md`) : le rouleau de tickets d’une Soirée, lisible à cinq mètres dans un bar sombre et utilisable d’une main sur la Manette, avec la cible WCAG 2.2 AA.

L’innovation d’usage reste la continuité de la Soirée : **un seul accès par QR code**, une Manette qui change d’elle-même à chaque Manche, un Pilotage Automatique avec reprise en main par l’Animateur, et une préparation par l’Assistant externe. Ces apports restent des hypothèses : aucun test en salle n’a encore eu lieu.

# Dépôt principal et suivi

**Dépôt unique :** <https://github.com/Tibxla/croustyquizz>\
**Spécification de référence :** [PRD, issue nº 2](https://github.com/Tibxla/croustyquizz/issues/2), mise à jour le 15/11 avec l’étude de l’existant.\
**Version de ce rendu :** [doc/rendu-2](https://github.com/Tibxla/croustyquizz/tree/develop/doc/rendu-2), suivie par l’issue nº 101 et fusionnée par la PR nº 102.

Le dossier est maintenu dans **`doc/rendu-2/dossier-v0.1.0.md`** et compilé en **`doc/rendu-2/dossier-v0.1.0.pdf`** par `python3 tools/generer-dossier.py rendu-2`. Le dossier du Rendu 1 reste dans `doc/rendu-1/`.

## Le suivi en chiffres, au 18/11

- **66 issues**, dont 22 fermées.
- **39 PR**, dont 31 fusionnées (2 de Dependabot) et 8 ouvertes. 27 PR ont été fusionnées depuis celle du Rendu 1 (#36).
- **Relectures** : Piyakabib a approuvé 24 PR, nRayen 9, Tibxla 2. Des changements ont été demandés sur 4 PR (#30, #38, #88, #94).

## Ce qui a changé dans la façon de travailler

- **Des issues découpées en tranches.** Une feature passe par quatre PR successives : contrat, domaine, service et routes, interface. Chacune se relit d’un seul tenant.
- **Des PR empilées.** Une tranche part de la branche de la précédente pour que son diff reste lisible ; GitHub la recible vers `develop` à chaque fusion.
- **Des relectures qui suivent les PR empilées.** Avec des PR empilées, une PR peut entrer dans `develop` par celle qui la suit : #81 est ainsi arrivée avec #83, relue et approuvée en même temps. Les remarques faites sur #88 après sa fusion sont reprises dans une PR de suite (#97) : une relecture tardive n'est jamais perdue.


# Scénarios d’usage : cible et état

Les parcours décrivent la **cible fonctionnelle** du Rendu 1. Chacun indique maintenant son état au 18/11. Le premier parcours à réaliser de bout en bout reste une Soirée en Format Solo et Pilotage Manuel avec une Manche de Quiz.

## 1. Préparer et ouvrir une Soirée

**Acteur : l’Animateur.** Il crée une Soirée, choisit le Format et le Pilotage, ajoute et ordonne des Manches, ouvre les inscriptions puis affiche l’Écran de salle avec le QR code.\
**État :** la création, l’ouverture et l’affichage du code et du QR code sont dans la PR #38 (nRayen), en relecture. La connexion de l’Animateur (#5) et la préparation des Manches (#9) ne sont pas commencées.

## 2. Rejoindre, jouer au Quiz et se reconnecter

**Acteurs : l’Animateur et les Joueurs.** Le Joueur scanne le QR code, choisit un pseudo et accède à sa Manette. Il répond à chaque Question avant la fin du délai ; le serveur calcule les points selon la justesse et la rapidité. Après une coupure, il retrouve ses points et sa Réponse.\
**État :** l’arrivée par code est dans #38 ; les pseudos, la reconnexion et l’arrivée en cours de Manche sont sur la branche de #10. Le contrat du Quiz est écrit, la Manche de Quiz (#14) n’est pas commencée. Côté écran, la reprise après coupure est fusionnée : la page recapture sa vue et ignore les Révisions déjà vues.

## 3. Prendre la main au Blind test

**Acteurs : les Joueurs et l’Animateur.** Les Joueurs buzzent sur un extrait ; le serveur ordonne les Buzz et donne la main au premier ; une erreur bloque jusqu’au Morceau suivant.\
**État :** non commencé (#17, bloquée par #12). Un prototype de Buzz entre une Manette et l’Écran de salle existe depuis la PR #30, sur des pages de test.

## 4. Chanter et voter au Karaoké

**Acteurs : le Joueur appelé, le public et l’Animateur.** Un Joueur s’inscrit dans la File d’attente, chante quand il est appelé, puis le public vote de 1 à 5.\
**État :** le contrat et le domaine de la File d’attente sont fusionnés ; le service et l’interface sont en relecture (#92, #94). La source audio est décidée (ADR 0007) : un fichier local choisi sur l’ordinateur de l’Écran de salle, sinon les paroles seules. Le Rendu 1 prévoyait, en Pilotage Automatique, de passer au suivant après 30 secondes sans confirmation. La spécification du Passage (#22) traite d’abord le Pilotage Manuel : ces 30 secondes y sont un repère, le Joueur appelé peut encore confirmer, et l’Animateur décide d’attendre ou de passer. Le Vote et le Palmarès (#23) restent à faire.

## 5. Jouer en Équipe et gérer un imprévu

Format Équipe sans capitaine, pause et reprise, Signalements, Avertissement et Exclusion.\
**État :** non commencé (#11, #26, #31). Le socle prévoit déjà les commandes de pilotage d’une Manche et leur refus quand elles sont périmées.

## 6. Préparer avec un Assistant et prolonger la relation

Préparation par l’Assistant externe avec une Clé d’Assistant, Contacts du Lieu et Campagnes SMS.\
**État :** non commencé (#28, #32, #33). Ce complément reste secondaire par rapport aux Modes de jeu.


# Architecture et frameworks

L’architecture n’a pas changé : un **monolithe modulaire Spring Boot 4.1.2**, en **Java 21**, construit avec le wrapper **Maven**. Une seule application sert les pages et l’API. Spring Boot est passé de 4.1.1 à 4.1.2 par une mise à jour de Dependabot (#53).

<figure class="architecture">
<div><strong>FRONT · HTML, CSS et modules JavaScript, sans build</strong><br>Pages communes : Console d’animation · Manette · Écran de salle, un module par Mode de jeu</div>
<p class="arrow">↕ HTTP JSON : commandes et consultations · STOMP : vues complètes numérotées par Révision</p>
<div><strong>API · Spring Web MVC et WebSocket/STOMP</strong><br>Contrats OpenAPI et AsyncAPI vérifiés en CI · identités de démonstration en attendant Spring Security (#5)</div>
<p class="arrow">↕ Service de Manche : verrou par Soirée, transaction, publication après validation</p>
<div><strong>BACK · Packages métier</strong><br>Livrés : manche · tempsreel · passage · karaoke (domaine) · À venir : soiree · quiz · blindtest · animateur · classement</div>
<p class="arrow">↕ Spring Data JPA / Hibernate · migrations Flyway</p>
<div><strong>BD · PostgreSQL 18</strong><br>Docker Compose en développement · Testcontainers en test · Neon pour la recette</div>
<figcaption>État de la v0.1.0 : le serveur décide des règles et publie à chaque écran sa vue complète.</figcaption>
</figure>

## Front et contrats réseau

Les pages restent servies par Spring Boot, **sans framework front ni bundler** (ADR 0001). Les trois pages communes chargent le module du Mode de jeu en cours (`modes/<mode>/<écran>.mjs`) ; la bibliothèque STOMP vient d’un WebJar Maven. Un module client de Vue synchronisée applique la séquence du contrat : capture, puis seuls les événements de Révision supérieure, et nouvelle capture en cas de trou.

Les contrats **OpenAPI** et **AsyncAPI** couvrent le premier parcours de Quiz et la File d’attente du Karaoké. Ils sont écrits avant le code (ADR 0003) et vérifiés en CI. Depuis la PR #59, chaque test de contrôleur valide sa réponse HTTP contre le contrat avec `ContratApi`.

## Back et persistance

Deux décisions précisent le socle depuis le Rendu 1 :

- **ADR 0004** : `tempsreel` numérote les Révisions de chaque destination et garde la dernière vue publiée. Chaque Mode de jeu publie à chaque changement la vue complète de chaque destination, jamais un changement partiel.
- **ADR 0006** : toute commande passe par le service du Mode de jeu, sérialisé par Soirée. Une commande périmée se reconnaît à la version de sa cible. Chaque vue est une partie commune plus un bloc par Mode de jeu. Le code commun aux Passages vit dans `passage`.

En base, `manche_deroulement` garde l’état de chaque Manche et `manche_envoi` les reçus des envois, pour qu’un envoi répété rende son résultat initial.

## Intégrations et décisions

| Sujet | Rendu 1 | Au 18/11 |
|---|---|---|
| Source audio du Karaoké | à valider | **décidée** (ADR 0007, après un prototype, #20) : fichier local sur l’Écran de salle, sinon paroles seules ; une vidéo intégrée au palier 2 |
| Paroles synchronisées | LRCLIB envisagé | adaptateur LRCLIB en relecture (#90) |
| Extraits du Blind test | Deezer envisagé | inchangé, non commencé |
| Banque de Questions en français | à choisir | à choisir (#13) |
| Prestataire SMS | à choisir | à choisir (#33) |
| Assistant (serveur MCP) | Spring AI envisagé | inchangé, non commencé (#28) |

Décisions d’architecture sur `develop` : ADR 0001 à 0004, 0006 et 0007. L’ADR 0005, sur l’Enregistrement d’un Passage, est dans la PR #64, et l’ADR 0008, sur la source des Questions du Quiz, dans la PR #105.


# Protocole d’IA, livraisons et qualité

## Protocole d’usage de l’IA pour le développement

Le protocole du Rendu 1 reste en vigueur. Nous utilisons l’IA comme aide à la conception, au développement, aux tests et à la rédaction. Les choix techniques et la validation des livrables restent à la charge de l’équipe.

1. **Tâches et outils.** Claude Code, Codex et Antigravity CLI servent à comprendre du code, comparer des pistes, proposer des implémentations et des cas de test, et rédiger la documentation, à partir du glossaire, des ADR et des conventions du dépôt.
2. **Traçabilité.** Les issues, les commits et les PR relues retracent chaque contribution et son auteur.
3. **Maîtrise et relecture.** L’auteur comprend et sait expliquer toute contribution intégrée, et obtient la relecture d’un autre membre.
4. **Vérification.** Les tests et la CI passent avant fusion ; une affirmation de l’IA ne prouve pas la réussite d’un test.
5. **Données transmises.** Exemples fictifs seulement, ni secret ni donnée personnelle réelle.

**Mise en œuvre depuis le Rendu 1 :**

- **Les mêmes consignes pour tous.** `AGENTS.md` décrit un parcours unique, pour un humain comme pour un agent : partir d’une issue, mettre à jour le contrat d’abord, écrire les tests avec le code, `./mvnw verify` vert avant la PR. Les skills d’agent sont partagés par le dépôt (#41, #44) : Claude Code les propose à l’ouverture, Codex et Antigravity CLI les lisent dans `.agents/skills/`.
- **Une relecture critique par un second agent avant la PR**, signalée dans sa description. Sur #92, elle a trouvé deux défauts, corrigés chacun avec un test de régression. La relecture humaine reste obligatoire.
- **Les décisions restent humaines et vérifiées.** Les règles du Karaoké ont été validées par Tibxla le 16/11 (#21). La source audio a été tranchée par un prototype mesuré (#20) : neuf vidéos de musique sur onze refusées par le lecteur intégré.
- **La section « Usage de l’IA » du gabarit de PR a été retirée** (#55, #56) : elle n’apportait rien à l’équipe. La traçabilité repose sur les issues, les commits et les relectures.

## Livraisons, intégration continue et qualité

Les jalons n’ont pas changé : **`v0.1.0` pour le socle**, **`v0.2.0` pour les Modes de jeu**, **`v1.0.0` pour le proof-of-concept final**. `develop` reçoit les PR relues avec une CI verte ; la livraison passe par une PR de `develop` vers `main`, puis le tag.

**Intégration continue** (`ci.yml`, à chaque PR et à chaque fusion sur `develop` ou `main`) :

- **Contrats HTTP et événements STOMP** : validation des contrats, de leurs exemples et des cas de confidentialité, puis tests de la Vue synchronisée et des pages communes (Node 24) ;
- **Build et tests** : `./mvnw -B verify` en Java 21, avec PostgreSQL jetable par Testcontainers, couverture JaCoCo et PMD bloquant ; les rapports sont gardés comme artefacts ;
- **Format des commits** (sur une PR) : chaque message doit suivre Conventional Commits, comme le hook local ;
- **Publier les badges mesurés** (après une fusion) : couverture et alertes PMD sur la branche `mesures-qualite` ;
- **Déployer la recette** (après une fusion sur `develop`, contrats et build verts) : Render construit ce commit précis. Le 17/11, le déploiement de `e8bb108` a été demandé par la CI, et la recette répondait.

S’y ajoutent l’analyse CodeQL de GitHub, Dependabot (mises à jour groupées chaque mois, #45) et l’épinglage par commit des actions tierces de la livraison (#52).

**Livraison** (`livraison.yml`, au tag `vX.Y.Z`) : vérification du format du tag et de sa présence sur `main`, `./mvnw -B verify` ; publication de l’image Docker sur GHCR (`ghcr.io/tibxla/croustyquizz`) ; GitHub Release avec le JAR exécutable et les notes de version ; déploiement de la production sur Render (<https://croustyquizz-prod.onrender.com>). Le 17/11, une exécution manuelle sur `develop` a réussi l’étape de vérification. Le tag `v0.1.0` déclenche la première livraison complète : le résultat de ses jobs est visible dans l’onglet Actions du dépôt, et le JAR sur la page de la release.

| Environnement | Où | Version |
|---|---|---|
| Développement | la machine de chacun | la branche de travail |
| Recette | Render et Neon, <https://croustyquizz-recette.onrender.com> | `develop`, après chaque fusion |
| Production | Render et Neon, <https://croustyquizz-prod.onrender.com>, créée au jalon `v0.1.0` | chaque livraison taguée, à partir de `v0.1.0` |
| Démo de secours | serveur de Tibxla, `tools/demo.sh`, à la demande | `main` ou un tag |

**Tests.** Au 18/11 sur `6ae1740`, `./mvnw verify` exécute **142 tests JUnit** dans 22 classes, sans échec. `npm test --prefix tools/api` exécute **36 tests** Node, après la vérification des contrats. Les tests couvrent :

- les déroulements en Java pur avec une `Horloge` factice, par la classe commune `ContratDeroulementDeManche` ;
- le service de Manche : verrou, transaction, publication après validation, envois répétés ;
- les routes HTTP sur PostgreSQL, chacune validée contre le contrat ;
- le canal STOMP, le contrôle d’accès et la Vue synchronisée ;
- les règles de la File d’attente du Karaoké.

Restent à mesurer : une salle de **60 Joueurs simultanés** et l’usage sur de vrais téléphones.

## README, licence et indicateurs

| Indicateur, au 18/11 sur `6ae1740` | Valeur |
|---|---|
| Couverture des lignes (JaCoCo) | **95,4 %** (880 lignes sur 922) |
| Couverture des branches (JaCoCo) | **87,0 %** (456 sur 524) |
| Alertes PMD (Error Prone et Best Practices) | **0** |
| Tests JUnit et tests Node | **142** et **36**, sans échec |
| Version du `pom.xml` | `0.1.0` |
| Licence | **MIT**, fichier `LICENSE` |

Ces mesures portent sur le code existant, c’est-à-dire le socle et le domaine du Karaoké. Elles ne disent rien des packages encore vides. Zéro alerte PMD signifie qu’aucun problème n’est détecté par les règles configurées, pas l’absence de bug. Le README présente l’installation, le déploiement et les badges de version, de licence, de couverture, d’alertes PMD et de CI ; les badges de couverture et de PMD reflètent le dernier build réussi de `develop`.

<p class="references">Références : consignes du Rendu 2 (issue nº 101) ; dossier du Rendu 1 ; PRD nº 2 ; GLOSSARY.md ; AGENTS.md ; CONTRIBUTING.md ; PRODUCT.md ; DESIGN.md ; pom.xml ; workflows ci.yml et livraison.yml ; docs/deploiement.md ; docs/ecrans.md ; ADR 0001 à 0004, 0006 et 0007 ; issues et PR citées, consultées le 18/11/2026.</p>
