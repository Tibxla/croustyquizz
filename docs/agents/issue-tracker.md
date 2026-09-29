# Gestionnaire d'issues : GitHub

Les issues et les PRD de ce dépôt sont des issues GitHub de `Tibxla/croustyquizz`. Toutes les opérations passent par la CLI `gh`.

## Conventions

- **Créer une issue** : `gh issue create --title "..." --body "..."`. Utiliser un heredoc pour un corps sur plusieurs lignes.
- **Lire une issue** : `gh issue view <numéro> --comments`, en filtrant les commentaires avec `jq` et en récupérant aussi les labels.
- **Lister les issues** : `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`, avec les filtres `--label` et `--state` adaptés.
- **Commenter une issue** : `gh issue comment <numéro> --body "..."`
- **Ajouter ou retirer un label** : `gh issue edit <numéro> --add-label "..."` / `--remove-label "..."`
- **Fermer** : `gh issue close <numéro> --comment "..."`

Le dépôt se déduit de `git remote -v` : `gh` le fait tout seul quand il est lancé dans un clone.

## Quand un skill dit « publier dans le gestionnaire d'issues »

Créer une issue GitHub.

## Quand un skill dit « récupérer le ticket concerné »

Lancer `gh issue view <numéro> --comments`.
