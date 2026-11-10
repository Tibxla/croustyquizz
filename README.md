# CroustyQuizz

[![Version du socle : 0.0.1-SNAPSHOT](https://img.shields.io/badge/version-0.0.1--SNAPSHOT-blue)](pom.xml)
[![Licence MIT](https://img.shields.io/badge/licence-MIT-green)](LICENSE)
[![Couverture des lignes — JaCoCo](https://raw.githubusercontent.com/Tibxla/croustyquizz/mesures-qualite/develop/couverture.svg)](https://github.com/Tibxla/croustyquizz/actions/workflows/ci.yml?query=branch%3Adevelop)
[![Alertes de qualité — PMD](https://raw.githubusercontent.com/Tibxla/croustyquizz/mesures-qualite/develop/qualite.svg)](https://github.com/Tibxla/croustyquizz/actions/workflows/ci.yml?query=branch%3Adevelop)
[![CI](https://github.com/Tibxla/croustyquizz/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/Tibxla/croustyquizz/actions/workflows/ci.yml)

Plateforme de soirées Quiz, Blind test et Karaoké pour les bars et les lieux qui accueillent du public. L'Animateur pilote la Soirée depuis une Console, la salle suit sur la télé, et chaque client joue depuis son téléphone en scannant un QR code, sans rien installer.

Projet de groupe du M1 MIAGE, Université Paris Nanterre.

## Équipe et fonctionnalités majeures

| Étudiant | Compte développeur | Fonctionnalité majeure |
|---|---|---|
| Thibaud Thomas-Lamotte | [Tibxla](https://github.com/Tibxla) | Karaoké : File d'attente, Passages, paroles synchronisées et Vote du public |
| Harris Hamadache | [Piyakabib](https://github.com/Piyakabib) | Quiz : Questions, Réponses, correction et calcul des points |
| Rayen Ndiaye | [nRayen](https://github.com/nRayen) | Blind test : Morceaux, Buzz, correction et calcul des points |

## Dossier de lancement — Rendu 1

Le dossier demandé par le cours est regroupé dans **`doc/rendu-1/`** : [source Markdown](doc/rendu-1/dossier-lancement.md) et [PDF de quatre pages](doc/rendu-1/dossier-lancement.pdf). Il sera mis à jour avec les livraisons ; `docs/` conserve la documentation technique.

Pour régénérer le PDF, installer [Pandoc](https://pandoc.org/installing.html) et Chrome ou Chromium, puis lancer depuis la racine :

```bash
python3 tools/generer-dossier.py
```

Le script produit le PDF à partir du Markdown et de `tools/dossier-lancement.css`. Le HTML intermédiaire est généré dans `target/dossier-lancement/`, ignoré par Git. Si nécessaire, définir `CHROME_BIN` avec le chemin du navigateur. Vérifier les quatre pages après chaque modification.

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

## Déployer

Un tag `vX.Y.Z` sur `main` déclenche les vérifications, la publication de l'image sur GHCR et la création d'une GitHub Release avec le JAR exécutable.
La **recette** de `develop` tourne sur https://croustyquizz-recette.onrender.com, redéployée après chaque fusion ; la **production** recevra chaque livraison taguée sur https://croustyquizz-prod.onrender.com à partir de `v0.1.0`. Sur le serveur de démo, `tools/demo.sh demarrer` et `tools/demo.sh arreter` lancent l'application seulement le temps d'une séance. La [procédure de déploiement](docs/deploiement.md) décrit l'installation, la sécurité, l'accès HTTPS, le plan de secours et le passage chez un autre hébergeur.

## Indicateurs de qualité

`./mvnw verify` exécute les tests, mesure leur couverture avec **JaCoCo** et analyse le code Java de production avec **PMD**. PMD applique les catégories **Error Prone** et **Best Practices** ; une alerte fait échouer le build. Aucun seuil de couverture n'est imposé à ce stade.

Pour consulter les résultats localement :

- `target/site/jacoco/index.html` : couverture des lignes et des branches du code Java de production.
- `target/site/pmd.html` : alertes PMD, avec leurs fichiers et règles.
- `python3 tools/mesures-qualite.py` : synthèse JSON et badges SVG dans `target/qualite/`, calculés à partir des rapports XML.

La CI conserve les rapports et les résultats de tests comme artefacts téléchargeables depuis chaque exécution. Après un build réussi sur `develop` ou `main`, elle publie les badges et le commit analysé sur la branche dédiée [`mesures-qualite`](https://github.com/Tibxla/croustyquizz/tree/mesures-qualite). Les badges ci-dessus reflètent le **dernier build réussi de `develop`** ; consulter aussi le badge CI pour détecter un échec plus récent. Avant la première publication, les images ne sont pas encore disponibles.

La couverture porte sur le code existant, pas sur les fonctionnalités encore à développer. Un résultat PMD sans alerte indique l'absence de problèmes détectés par les règles configurées ; il ne garantit pas l'absence de bugs. Le badge de version reflète le `pom.xml`. Le déploiement automatisé de l'application reste à mettre en place.

## Licence

Le code du projet est distribué sous [licence MIT](LICENSE).

## Documentation

- [`CONTRIBUTING.md`](CONTRIBUTING.md) : branches, format des commits, pull requests. À lire avant le premier commit.
- [`doc/rendu-1/dossier-lancement.md`](doc/rendu-1/dossier-lancement.md) : dossier de lancement, scénarios, architecture et protocole d'usage de l'IA.
- [`GLOSSARY.md`](GLOSSARY.md) : le vocabulaire du projet (Soirée, Manche, Manette…).
- [`AGENTS.md`](AGENTS.md) : commandes, propriétaires des packages, comment coder un Mode de jeu.
- [`docs/conception/`](docs/conception/) : diagrammes de classes.
- [`docs/adr/`](docs/adr/) : décisions d'architecture.
- [PRD (issue #2)](https://github.com/Tibxla/croustyquizz/issues/2) : user stories et décisions de conception.
