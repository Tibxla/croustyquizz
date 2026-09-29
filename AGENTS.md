# CroustyQuizz

Plateforme de soirées Quiz, Blind test et Karaoké pour les lieux qui accueillent du public : l'Animateur pilote depuis la Console d'animation, les Joueurs jouent depuis leur Manette (page web sur téléphone, ouverte par QR code), et la salle suit sur l'Écran de salle. Projet de groupe du M1 MIAGE (Université Paris Nanterre), noté individuellement.

## À lire avant de coder

- `CONTEXT.md` : le glossaire. Employer ses termes dans le code, les issues et les commits.
- `CONTRIBUTING.md` : branches, format des commits, pull requests. Obligatoire.
- `docs/adr/` : décisions d'architecture déjà prises.

## Stack

- Back : Spring Boot 4, Maven (wrapper `./mvnw`, rien à installer), code compilé pour Java 21. Une seule application, découpée en packages sous `fr.croustyquizz`.
- Temps réel : WebSocket + STOMP (Spring).
- Base : PostgreSQL via Docker Compose, schéma versionné par Flyway.
- Front : HTML + JavaScript servis par Spring Boot, sans npm ni bundler (ADR 0001).

## Commandes

Docker doit tourner pour lancer l'application et pour les tests.

| Commande | Effet |
|---|---|
| `./mvnw spring-boot:run` | démarre PostgreSQL (Docker Compose) puis l'application sur http://localhost:8080 |
| `./mvnw verify` | build et tous les tests, avec un PostgreSQL jetable (Testcontainers) |
| `./mvnw test -Dtest=NomDuTest` | un seul test |
| `docker compose down -v` | efface la base locale pour repartir de zéro |
| `git config core.hooksPath .githooks` | une fois par clone : refuse les messages de commit hors format |

Sous Windows, dans PowerShell : `.\mvnw.cmd` au lieu de `./mvnw`.

## Coder un Mode de jeu

Le contrat est dans le package `manche` : implémenter `DeroulementDeManche` et déclarer une `FabriqueDeDeroulement` en bean Spring. Pas de `Instant.now()` ni de thread : le temps passe par l'`Horloge` du `ContexteDeManche`, et les écrans ne se joignent que par le `DiffuseurTempsReel`. La classe de test du mode étend `ContratDeroulementDeManche` (modèle : `DeroulementFacticeTest`).

## Propriétaires

Chaque package a un propriétaire, qui écrit et relit en premier ce qui s'y passe. Ne pas modifier le package d'un autre sans le prévenir dans la PR.

| Package | Propriétaire |
|---|---|
| `manche` (contrat commun), `tempsreel`, `karaoke`, pilotage automatique | Tibxla |
| `quiz`, `animateur`, classement général | Piyakabib |
| `soiree`, `blindtest`, déroulement manuel des Manches, format Équipe, `assistant` (serveur MCP) | nRayen |

## Skills d'agent

### Gestionnaire d'issues

Issues et PRD dans les GitHub Issues de Tibxla/croustyquizz, via `gh`. Voir `docs/agents/issue-tracker.md`.

### Labels de tri

Les cinq labels par défaut (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). Voir `docs/agents/triage-labels.md`.

### Doc du domaine

Un seul contexte : `CONTEXT.md` et `docs/adr/` à la racine. Voir `docs/agents/domain.md`.
