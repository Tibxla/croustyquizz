# Déployer la démo

L'application et PostgreSQL tournent sur le serveur de Tibxla avec Docker Compose,
**seulement pendant une séance de test ou une démo** : `tools/demo.sh` les démarre
et les arrête, rien ne redémarre seul avec la machine. Pendant une démo, Tailscale
Funnel donne une adresse HTTPS publique aux Manettes, à la Console d'animation et à
l'Écran de salle. PostgreSQL ne publie aucun port.

## Trois environnements

| Environnement | Où | Version | Base |
|---|---|---|---|
| Développement | la machine de chacun, `./mvnw spring-boot:run` | la branche de travail | PostgreSQL de `compose.yaml` |
| Recette | Render, https://croustyquizz-recette.onrender.com | `develop`, redéployée après chaque fusion | Neon |
| Démo | serveur de Tibxla, `tools/demo.sh`, à la demande | `main` ou `vX.Y.Z` | PostgreSQL de `compose.prod.yaml` |

Aucun ne partage de base avec un autre. La suite de ce document décrit la démo ; la recette a sa section, plus bas.

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

Démarrer ensuite avec `tools/demo.sh demarrer` (section suivante). L'application
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

## Démarrer et arrêter une séance

Depuis le dossier du dépôt sur le serveur, avec la version choisie dans `.env`
(`CROUSTYQUIZZ_VERSION`, sans `v`) :

```bash
tools/demo.sh demarrer   # télécharge l'image si besoin, démarre, attend la réponse HTTP
tools/demo.sh etat       # conteneurs et exposition en cours
tools/demo.sh arreter    # ferme l'adresse publique, arrête les conteneurs
```

`demarrer` affiche `CroustyQuizz répond sur http://127.0.0.1:8080/` quand la page
d'accueil répond. Sans réseau, il démarre avec l'image déjà présente sur la machine.
`arreter` garde la base dans son volume : la séance suivante la retrouve. Les
conteneurs sont relancés après un plantage pendant la séance (trois fois au plus),
jamais au démarrage de la machine.

Pour les tests de l'équipe sans exposition à Internet, partager le port local sur le
réseau Tailscale seulement (les camarades invités sur le réseau de Tibxla) :
`tailscale serve --bg --https=8443 8080`, puis `tailscale serve --https=8443 off`.

Pour changer de version : sauvegarder la base, modifier `CROUSTYQUIZZ_VERSION` dans
`.env`, puis `tools/demo.sh arreter` et `tools/demo.sh demarrer`. Choisir une version
numérotée pour une démonstration, afin de pouvoir la retrouver.

En production, Flyway garde `out-of-order=false`. Si une migration ancienne manque
sur une base déjà utilisée, le démarrage peut être refusé. Conserver les logs
(`docker compose -f compose.prod.yaml logs app`) et faire corriger la séquence de
migrations avant de poursuivre. Ne pas activer le profil `dev` pour contourner ce refus.

## Exposer en HTTPS le temps d'une démo

