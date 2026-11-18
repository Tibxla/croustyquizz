# Gestionnaire d'issues : GitHub

Les issues et les PRD de ce dépôt sont des issues GitHub de `Tibxla/croustyquizz`. Toutes les opérations passent par la CLI `gh`, qui déduit le dépôt de `git remote -v` quand elle est lancée dans un clone.

Les commandes ci-dessous marchent avec `gh` 2.46 (version d'Ubuntu). Les options `--parent`, `--blocked-by` et `--add-sub-issue` n'existent qu'à partir de `gh` 2.94 : avec une version plus ancienne, passer par `gh api` comme indiqué.

## Conventions

- **Créer une issue** : `gh issue create --title "..." --body "..."`. Utiliser un heredoc pour un corps sur plusieurs lignes.
- **Lire une issue** : `gh issue view <numéro> --json number,title,body,labels,comments`.
- **Lister les issues** : `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`, avec les filtres `--label` et `--state` adaptés.
- **Sous-issue** : `gh api --method POST repos/Tibxla/croustyquizz/issues/<parent>/sub_issues -F sub_issue_id=<id de l'enfant>`, où l'id est l'**id de base** (`gh api repos/Tibxla/croustyquizz/issues/<n> --jq .id`), pas le `#numéro`. À défaut, écrire `Part of #<parent>` en tête du corps de l'enfant.
- **Blocage** : les dépendances natives de GitHub, visibles dans l'interface. `gh api --method POST repos/Tibxla/croustyquizz/issues/<enfant>/dependencies/blocked_by -F issue_id=<id de base du bloqueur>`. À défaut, une ligne `Blocked by: #<n>, #<n>` en tête du corps. Une issue est débloquée quand tous ses bloqueurs sont fermés.
- **Compter les bloqueurs ouverts** : `gh api repos/Tibxla/croustyquizz/issues/<n> --jq .issue_dependencies_summary.blocked_by` (0 = on peut commencer).
- **Commenter** : `gh issue comment <numéro> --body "..."`
- **Ajouter ou retirer un label** : `gh issue edit <numéro> --add-label "..."` / `--remove-label "..."`
- **Fermer** : publier l'explication avec `gh issue comment <numéro> --body "..."`, puis `gh issue close <numéro>`. Sur une issue déjà fermée, `gh issue close --comment` perd le commentaire sans erreur.

Ne créer d'issues que dans `Tibxla/croustyquizz`, jamais dans `mattpocock/skills`, le dépôt d'où viennent les skills.

## Pull requests comme surface de tri

**PR comme surface de demande : non.** _(Passer à `oui` si des PR externes devaient être traitées comme des demandes ; `/triage` lit ce drapeau.)_

GitHub partage la numérotation entre issues et PR : un `#42` nu peut être l'un ou l'autre. Lever le doute avec `gh pr view 42`, puis `gh issue view 42`.

## Quand un skill dit « publier dans le gestionnaire d'issues »

Créer une issue GitHub.

## Quand un skill dit « récupérer le ticket concerné »

Le lire comme dans **Lire une issue** ci-dessus.

## Opérations de `/wayfinder`

La **carte** est une issue unique labellisée `wayfinder:map` (Notes, Décisions prises, Brouillard) ; ses **tickets** sont des sous-issues labellisées `wayfinder:<type>` (`research`, `prototype`, `grilling`, `task`). Créer ces labels au premier usage seulement.

- **Frontière** : les sous-issues ouvertes de la carte, sans bloqueur ouvert ni personne assignée ; la première dans l'ordre de la carte l'emporte.
- **Prendre un ticket** : `gh issue edit <n> --add-assignee @me`, première écriture de la session.
- **Résoudre** : commenter la réponse, fermer le ticket, puis ajouter un résumé et le lien dans les Décisions prises de la carte.
