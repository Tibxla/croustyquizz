# Coquilles des trois écrans (#80)

Ouvrir `/test/identite.html?soiree=<UUID>` en profil `dev` ou `recette`. Pour ouvrir deux Manettes, charger la page dans deux onglets avec le même UUID de Soirée, choisir le rôle Joueur dans chacun, puis sélectionner « Joueur 1 » dans le premier et « Joueur 2 » dans le second. Quatre Joueurs de démonstration sont disponibles. La page lit l’UUID stable du Joueur choisi dans la vue du serveur. Le jeton `demo:TYPE:soireeId:acteurId` reste dans le `sessionStorage` de l’onglet (`croustyquizz.jeton`), jamais dans l’URL. La page est stockée dans `resources/demonstration/`, hors de `static/`, et servie par un contrôleur `@Profile({"dev", "recette"})`.

Les destinations et captures sont celles de `docs/api/`. Les trois pages lisent `?soiree=<UUID>`. La Console utilise le Bearer de démonstration ; sans ce jeton, elle demande `/api/csrf` et transmet session et CSRF, en prévision de #5. Le serveur de production refuse encore les identités tant que #5/#10 ne sont pas installées. La salle n'a aucune commande ; la Console et la Manette ont un bouton de recapture. Les commandes métier appartiennent aux modules des modes.

## Module factice

La case « Afficher le module factice » ajoute `factice=1` au lien de l'écran. Cet alias explicite, utilisable avec un jeton de démonstration, charge `modes/factice/<ecran>.mjs` à la place du module réel. Il affiche le contenu du bloc correspondant au vrai `modeEnCours`, sans ajouter de valeur `FACTICE` au contrat HTTP. Sans ce paramètre, les chemins sont exactement `modes/quiz|blindtest|karaoke|freestyle/<ecran>.mjs` ; le Karaoké est livré, les autres modules réels restent hors périmètre. Un module absent produit un message explicite, la partie commune reste visible.

Le Karaoké livre désormais ses trois modules de File d'attente (#93), avec recherche, fiche, inscriptions et désinscriptions personnelles, placements, priorité et limite. Les autres modules réels restent hors périmètre ; le mode Java factice du socle n'est installé que dans les tests (`BancDuSocle`, bloc `quiz`). Les routes `/api/test/.../manches` nécessitent un service de mode. Pour une recette navigateur sans base, `node tools/ecrans/servir-recette.mjs` sert les ressources sur le port local 18080, puis `playwright-cli run-code --filename=tools/ecrans/karaoke-recette-navigateur.mjs` simule HTTP et STOMP à la frontière réseau (après `playwright-cli open`). Les vrais HTML, CSS et modules sont servis localement, avec le client STOMP du WebJar extrait de Maven ; aucun accès au catalogue externe. L'ancienne `recette-navigateur.mjs` conserve la recette des coquilles avec le mode factice. Ces recettes ne vérifient pas l'intégration PostgreSQL.

## Vérification visuelle

Tester Manette à 390 × 844, Console à 1440 × 900 et 1024 × 768, salle à 1920 × 1080. Vérifier la capture, un événement, une coupure puis la recapture, la sortie de Manche et le focus clavier. Le talon n'affiche que le code effectivement reçu. Le QR code de l'arrivée est pour l'instant sur `ecran-de-salle.html` (#38) ; il rejoindra le talon d'`ecran.html`. Les maquettes de référence et les deux pages du prototype de Buzz restent utilisables.

Le contrat de module est documenté dans `AGENTS.md`, section « Coder un Mode de jeu ». `npm test --prefix tools/api` couvre contrats, synchronisation et logique des coquilles. `IdentiteDeTestParProfilTest` vérifie la route HTML en dev, recette, prod+recette, prod et sans profil de démonstration, sans Docker.

Le module Karaoké ajoute dans `head` un unique `<link rel="stylesheet" data-mode="karaoke">` vers `css/karaoke.css` et le retire au démontage. C'est la seule exception à la règle des zones ; le pied, le talon et le pilotage appartiennent toujours aux coquilles. Une capture restaure sans animation. Les réessais restent manuels et conservent clé, méthode, corps et compteurs ; après une confirmation inconnue, ils attendent une capture puis la connexion synchronisée.

