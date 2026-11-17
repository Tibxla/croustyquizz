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

La recette de `develop` est sur https://croustyquizz-recette.onrender.com : la CI la redéploie après chaque fusion verte. La production (https://croustyquizz-prod.onrender.com, à partir de `v0.1.0`) reçoit chaque livraison taguée. Chacune a sa base Neon (`docs/deploiement.md`, « Recette et production sur Render et Neon »).
| `docker compose down -v` | efface la base locale pour repartir de zéro |
| `git config core.hooksPath .githooks` | une fois par clone : refuse les messages de commit hors format |

Sous Windows, dans PowerShell : `.\mvnw.cmd` au lieu de `./mvnw`.

## Coder un Mode de jeu

Le contrat est dans `manche` : implémenter `DeroulementDeManche` et une `FabriqueDeDeroulement<R, E>` (réglages typés, création, restauration avec `EtatManche`). Déclarer un service de mode qui étend `ServiceDeManche<R, E>`, sa `PersistanceDuMode<E>` JPA et un `FournisseurDeBloc` dans le package du mode. Les tables du mode sont migrées par Flyway ; `manche_deroulement` conserve le cycle commun et `manche_envoi` les reçus idempotents.

Toute commande passe par le service : préparation externe hors verrou, verrou de Soirée, transaction, appel du déroulement, persistance, assemblage des vues, puis publication après commit et avant libération du verrou (ADR 0006). Un refus ne change jamais l'`instantane()` immuable. Le déroulement rend un `ResultatDeCommande` avec l'accusé HTTP, les destinations modifiées et un type d'événement du contrat ; il n'a plus de diffuseur. Si la transaction échoue, le service évince le déroulement pour le restaurer à la prochaine commande.

Le temps passe exclusivement par l'`Horloge` du `ContexteDeManche` (jamais `Instant.now()` ni de thread). Ses callbacks repassent par le service ; la fabrique précise `typeEvenementMinuteur` pour ses phases. `reglagesCommuns` extrait les réglages du contexte depuis les réglages typés du mode. Les fournisseurs produisent des projections sans secrets pour chaque destination sous le même verrou ; seul le socle compose et publie la vue complète. La classe de test du mode étend `ContratDeroulementDeManche` (modèle : `DeroulementFacticeTest`), notamment pour les refus sans mutation et après la fin.

Côté navigateur, ne pas créer de page par mode : les coquilles `manette.html`, `console.html` et `ecran.html` chargent `static/js/modes/<mode>/<ecran>.mjs`. Dossiers : `quiz`, `blindtest` (pour `BLIND_TEST`), `karaoke`, `freestyle`. Chaque module exporte `monter(zones, contexte)`, qui rend `{ afficher(vue, origine), demonter() }` :

- `zones.principale` sur les trois écrans ; `zones.laterale` aussi sur la Console et l'Écran de salle. Sur la salle, la principale est à gauche et la latérale en haut à droite : la coquille garde `.salle__scene`, le pied, le talon et les informations communes. Ne modifier que ses zones. Seule exception : un module peut ajouter dans `head` un unique `<link rel="stylesheet" data-mode="<mode>">` au montage et le retirer au démontage (`data-mode="karaoke"` pour le Karaoké).
- `afficher` reçoit la vue complète et `origine: { source: 'capture' | 'evenement', type }`. Une capture restaure sans rejouer d'animation ; seul un événement peut la déclencher. Les pages ne contiennent aucune règle de jeu. Un module ne reçoit jamais de bloc absent ou nul : quand le bloc de son mode manque ou vaut `null` (fin de Manche, pas de fournisseur), le gestionnaire le démonte, puis en remonte un neuf à la prochaine vue qui porte un bloc, même si le mode n'a pas changé.
- `contexte.envoyer(methode, chemin, corps, { entetes, signal, annoncer } = {})` authentifie toute requête HTTP, y compris `GET`, et rend `{ statut, corps }` pour tous les statuts HTTP ; une erreur réseau lève, comme une réponse 2xx dont le corps n'est pas du JSON. Hors 2xx, un corps illisible (page HTML d'un proxy) devient `{ detail: 'Le serveur ne répond pas correctement.' }`. Le module fournit `Idempotency-Key` dans `entetes` et le signal d'un `AbortController`. Pour une mutation, la coquille affiche l'envoi en cours puis le reçu ou le `detail` du `Probleme`, sans succès anticipé ; une panne réseau donne une confirmation inconnue, une annulation efface le bandeau. Un module qui affiche son propre statut près de l'action passe `annoncer: false` : la coquille se tait, et un seul `role="status"` annonce l'envoi. Un reçu ne remplace jamais la vue. Ne pas réessayer automatiquement une commande.
- `contexte.resynchroniser()` redemande une capture complète. `contexte.surEtatConnexion(rappel)` appelle immédiatement le rappel avec l'état courant (`synchronisation`, `synchronisee`, `coupure`), puis à chaque changement ; il rend la fonction de désabonnement. Désactiver les commandes tant que la vue n'est pas synchronisée.
- `contexte.acteur` contient `type`, `soireeId` et l'`identifiant` de démonstration ; `contexte.soireeId` identifie la Soirée. La session Animateur remplacera cette identité avec #5. L'identité du Joueur viendra de #10.
- `demonter()` annule ses requêtes, ses écouteurs, ses abonnements et ses animations, puis libère ses zones. Le changement de `modeEnCours` démonte l'ancien module avant de monter le suivant ; hors Manche, aucun module. Tester la logique pure par `node --test` dans `tools/ecrans/` (inclus dans `npm test --prefix tools/api`).

Les styles `.panneau`, `.recherche`, `.champ`, `.champ--erreur` et `.dialogue` sont communs dans `croustyquizz.css`. Pour ouvrir un `<dialog class="dialogue">`, utiliser `ouvrirDialogue` de `js/dialogue.mjs` (focus initial et retour au déclencheur). Le module factice `modes/factice/` affiche le bloc reçu sans règle de jeu ; voir `docs/ecrans.md` pour la recette.

## Propriétaires

Chaque package a un propriétaire, qui écrit et relit en premier ce qui s'y passe. Une PR qui touche le package d'un autre le dit dans sa description et demande sa relecture.

| Package | Propriétaire |
|---|---|
| `manche` (contrat commun), `tempsreel`, `passage` (code commun du Karaoké et du Freestyle), `karaoke`, pilotage automatique | Tibxla |
| `quiz`, `animateur`, classement général | Piyakabib |
| `soiree`, `blindtest`, déroulement manuel des Manches, format Équipe, `assistant` (serveur MCP) | nRayen |

## Skills d'agent

Les skills de [mattpocock/skills](https://github.com/mattpocock/skills) et d'[Impeccable](https://impeccable.style) sont partagés : Claude Code les reçoit en plugins par `.claude/settings.json`, Codex et Antigravity les lisent dans `.agents/skills/` (détails dans `CONTRIBUTING.md`). Les dossiers de `.agents/skills/` listés dans `.agents/skills/.copies` sont des copies générées : ils se mettent à jour par `tools/mettre-a-jour-skills.sh`, pas à la main. Repères :

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
