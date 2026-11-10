# Contenu rédigé par l'Assistant de l'Animateur, via un serveur MCP

CroustyQuizz n'appelle aucun modèle de langage. Pour préparer une Soirée ou obtenir des Questions en français, l'Animateur passe par son propre Assistant (Claude, ChatGPT…), auquel CroustyQuizz expose un serveur MCP authentifié par une Clé d'Assistant propre au Lieu. L'Assistant rédige, CroustyQuizz vérifie et range avec les mêmes règles que la Console. On évite ainsi une clé d'API payante, un coût par requête et des Questions générées sans relecture, tout en réglant le manque de banque de Questions en français (Open Trivia DB est en anglais uniquement).

## Options écartées

- **Appeler un modèle de langage depuis l'application** pour générer les Questions : coût par appel, clé d'API à protéger, dépendance à un fournisseur, et Questions publiées sans relecture humaine.
- **Une API REST documentée seule** : utilisable par un script, mais pas par un Assistant sans travail d'intégration de l'Animateur. Le serveur MCP s'appuie sur les mêmes services et ne duplique aucune règle.

## Conséquences

- Les Questions rédigées par un Assistant portent la source « Assistant » et restent modifiables dans la Console avant la Soirée.
- Une Clé d'Assistant ne donne accès qu'aux Soirées de son Lieu, et jamais à une Soirée en cours.
