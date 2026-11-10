# Déployer la démo

L'application et PostgreSQL tournent sur le serveur de Tibxla avec Docker Compose.
Tailscale Funnel donne une adresse HTTPS publique aux Manettes, à la Console
d'animation et à l'Écran de salle. PostgreSQL ne publie aucun port sur le serveur.

## Prérequis

- Un serveur Linux x86_64 allumé pendant la Soirée, avec une connexion Internet.
- Docker Engine et le plugin Docker Compose, accessibles au compte de Tibxla.
- Tailscale installé et connecté au compte de Tibxla. Activer MagicDNS, HTTPS et
  l'autorisation Funnel dans le réseau Tailscale selon la
  [documentation Tailscale](https://tailscale.com/kb/1223/funnel).
- Git pour récupérer les fichiers du dépôt. Aucun JDK ni Maven n'est nécessaire
  sur le serveur : l'image contient l'application prête à lancer.
- Une image disponible dans `ghcr.io/tibxla/croustyquizz`. Après la première
  publication, Tibxla doit rendre le paquet GHCR public dans les paramètres du
  paquet GitHub pour permettre le téléchargement sans authentification.

## Livraison depuis GitHub

Le workflow `livraison.yml` relance `./mvnw -B verify` avec Java 21. Il publie
ensuite l'image sur GHCR : `main` pour un push sur `main`, `X.Y.Z` et `latest`
pour un tag `vX.Y.Z`. Les tests avec Testcontainers tournent sur le runner GitHub,
avant la construction de l'image. Le Dockerfile compile sans exécuter les tests.

Le tag doit viser un commit présent sur `main`, conformément à `CONTRIBUTING.md`.
Les jalons prévus sont `v0.1.0`, `v0.2.0` et `v1.0.0`. Après la publication de
l'image d'un tag, le workflow crée une GitHub Release avec les notes générées et
le JAR exécutable vérifié. Le nom du JAR conserve la version du `pom.xml`.
Le workflow utilise `GITHUB_TOKEN`, sans secret de registre supplémentaire.

Un lancement manuel depuis GitHub Actions suit les mêmes règles : sur `main`,
il publie l'image `main` ; sur un tag valide, il publie l'image et la release ;
sur une autre branche, il lance seulement les vérifications.
Le workflow ne se connecte pas au serveur. Tibxla choisit quand y installer une
version après une livraison réussie.

## Première installation

Sur le serveur, récupérer les fichiers puis préparer les variables :

```bash
git clone https://github.com/Tibxla/croustyquizz.git
cd croustyquizz
git switch main
cp .env.example .env
chmod 600 .env
```

Dans `.env`, renseigner `POSTGRES_PASSWORD` avec un mot de passe long et unique.
Conserver `POSTGRES_DB` et `POSTGRES_USER`, ou choisir leurs valeurs avant le
premier démarrage. Inscrire la version choisie, sans `v`, dans
`CROUSTYQUIZZ_VERSION`, par exemple `0.1.0` une fois cette livraison disponible.
Le fichier `.env` reste sur le serveur et n'est jamais ajouté à Git.
Changer ces variables après l'initialisation ne change pas les identifiants de
la base déjà stockée dans le volume.

Lancer la commande de déploiement ci-dessous depuis ce dossier. L'application
attend que PostgreSQL soit sain. Flyway applique les migrations au démarrage,
puis Hibernate vérifie le schéma. Le volume nommé `donnees-postgres` conserve la
base lors du remplacement des conteneurs. PostgreSQL 18 utilise le montage
`/var/lib/postgresql`, comme décrit dans la
[documentation Docker](https://docs.docker.com/guides/postgresql/immediate-setup-and-data-persistence/).

Le profil `prod` reçoit la connexion via `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` et `SPRING_DATASOURCE_PASSWORD`, renseignées par
Compose. Il désactive le support Docker Compose de Spring. Le plugin Maven de
Spring Boot exclut aussi cette dépendance du JAR exécutable
([documentation Spring Boot](https://docs.spring.io/spring-boot/maven-plugin/packaging.html)).
Le profil `dev` n'est pas actif.

## Installer une version

Après avoir sauvegardé la base, remplacer `0.1.0` par la version livrée souhaitée
dans cette commande unique :

```bash
CROUSTYQUIZZ_VERSION=0.1.0 docker compose -f compose.prod.yaml pull && CROUSTYQUIZZ_VERSION=0.1.0 docker compose -f compose.prod.yaml up -d
```

La variable est répétée pour que les deux commandes utilisent la même image.
Après succès, reporter cette version dans `.env` pour les prochaines commandes.
Sans version définie, Compose utilise `main`. Pour une démonstration, choisir une
version numérotée afin de pouvoir la retrouver.

Vérifier le démarrage, car `up -d` ne garantit pas encore une réponse HTTP :

```bash
docker compose -f compose.prod.yaml ps
docker compose -f compose.prod.yaml logs --tail=100 app
curl -fsS http://127.0.0.1:8080/
```

La page doit contenir `<title>CroustyQuizz</title>`. L'application écoute seulement
sur `127.0.0.1:8080` côté serveur. Aucun port PostgreSQL n'est publié.

En production, Flyway garde `out-of-order=false`. Si une migration ancienne manque
sur une base déjà utilisée, le démarrage peut être refusé. Conserver les logs et
faire corriger la séquence de migrations avant de poursuivre. Ne pas activer le
profil `dev` pour contourner ce refus.

## Exposer et vérifier HTTPS

Tibxla exécute sur le serveur, après la vérification locale :

```bash
tailscale funnel --bg 8080
tailscale funnel status
```

Le mode `--bg` conserve l'exposition en arrière-plan. Funnel termine HTTPS et
relaie les requêtes vers le port local 8080. Relever l'adresse publique affichée.
Les Joueurs n'ont pas besoin d'installer Tailscale.

Sur un téléphone, couper le Wi-Fi et ouvrir cette adresse en 4G, sans connexion
Tailscale sur le téléphone. Vérifier le certificat HTTPS et la page d'accueil.
Quand le parcours métier est disponible, créer une Soirée depuis la Console
d'animation, ouvrir sa Manette via le QR code et vérifier une mise à jour sur
l'Écran de salle. Cette vérification extérieure reste à faire sur le serveur ;
un essai local seul ne valide pas l'acceptation de l'issue #4.

## Sauvegarder et restaurer la base

Avant chaque changement de version, créer une sauvegarde avec `pg_dump` au
format personnalisé. Utiliser un nom de fichier différent à chaque sauvegarde :

```bash
mkdir -p ../sauvegardes-croustyquizz
chmod 700 ../sauvegardes-croustyquizz
docker compose -f compose.prod.yaml exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > ../sauvegardes-croustyquizz/avant-livraison.dump
```

Vérifier que la commande réussit et que le fichier n'est pas vide. Conserver une
copie hors du serveur, avec un accès limité. Le dossier de sauvegardes est à côté
du clone Git pour éviter de le versionner.

Pour restaurer la base, arrêter l'application afin de suspendre les écritures.
La restauration remplace le contenu de la base par celui de la sauvegarde :

```bash
docker compose -f compose.prod.yaml stop app
docker compose -f compose.prod.yaml exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --single-transaction --exit-on-error' < ../sauvegardes-croustyquizz/avant-livraison.dump
# Continuer seulement si pg_restore a réussi et si la version dans .env convient.
docker compose -f compose.prod.yaml up -d app
```

Ne jamais supprimer le volume pour une mise à jour. Un `down -v` effacerait la base.

## Revenir à la version précédente

Reprendre la commande d'installation avec le numéro précédent dans les deux
affectations de `CROUSTYQUIZZ_VERSION`, puis reporter ce numéro dans `.env` et
refaire les vérifications HTTP. Par exemple, pour revenir de `0.2.0` à `0.1.0`,
utiliser la commande de la section « Installer une version ».

Une ancienne image ne remet pas la base dans son ancien état. Si les migrations
de la nouvelle version sont incompatibles avec l'ancienne, arrêter l'application,
restaurer la sauvegarde prise avant la livraison, puis relancer l'ancienne version.
Les données saisies depuis la sauvegarde seront perdues.

## Plan de secours : portable en partage de connexion

Avant la démonstration, préparer un portable x86_64 avec Docker, Compose et
Tailscale, les fichiers du dépôt et son propre `.env`. Télécharger la même image
numérotée et `postgres:18` à l'avance avec la commande `pull`. Préparer une copie
de la sauvegarde si les données de démonstration doivent être reprises.

Si le serveur ou son accès Internet tombe, connecter le portable au partage de
connexion d'un téléphone. Lancer Compose sur le portable avec la version prévue,
restaurer la sauvegarde si nécessaire, puis vérifier la réponse HTTP locale.
Activer Funnel avec la même commande sur ce portable. Son adresse publique sera
différente : transmettre la nouvelle adresse aux Joueurs et régénérer les QR codes
utilisés pour accéder aux Manettes. Refaire le test avec un autre téléphone en
4G, Wi-Fi coupé, avant de reprendre la Soirée.

Sans sauvegarde, la base du portable est vierge. Ce secours ne conserve pas les
connexions ni la Soirée en cours : l'Animateur doit préparer une nouvelle Soirée.
Essayer ce parcours avant le jour de la démonstration.
