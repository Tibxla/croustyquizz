---
status: accepted
---

# Choisir la source de Questions en français pour le mode Quiz

Les questions du mode Quiz doivent être en français, disponibles en quatre propositions par question, avec des niveaux de difficulté et des catégories thématiques. L'objectif est de choisir une source exploitable dès maintenant, sans dépendance externe à l'exécution, et compatible avec la licence du projet.

## Sources comparées

Quatre candidats ont été étudiés et testés en novembre 2026 :

| Critère | OpenQuizzDB (fichiers) | Open Trivia DB (API) | OpenTriviaQA-FR (GitHub) | QuizAPI.io |
|---|---|---|---|---|
| **Langue** | 🇫🇷 Français natif | 🇬🇧 Anglais uniquement | 🇫🇷 Français (traduction) | 🇬🇧 Anglais uniquement |
| **Licence** | CC BY-SA 4.0 | CC BY-SA 4.0 | CC BY-SA 4.0 | Propriétaire |
| **Gratuit** | ✅ Oui | ✅ Oui | ✅ Oui | ⚠️ Freemium |
| **API REST au runtime** | ❌ Désactivée définitivement | ✅ Oui (testé : 10 questions OK) | ❌ Non | ✅ Oui |
| **Volume** | ~287 000 questions | ~21 000 questions | ~5 000 questions | Milliers (technique) |
| **Catégories** | 25+ (cinéma, musique, histoire…) | 24 (anglais) | ~15 | Informatique/DevOps |
| **Niveaux de difficulté** | ✅ 3 (débutant/confirmé/expert) | ✅ 3 (easy/medium/hard) | ❌ Aucun | ✅ 3 |
| **4 propositions exactes** | ✅ Oui (champ `propositions[]`) | ✅ Oui (testé, type=multiple) | ✅ Oui | ✅ Oui |
| **Adapté pub quiz FR** | ✅ Excellent | ❌ Non (anglais) | ⚠️ Moyen | ❌ Non |

### Résultats des tests réels

**Open Trivia DB** : appel `GET https://opentdb.com/api.php?amount=10&type=multiple` testé avec succès. 10 questions retournées, toutes avec 4 propositions, 3 niveaux, 24 catégories. Mais **100 % en anglais** : pas de paramètre `lang`, pas de questions françaises disponibles. Écarté pour cette raison.

**OpenQuizzDB** : tous les endpoints (`/data/json/*.json`, `/api/`) renvoient la page d'accueil HTML depuis 2021. L'API est définitivement désactivée. Les fichiers JSON restent téléchargeables manuellement sur le site, quiz par quiz, sous la structure suivante :

```json
{
  "quizz_fichiers": {
    "fr": {
      "debutant": [
        {
          "question": "Quel est le vrai nom de Lady Gaga ?",
          "propositions": [
            "Stefani Joanne Germanotta",
            "Alecia Beth Moore",
            "Robyn Rihanna Fenty",
            "Melissa Viviane Jefferson"
          ],
          "reponse": "Stefani Joanne Germanotta",
          "anecdote": "Lady Gaga est née le 28 mars 1986 à New York."
        }
      ]
    }
  }
}
```

Cette structure mappe directement sur `Question` et `Proposition` du modèle CroustyQuizz.

**OpenTriviaQA-FR** : dataset statique de ~5 000 questions traduites, sans niveaux de difficulté, format texte non standard. Volume et qualité insuffisants comparés à OpenQuizzDB.

**QuizAPI.io** : anglais uniquement, focus informatique/DevOps, inadapté.

## Décision

**OpenQuizzDB est retenu comme source primaire** de questions, en mode import statique.

Les packs JSON sont téléchargés manuellement à partir de `openquizzdb.org`, convertis en SQL par un script dédié, puis versionnés comme migration Flyway dans `src/main/resources/db/migration/`. Les questions ne sont pas consommées via API au runtime : elles vivent dans la base PostgreSQL du projet, comme n'importe quelle autre donnée.

Cette approche est retenue parce que :

1. **Volume suffisant** : 287 000+ questions couvrent plusieurs semestres de soirées Quiz.
2. **Français natif, validé par la communauté** : qualité supérieure à toute traduction automatique.
3. **Structure native compatible** : `propositions[]` à 4 éléments, `reponse`, `anecdote`, trois niveaux de difficulté, 25+ catégories thématiques — tout ce qu'attend le modèle `Question` de CroustyQuizz.
4. **Zéro dépendance externe au runtime** : pas de clé API, pas de quota, pas de panne d'un service tiers pendant une soirée.
5. **Licence CC BY-SA 4.0** : libre pour un projet non commercial avec attribution. La mention « Questions issues d'OpenQuizzDB (CC BY-SA 4.0) » suffit dans la documentation ou l'interface.

## Conséquences

### Modèle de données

L'interface `SourceDeQuestions` déclarée dans le diagramme de classes des services est **conservée telle quelle**. Elle est implémentée par un `DepotDeQuestions` JPA qui tire aléatoirement des questions depuis la base selon les critères (catégories, difficulté, nombre). Aucune modification de l'interface n'est nécessaire.

Le mapping des niveaux OpenQuizzDB vers les `Difficulte` de CroustyQuizz est le suivant :

| OpenQuizzDB | CroustyQuizz `Difficulte` |
|---|---|
| `debutant` | `FACILE` |
| `confirme` | MOYENNE |
| `expert` | `DIFFICILE` |

### Script d'import

Un script Python `tools/importer-questions-quiz.py` est créé pour :
1. Lire les fichiers JSON téléchargés depuis OpenQuizzDB.
2. Générer une migration Flyway `V<horodatage>__import_questions_quiz.sql`.
3. Insérer dans la table `question` (package `quiz`) : `enonce`, `categorie`, `difficulte`, et les 4 propositions dans une table `proposition`.

Les questions importées portent `source = BANQUE` (valeur de l'énumération `SourceQuestion`).

### Attribution

Toute interface ou documentation exposée aux utilisateurs doit mentionner : « Questions issues d'[OpenQuizzDB](https://www.openquizzdb.org) (Philippe Bresoux), licence CC BY-SA 4.0 ».

### Open Trivia DB comme référence de test uniquement

Open Trivia DB (OpenTDB) peut être utilisé pour les tests automatisés qui ont besoin d'un corpus de questions sans contrainte de langue (ex. : tester le service de tirage de questions indépendamment du contenu). Il n'est pas utilisé en production.
