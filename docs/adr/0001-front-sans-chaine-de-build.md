# Front sans chaîne de build

Les trois interfaces (Manette, Écran de salle, Console d'animation) sont des pages HTML et JavaScript servies directement par Spring Boot, sans npm ni bundler. Une bibliothèque front reste permise si elle se charge par une simple balise `<script>` (WebJar Maven ou fichier copié dans `static/`). Deux membres de l'équipe débutent : un second écosystème à apprendre en plus de Spring leur aurait coûté plusieurs semaines, pour une partie que l'évaluation du cours considère comme secondaire. Un seul `mvn spring-boot:run` lance donc tout le projet.

## Options écartées

- **React ou Vue avec Vite** : écarté pour le coût d'apprentissage et la seconde commande de build. Le Karaoké en aurait profité (paroles synchronisées), c'est le prix assumé.
