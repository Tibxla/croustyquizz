# Doc du domaine

Comment les skills d'ingénierie doivent lire la documentation du domaine de ce dépôt avant d'explorer le code.

## Avant d'explorer, lire

- **`CONTEXT.md`** à la racine : le glossaire du projet (Soirée, Manche, Manette…).
- **`docs/adr/`** : les ADR qui touchent la zone sur laquelle on s'apprête à travailler.

Si l'un de ces fichiers n'existe pas, **continuer sans rien dire**. Ne pas signaler son absence ni proposer de le créer d'avance : le skill producteur (`/grill-with-docs`) les crée au fil de l'eau, quand un terme ou une décision est tranché.

## Structure

Dépôt à contexte unique :

```
/
├── CONTEXT.md
├── docs/adr/
│   └── 0001-front-sans-chaine-de-build.md
└── src/
```

## Employer le vocabulaire du glossaire

Quand une production nomme un concept du domaine (titre d'issue, proposition de refactoring, hypothèse, nom de test), utiliser le terme tel que défini dans `CONTEXT.md`. Ne pas glisser vers les synonymes que le glossaire range sous « _Éviter_ ».

Si le concept voulu n'est pas encore dans le glossaire, c'est un signal : soit on invente un vocabulaire que le projet n'emploie pas (à reconsidérer), soit il manque vraiment un terme (à noter pour `/grill-with-docs`).

## Signaler les contradictions avec un ADR

Si une production contredit un ADR existant, le dire explicitement plutôt que de passer outre en silence :

> _Contredit l'ADR 0001 (front sans chaîne de build), mais mérite d'être rouvert parce que…_
