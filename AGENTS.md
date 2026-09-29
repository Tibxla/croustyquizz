# CroustyQuizz

Plateforme de soirées Quiz, Blind test et Karaoké pour les lieux qui accueillent du public : l'Animateur pilote depuis la Console d'animation, les Joueurs jouent depuis leur Manette (page web sur téléphone, ouverte par QR code), et la salle suit sur l'Écran de salle. Projet de groupe du M1 MIAGE (Université Paris Nanterre), noté individuellement.

## À lire avant de coder

- `CONTEXT.md` : le glossaire. Employer ses termes dans le code, les issues et les commits.
- `CONTRIBUTING.md` : branches, format des commits, pull requests. Obligatoire.
- `docs/adr/` : décisions d'architecture déjà prises.

## Stack

- Back : Java 21, Spring Boot 3, Maven. Une seule application, découpée en packages.
- Temps réel : WebSocket + STOMP (Spring).
- Base : PostgreSQL via Docker Compose, schéma versionné par Flyway.
- Front : HTML + JavaScript servis par Spring Boot, sans npm ni bundler (ADR 0001).

## Commandes

À compléter au jalon 0, quand le squelette Maven existera.

## Propriétaires

Chaque package a un propriétaire, qui écrit et relit en premier ce qui s'y passe. Ne pas modifier le package d'un autre sans le prévenir dans la PR.

| Package | Propriétaire |
|---|---|
| `karaoke`, `tempsreel`, pilotage automatique | Tibxla |
| `quiz`, `animateur`, classement général | Piyakabib |
| `blindtest`, `soiree`, format Équipe | nRayen |

## Skills d'agent

### Gestionnaire d'issues

Issues et PRD dans les GitHub Issues de Tibxla/croustyquizz, via `gh`. Voir `docs/agents/issue-tracker.md`.

### Labels de tri

Les cinq labels par défaut (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). Voir `docs/agents/triage-labels.md`.

### Doc du domaine

Un seul contexte : `CONTEXT.md` et `docs/adr/` à la racine. Voir `docs/agents/domain.md`.