La recette Karaoké couvre trois Joueurs distincts, deux Tours, priorité, recherche, refus, confirmation perdue puis tardive, recapture, focus, Échap et retrait du CSS. En dev, vérifier les quatre Joueurs de démonstration, la limite, les placements et le retardataire. Vérifier aussi à 390 × 844, 1440 × 900, 1024 × 768, 1920 × 1080 et autour de 1200/860 px : clavier, tactile, agrandissement, textes longs, contraste et mouvement réduit. La simulation réseau ne remplace pas ces essais en salle.

Pour la recette du ticket #114, lancer `./mvnw spring-boot:run` et ouvrir la Console et les Manettes des Joueurs 1 et 2 dans trois onglets avec un même UUID de Soirée de démonstration. Décocher « Afficher le module factice » sur les trois onglets.

1. Avec le jeton Bearer de la Console, créer une Manche via `POST /api/test/soirees/{soireeId}/manches` avec le corps JSON `{"mode":"KARAOKE","reglages":{}}`.
2. Avec le même jeton et un nouvel UUID dans l’en-tête `Idempotency-Key`, appeler `POST /api/test/soirees/{soireeId}/manches/{mancheId}/demarrer` en reprenant le `mancheId` reçu à la création.
3. Rechercher une Chanson et inscrire un Passage depuis la Manette du Joueur 1. Vérifier que le Passage apparaît dans la File d’attente de la Manette du Joueur 2 sans recapture.

Vérification de #114 dans Chrome, Manettes en 390 × 844 : le champ « Joueur de démonstration » n'apparaît que pour le rôle Joueur. Les Manettes des Joueurs 1 et 2 affichent chacune leur pseudo et la liste « Joueurs (4) ». Le Passage inscrit par le Joueur 1 apparaît dans « Ensuite » chez le Joueur 2 sans recapture, puis le Joueur 2 s'inscrit en position 2.

Vérifications de #93 : suite `npm test --prefix tools/api` verte ; recette navigateur exécutée sur réseau simulé avec les six captures ci-dessus (Console également à 1200 et 860 px), sans débordement horizontal et avec le talon de salle visible à 1080 px. Focus de sélection, de commandes après événement et après confirmation, Échap, recapture et retrait du CSS vérifiés. Le port 18080 étant occupé, la même recette a été exécutée sur 18093, sans changer son adresse par défaut dans le dépôt. Restent à vérifier en dev avec PostgreSQL et le catalogue réel, sur téléphone tactile, avec agrandissement et textes longs, et dans un Lieu à cinq mètres. Le contraste n'a pas fait l'objet d'une mesure instrumentée.

## Recette navigateur du Pilotage de démonstration (#119)

1. Ouvrir la page d’identité en profil `dev` ou `recette` avec un UUID de Soirée de démonstration neuf. Le champ « Pilotage de la Soirée » apparaît uniquement pour le rôle Animateur. Son choix par défaut est « Garder le Pilotage actuel ».
2. Choisir « Automatique » et ouvrir la Console. Vérifier que son résumé affiche « Pilotage automatique ». Rouvrir la page d’identité avec le même UUID et le choix par défaut doit conserver ce résumé.
3. Créer puis démarrer une Manche de Karaoké avec les deux requêtes de la recette #114. Revenir à la page d’identité de la même Soirée et essayer « Manuel ». Le refus « La Soirée a commencé : son Pilotage ne change plus. » doit apparaître près du formulaire, qui reste ouvert. La Console conserve « Pilotage automatique ».
4. Après redémarrage du serveur, rouvrir la Console avec le choix par défaut. Son résumé doit afficher « Pilotage manuel », y compris si la Manche est restaurée.

Vérification de #119 dans Chrome, branche lancée en local avec PostgreSQL : le champ « Pilotage de la Soirée » n'apparaît que pour l'Animateur, « Automatique » donne « Pilotage automatique » sur la Console, « Manuel » après le démarrage d'une Manche affiche le refus sans quitter la page, focus sur le champ, et la Soirée repasse en Pilotage manuel après un redémarrage du serveur.
