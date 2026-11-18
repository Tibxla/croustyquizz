# Contribuer à CroustyQuizz

Le cours évalue le process autant que le code : des push réguliers, une branche par feature, une pull request relue avant chaque fusion. Ces règles servent à ça.

## Branches

- `main` : ne reçoit qu'une fusion depuis `develop` à la fin de chaque jalon, suivie d'un tag (`v0.1.0` socle, `v0.2.0` modes de jeu, `v1.0.0` rendu final).
- `develop` : protégée. On n'y entre que par une pull request.
- Branches de travail, toujours créées depuis `develop` à jour :
  - `feature/<n° d'issue>-<slug>`, par exemple `feature/12-file-attente-karaoke`
  - `fix/<n° d'issue>-<slug>`
  - `docs/<n° d'issue>-<slug>` pour la documentation seule
  - `chore/<n° d'issue>-<slug>` pour l'outillage, la CI ou les dépendances

Une branche fusionnée est supprimée et ne sert plus. Une correction découverte après la fusion part d'une nouvelle branche `fix/<n° d'issue>-<slug>` depuis `develop`.

## Commits

Format [Conventional Commits](https://www.conventionalcommits.org/fr/) : type en anglais, description en français, à l'infinitif, sans point final. Le scope est le package touché.

```
feat(karaoke): ajouter la file d'attente des chanteurs
fix(soiree): refuser un code d'accès expiré
test(quiz): couvrir le calcul des points selon la vitesse
docs: décrire la procédure d'installation
```

Types : `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `ci`, `build`.

Le hook `.githooks/commit-msg` refuse un message hors format avant même le commit, et la CI refait la vérification sur chaque PR. Pour activer le hook, une fois par clone :

```bash
git config core.hooksPath .githooks
```

Un commit = un changement qui se comprend seul. Pousser sa branche au moins à chaque séance de travail, même inachevée.

## Pull requests

1. Chaque PR répond à une issue et la ferme : écrire `Closes #12` dans la description. Une PR complémentaire d'une issue déjà fermée écrit `Refs #12`.
   Seule exception : les PR de Dependabot (`build(deps): …`, `ci(deps): …`) : mises à jour de versions regroupées une fois par mois et par écosystème, correctifs de sécurité dès qu'une faille est connue. N'importe quel membre les relit ; si une mise à jour casse le build, ouvrir une issue plutôt que de forcer la fusion.
2. La description suit le gabarit `.github/pull_request_template.md`.
3. Avant d'ouvrir la PR, remettre sa branche à jour sur `develop` par un rebase (recette ci-dessous) et nettoyer ses propres commits. Pendant la relecture, mettre à jour avec « Update with rebase » dans GitHub, ou la même recette en local, pour garder l'historique sans commits de fusion de `develop`.
4. Une approbation d'un autre membre et une CI verte sont obligatoires.
5. Fusion par **merge commit** (pas de squash, pas de rebase dans GitHub), puis suppression de la branche.

### Relecture en tourniquet

| Auteur de la PR | Relecteur |
|---|---|
| Piyakabib | nRayen |
| nRayen | Tibxla |
| Tibxla | Piyakabib |

### Recette du rebase

```bash
git switch develop
git pull
git switch feature/12-file-attente-karaoke
git rebase develop
# en cas de conflit : corriger le fichier, puis
git add <fichier>
git rebase --continue
# une fois le rebase fini
git push --force-with-lease
```

`--force-with-lease` refuse d'écraser la branche distante si quelqu'un d'autre y a poussé entre-temps. Ne jamais faire de rebase sur `develop` ni sur `main`.

## Base de données

Le schéma n'évolue que par des migrations Flyway dans `src/main/resources/db/migration/`, nommées avec l'horodatage de création pour que deux branches ne prennent jamais le même numéro :

```
V202610011430__karaoke_file_attente.sql
```

Ne jamais modifier une migration déjà fusionnée dans `develop` : en écrire une nouvelle.

Le profil `dev`, actif par défaut sur vos machines, active `spring.flyway.out-of-order=true`. Sans ça, après la fusion d'une migration plus ancienne que la dernière appliquée sur ta base locale, Flyway refuse de démarrer (`Detected resolved migration not applied to database`).

Si Flyway refuse quand même de démarrer (migration modifiée après coup, base locale abîmée), repartir d'une base vide : `docker compose down -v`, puis relancer l'application.

## Skills d'agent

Toute l'équipe utilise les mêmes skills : [mattpocock/skills](https://github.com/mattpocock/skills) (`tdd`, `diagnosing-bugs`, `grill-with-docs`, `code-review`…) et [Impeccable](https://impeccable.style) pour les interfaces. `AGENTS.md` dit lequel utiliser à quel moment.

- **Claude Code** : à l'ouverture du dépôt, Claude Code demande d'approuver les deux marketplaces déclarées dans `.claude/settings.json`, puis installe les plugins. Ceux qui les ont déjà en global depuis les mêmes marketplaces (`mattpocock`, `impeccable`) gardent les mêmes plugins, chargés une seule fois.
- **Codex et Antigravity CLI** : rien à installer. Les skills sont versionnés dans `.agents/skills/`, le dossier que ces deux outils lisent dans un dépôt. Codex demande d'approuver le dossier au premier lancement ; `.codex/config.toml` désactive alors, dans ce dépôt seulement, les plugins mattpocock et Impeccable de ceux qui les ont en global.

Les copies de `.agents/skills/` ne se modifient pas à la main. Pour les mettre à jour, lancer `tools/mettre-a-jour-skills.sh` (Git Bash sous Windows), relire le diff et le proposer dans une PR ; `.agents/PROVENANCE.md` indique les commits copiés et les licences. Un skill propre au projet peut s'ajouter dans `.agents/skills/` : le script ne touche qu'aux dossiers listés dans `.agents/skills/.copies`.

La configuration propre au dépôt (`docs/agents/`, `GLOSSARY.md`) est déjà versionnée : inutile de relancer `setup-matt-pocock-skills`.