L'adresse publique s'ouvre seulement pendant une démo, et seulement quand ces
conditions sont remplies (issue #4) :

1. la Console d'animation est protégée par un compte (#5) ;
2. les routes `/api/test/` du prototype temps réel sont coupées en profil `prod` ;
3. le Buzz, les Réponses et l'accès à une Soirée ont une limite de requêtes.

Tant qu'elles ne le sont pas, `tools/demo.sh demarrer --public` refuse d'ouvrir
l'adresse. Une fois vérifiées, écrire `CROUSTYQUIZZ_PUBLIC=autorise` dans `.env`, puis :

```bash
tools/demo.sh demarrer --public   # tailscale funnel --bg --https=443 8080
tools/demo.sh arreter             # à la fin de la démo, toujours
```

Funnel termine le HTTPS et relaie les requêtes vers le port local 8080 ; l'adresse
IP du serveur n'est pas publiée et aucun port n'est ouvert sur la box. `arreter`
ferme uniquement l'entrée HTTPS de CroustyQuizz (`--https=443 off`) : les autres
services de `tailscale serve` restent en place. Ne jamais utiliser
`tailscale funnel reset`, qui les effacerait.

Sur un téléphone, couper le Wi-Fi et ouvrir l'adresse en 4G, sans Tailscale sur le
téléphone. Vérifier le certificat HTTPS et la page d'accueil. Quand le parcours
métier est disponible, créer une Soirée depuis la Console d'animation, ouvrir sa
Manette par le QR code et vérifier une mise à jour sur l'Écran de salle. Un essai
local seul ne valide pas l'acceptation de l'issue #4.

## Sécurité du serveur

`compose.prod.yaml` limite ce qu'une faille de l'application pourrait faire sur la
machine de Tibxla :

- l'application tourne sous un utilisateur sans droits (UID 10001), sur un système de
  fichiers en lecture seule, sans aucune capacité Linux ni gain de privilèges ;
- elle n'a accès à aucun dossier de la machine ; seule la base a un volume, le sien ;
- mémoire, processeur et nombre de processus sont plafonnés (1 Go et 2 processeurs
  pour l'application, 512 Mo pour la base) : une séance ne peut pas étouffer les
  autres services ;
- la base est sur un réseau interne sans accès sortant, joignable par l'application seule ;
- l'application n'écoute que sur `127.0.0.1` ; les journaux sont limités à 30 Mo par
  conteneur.

Vérifié le 10/11 sur une installation d'essai : écriture refusée dans `/app`, UID
10001, base sans accès à Internet. Le secret de la base reste dans `.env`
(`chmod 600`), jamais dans Git.


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

Remettre le numéro précédent dans `CROUSTYQUIZZ_VERSION` (`.env`), puis
`tools/demo.sh arreter` et `tools/demo.sh demarrer`, qui attend de nouveau la
réponse HTTP. Par exemple, pour revenir de `0.2.0` à `0.1.0`, écrire `0.1.0`.

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
Ouvrir l'adresse publique avec `tools/demo.sh demarrer --public` sur ce portable. Son adresse publique sera
différente : transmettre la nouvelle adresse aux Joueurs et régénérer les QR codes
utilisés pour accéder aux Manettes. Refaire le test avec un autre téléphone en
4G, Wi-Fi coupé, avant de reprendre la Soirée.

Sans sauvegarde, la base du portable est vierge. Ce secours ne conserve pas les
connexions ni la Soirée en cours : l'Animateur doit préparer une nouvelle Soirée.
Essayer ce parcours avant le jour de la démonstration.

## Recette sur Render et Neon

La recette fait tourner `develop` hors du serveur de Tibxla, pour que l'équipe teste
depuis ses téléphones sans rien installer : l'application chez Render (offre gratuite,
Francfort), la base chez Neon (PostgreSQL 18, Francfort). Les deux comptes appartiennent
à l'organisation « Croustyquizz » ; y inviter les camarades pour qu'ils voient les journaux.

- **Adresse** : https://croustyquizz-recette.onrender.com, publique. N'y mettre que des
  données fictives.
- **Déploiement** : après chaque fusion dans `develop`, une fois le build et les contrats
  verts, le job « Déployer la recette » de la CI appelle le *deploy hook* de Render (secret
  GitHub `RENDER_DEPLOY_HOOK_RECETTE`) pour ce commit précis ; Render construit l'image
  depuis le `Dockerfile`. Rien à lancer à la main. Render n'a aucun accès au compte GitHub :
  le déploiement automatique de Render est coupé, seule la CI déclenche.
- **Veille** : le service s'endort après 15 minutes sans requête HTTP ni message WebSocket ;
  la visite suivante le réveille en une à deux minutes (démarrage de Spring Boot mesuré à
  86 s sur l'instance gratuite). Pendant une séance, les battements
  STOMP des Manettes le gardent éveillé. La base Neon s'endort après 5 minutes sans connexion
  et se réveille en moins d'une seconde.
- **Limites gratuites** : 512 Mo de mémoire et un CPU partagé, 750 heures par mois chez
  Render ; Render peut redémarrer le service à tout moment, et la dernière vue publiée
  repart alors de la capture de repli (ADR 0004). Neon : 1 Go et 100 heures de calcul par mois.
- **Configuration** : variables du service Render, jamais dans le dépôt :
  `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`
  (`jdbc:postgresql://<hôte Neon>/croustyquizz?sslmode=require`, connexion directe et non
  le « pooler », pour Flyway), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`,
  `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+UseSerialGC`. Render fournit `PORT`.
- **Repartir d'une base vide** : dans Neon, réinitialiser la branche `main` du projet
  `croustyquizz-recette`, puis redéployer ; Flyway recrée le schéma au démarrage.

## Changer d'hébergeur

Rien dans l'image ne dépend du serveur de Tibxla ni de Tailscale : `compose.prod.yaml`,
`tools/demo.sh` et Funnel ne sont qu'une façon de la lancer. Un hébergeur de
conteneurs (Render, Koyeb, Cloud Run…) peut prendre la même image s'il fournit :

| Besoin | Valeur |
|---|---|
| Image | `ghcr.io/tibxla/croustyquizz:<version>`, publiée à chaque tag |
| Port | `PORT` si l'hébergeur l'impose, 8080 sinon |
| Configuration | `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| Base | PostgreSQL 18 ; Flyway crée le schéma au premier démarrage |
| Réseau | HTTPS et WebSocket (STOMP) pendant toute la Soirée |
| Instances | une seule, qui reste éveillée pendant la Soirée : les Révisions et la dernière Vue synchronisée sont en mémoire (ADR 0004) |

Vercel et Netlify ne conviennent pas : ils ne font tourner ni conteneur ni JVM, et
ne gardent pas de connexion WebSocket ouverte. Pour migrer les données, utiliser la
sauvegarde `pg_dump` ci-dessus et la restaurer sur la nouvelle base.
