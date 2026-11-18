# Doc du domaine

Comment les skills d'ingénierie lisent la documentation du domaine de ce dépôt avant d'explorer le code.

## Avant d'explorer, lire

- **`GLOSSARY.md`** à la racine : le glossaire du projet (Soirée, Manche, Manette…).
- **`docs/adr/`** : les ADR qui touchent la zone sur laquelle on s'apprête à travailler.

Si l'un de ces fichiers n'existe pas, **continuer sans rien dire**. Le skill `/domain-modeling` (appelé aussi par `/grill-with-docs` et `/improve-codebase-architecture`) les crée au fil de l'eau, quand un terme ou une décision est tranché.

## Structure

Dépôt à contexte unique :

```
/
├── GLOSSARY.md
├── docs/adr/
│   ├── 0001-front-sans-chaine-de-build.md
│   └── …
└── src/
```

`doc/` (au singulier) ne contient que les dossiers de rendu demandés par le cours : ce n'est pas de la documentation du domaine.

## Employer le vocabulaire du glossaire

Quand une production nomme un concept du domaine (titre d'issue, proposition de refactoring, hypothèse, nom de test), utiliser le terme tel que défini dans `GLOSSARY.md`, et non les synonymes qu'il range sous « _Éviter_ ».

Si le concept voulu n'est pas encore dans le glossaire, c'est un signal : soit on invente un vocabulaire que le projet n'emploie pas (à reconsidérer), soit il manque vraiment un terme (à noter pour `/domain-modeling`).

## Signaler les contradictions avec un ADR

Si une production contredit un ADR existant, le dire explicitement plutôt que de passer outre en silence :

> _Contredit l'ADR 0001 (front sans chaîne de build), mais mérite d'être rouvert parce que…_
