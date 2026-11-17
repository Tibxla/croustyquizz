# CroustyQuizz

Plateforme de soirées Quiz, Blind test et Karaoké pour les lieux qui accueillent du public : l'Animateur pilote depuis la Console d'animation, les Joueurs jouent depuis leur Manette (page web sur téléphone, ouverte par QR code), et la salle suit sur l'Écran de salle. Projet de groupe du M1 MIAGE (Université Paris Nanterre), noté individuellement : chaque contribution doit rester attribuable à son auteur.

## À lire avant de coder

- `GLOSSARY.md` : le glossaire. Employer ses termes dans le code, les issues et les commits.
- `CONTRIBUTING.md` : branches, format des commits, pull requests, relecture. Obligatoire.
- `docs/adr/` : décisions d'architecture déjà prises.
- `docs/api/` : contrats HTTP (OpenAPI) et événements STOMP (AsyncAPI), avant de toucher un contrôleur ou un événement.

## Méthode de travail

Le même parcours pour tout le monde, humain ou agent :

1. Partir d'une issue assignée à la personne qui travaille. Sans issue, en créer une (voir `docs/agents/issue-tracker.md`).
2. Créer la branche depuis `develop` à jour, nommée comme dans `CONTRIBUTING.md`.
3. Un échange réseau nouveau ou modifié : mettre d'abord à jour `docs/api/`, puis l'implémenter (ADR 0003).
4. Écrire les tests avec le code (skill `tdd`) ; les délais passent par une `Horloge` factice.
5. Avant d'ouvrir la PR : `./mvnw verify` vert, et `npm test --prefix tools/api` vert si `docs/api/` a changé ; chaque test de contrôleur valide sa réponse contre le contrat avec `ContratApi`.
6. Ouvrir la PR vers `develop` en remplissant `.github/pull_request_template.md`, et demander la relecture prévue par le tourniquet de `CONTRIBUTING.md`.

Chaque commit est fait depuis le compte de la personne qui a réalisé le travail : un agent committe pour la personne qui le pilote, jamais au nom d'un autre membre.

## Stack

- Back : Spring Boot 4, Maven (wrapper `./mvnw`, rien à installer), code compilé pour Java 21. Une seule application, découpée en packages sous `fr.croustyquizz`.
- Temps réel : WebSocket + STOMP (Spring).
- Base : PostgreSQL via Docker Compose, schéma versionné par Flyway. Une migration se nomme `V<horodatage>__<sujet>.sql` et ne se modifie plus une fois fusionnée : en écrire une nouvelle (`CONTRIBUTING.md`, « Base de données »).
- Front : HTML + JavaScript servis par Spring Boot, sans npm ni bundler (ADR 0001). Le `package.json` de `tools/api/` ne sert qu'à valider les contrats.

## Commandes

Docker doit tourner pour lancer l'application et pour les tests.

| Commande | Effet |
|---|---|
| `./mvnw spring-boot:run` | démarre PostgreSQL (Docker Compose) puis l'application sur http://localhost:8080 |
| `./mvnw verify` | build, tests (PostgreSQL jetable par Testcontainers), couverture JaCoCo et alertes PMD bloquantes |
| `./mvnw test -Dtest=NomDuTest` | un seul test |
| `npm ci --prefix tools/api` puis `npm test --prefix tools/api` | valide les contrats, les exemples et les cas de confidentialité de `docs/api/` (Node 24) |
| `python3 tools/generer-dossier.py` | régénère le PDF du dossier de rendu depuis `doc/rendu-1/` (Pandoc et Chrome) ; vérifier qu'il fait toujours quatre pages |
| `tools/demo.sh demarrer` / `arreter` | démo sur le serveur, le temps d'une séance : jamais en développement, procédure dans `docs/deploiement.md` |
| `docker compose down -v` | efface la base locale pour repartir de zéro |
| `git config core.hooksPath .githooks` | une fois par clone : refuse les messages de commit hors format |

Sous Windows, dans PowerShell : `.\mvnw.cmd` au lieu de `./mvnw`.

## Coder un Mode de jeu

Le contrat est dans `manche` : implémenter `DeroulementDeManche` et une `FabriqueDeDeroulement<R, E>` (réglages typés, création, restauration avec `EtatManche`). Déclarer un service de mode qui étend `ServiceDeManche<R, E>`, sa `PersistanceDuMode<E>` JPA et un `FournisseurDeBloc` dans le package du mode. Les tables du mode sont migrées par Flyway ; `manche_deroulement` conserve le cycle commun et `manche_envoi` les reçus idempotents.

Toute commande passe par le service : préparation externe hors verrou, verrou de Soirée, transaction, appel du déroulement, persistance, assemblage des vues, puis publication après commit et avant libération du verrou (ADR 0006). Un refus ne change jamais l'`instantane()` immuable. Le déroulement rend un `ResultatDeCommande` avec l'accusé HTTP, les destinations modifiées et un type d'événement du contrat ; il n'a plus de diffuseur. Si la transaction échoue, le service évince le déroulement pour le restaurer à la prochaine commande.

Le temps passe exclusivement par l'`Horloge` du `ContexteDeManche` (jamais `Instant.now()` ni de thread). Ses callbacks repassent par le service ; la fabrique précise `typeEvenementMinuteur` pour ses phases. `reglagesCommuns` extrait les réglages du contexte depuis les réglages typés du mode. Les fournisseurs produisent des projections sans secrets pour chaque destination sous le même verrou ; seul le socle compose et publie la vue complète. La classe de test du mode étend `ContratDeroulementDeManche` (modèle : `DeroulementFacticeTest`), notamment pour les refus sans mutation et après la fin.

## Propriétaires

Chaque package a un propriétaire, qui écrit et relit en premier ce qui s'y passe. Une PR qui touche le package d'un autre le dit dans sa description et demande sa relecture.

| Package | Propriétaire |
|---|---|
| `manche` (contrat commun), `tempsreel`, `karaoke`, pilotage automatique | Tibxla |
| `quiz`, `animateur`, classement général | Piyakabib |
| `soiree`, `blindtest`, déroulement manuel des Manches, format Équipe, `assistant` (serveur MCP) | nRayen |

## Skills d'agent

Les skills de [mattpocock/skills](https://github.com/mattpocock/skills) et d'[Impeccable](https://impeccable.style) sont partagés : Claude Code les propose à l'ouverture du dépôt (`.claude/settings.json`), les autres agents les installent comme décrit dans `CONTRIBUTING.md`. Repères :

- `tdd` pour implémenter une issue, `diagnosing-bugs` pour un bug qui résiste.
- `grill-with-docs` pour trancher un terme ou une décision ; il met à jour `GLOSSARY.md` et `docs/adr/`.
- `code-review` de mattpocock-skills (`/mattpocock-skills:code-review` dans Claude Code) avant d'ouvrir une PR.
- `impeccable` pour concevoir ou relire la Console d'animation, la Manette et l'Écran de salle.

### Gestionnaire d'issues

Issues et PRD dans les GitHub Issues de Tibxla/croustyquizz, via `gh`. Voir `docs/agents/issue-tracker.md`.

### Labels de tri

Les cinq labels par défaut (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). Voir `docs/agents/triage-labels.md`.

### Doc du domaine

Un seul contexte : `GLOSSARY.md` et `docs/adr/` à la racine. Voir `docs/agents/domain.md`.
