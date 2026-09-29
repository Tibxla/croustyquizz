# CroustyQuizz

Plateforme de soirées Quiz, Blind test et Karaoké pour les bars et les lieux qui accueillent du public. L'Animateur pilote la Soirée depuis une Console, la salle suit sur la télé, et chaque client joue depuis son téléphone en scannant un QR code, sans rien installer.

Projet de groupe du M1 MIAGE, Université Paris Nanterre.

## Installer

### Prérequis

- **Git**
- **Un JDK 21 ou plus récent**, par exemple [Temurin](https://adoptium.net/). Maven n'est pas nécessaire : le projet fournit son propre lanceur, `mvnw`.
- **Docker**, qui doit tourner pendant qu'on lance l'application ou les tests.
  - Linux : Docker Engine.
  - macOS : Docker Desktop.
  - Windows : Docker Desktop avec WSL 2. Dans un PowerShell ouvert en administrateur, lancer `wsl --install`, redémarrer, installer [Docker Desktop](https://www.docker.com/products/docker-desktop/) en gardant l'option « Use WSL 2 based engine », puis démarrer Docker Desktop avant de travailler.

### Premier lancement

```bash
git clone https://github.com/Tibxla/croustyquizz.git
cd croustyquizz
git config core.hooksPath .githooks
./mvnw spring-boot:run
```

Sous Windows, dans PowerShell, remplacer `./mvnw` par `.\mvnw.cmd`. Dans Git Bash, `./mvnw` fonctionne.

Le premier lancement télécharge Maven, les dépendances et l'image PostgreSQL : compter quelques minutes. L'application démarre ensuite sa base toute seule et répond sur http://localhost:8080.

### Lancer les tests

```bash
./mvnw verify
```

Les tests de bout en bout démarrent leur propre PostgreSQL jetable : Docker doit tourner.

## Documentation

- [`CONTRIBUTING.md`](CONTRIBUTING.md) : branches, format des commits, pull requests. À lire avant le premier commit.
- [`CONTEXT.md`](CONTEXT.md) : le vocabulaire du projet (Soirée, Manche, Manette…).
- [`AGENTS.md`](AGENTS.md) : commandes, propriétaires des packages, comment coder un Mode de jeu.
- [`docs/conception/`](docs/conception/) : diagrammes de classes.
- [`docs/adr/`](docs/adr/) : décisions d'architecture.
- [PRD (issue #2)](https://github.com/Tibxla/croustyquizz/issues/2) : user stories et décisions de conception.
