# Coquilles des trois écrans (#80)

Ouvrir `/test/identite.html?soiree=<UUID>` en profil `dev` ou `recette`, dans un onglet par rôle. Le jeton `demo:TYPE:soireeId:acteurId` reste dans `sessionStorage` (`croustyquizz.jeton`), jamais dans l'URL. La page demande une vue au serveur avant de déposer l'identité ; le Joueur prend son UUID stable dans cette projection. La page est physiquement dans `resources/demonstration/`, hors de `static/`, et servie par un contrôleur `@Profile({"dev", "recette"})` à l'URL demandée : aucun fichier statique ne contourne cette restriction en production.

Les destinations et captures sont celles de `docs/api/`. Les trois pages lisent `?soiree=<UUID>`. La Console utilise le Bearer de démonstration ; sans ce jeton, elle demande `/api/csrf` et transmet session et CSRF, en prévision de #5. Le serveur de production refuse encore les identités tant que #5/#10 ne sont pas installées. L’Écran de salle envoie les commandes de son lecteur. La Console et la Manette ont un bouton de recapture. Les commandes métier appartiennent aux modules des modes.

## Module factice

La case « Afficher le module factice » ajoute `factice=1` au lien de l'écran. Cet alias explicite, utilisable avec un jeton de démonstration, charge `modes/factice/<ecran>.mjs` à la place du module réel. Il affiche le contenu du bloc correspondant au vrai `modeEnCours`, sans ajouter de valeur `FACTICE` au contrat HTTP. Sans ce paramètre, les chemins sont exactement `modes/quiz|blindtest|karaoke|freestyle/<ecran>.mjs` ; le Karaoké est livré, les autres modules réels restent hors périmètre.

Si le bloc du Mode de jeu est absent ou nul, le gestionnaire démonte le module en place et seule la partie commune reste visible. C’est le cas des blocs du Blind test et du Freestyle, qui sont encore réservés. Si le bloc est présent mais que le module ne peut pas être chargé, la coquille affiche un message d’erreur et garde la partie commune visible.

Le Karaoké livre désormais ses trois modules de File d'attente (#93), avec recherche, fiche, inscriptions et désinscriptions personnelles, placements, priorité et limite. Les autres modules réels restent hors périmètre ; le mode Java factice du socle n'est installé que dans les tests (`BancDuSocle`, bloc `quiz`). Les routes `/api/test/.../manches` nécessitent un service de mode. Pour une recette navigateur sans base, `node tools/ecrans/servir-recette.mjs` sert les ressources sur le port local 18080, puis `playwright-cli run-code --filename=tools/ecrans/karaoke-recette-navigateur.mjs` simule HTTP et STOMP à la frontière réseau (après `playwright-cli open`). Les vrais HTML, CSS et modules sont servis localement, avec le client STOMP du WebJar extrait de Maven ; aucun accès au catalogue externe. L'ancienne `recette-navigateur.mjs` conserve la recette des coquilles avec le mode factice. Ces recettes ne vérifient pas l'intégration PostgreSQL.

## Vérification visuelle

Tester Manette à 390 × 844, Console à 1440 × 900 et 1024 × 768, salle à 1920 × 1080. Vérifier la capture, un événement, une coupure puis la recapture, la sortie de Manche et le focus clavier. Le talon n'affiche que le code effectivement reçu. Le QR code de l'arrivée est pour l'instant sur `ecran-de-salle.html` (#38) ; il rejoindra le talon d'`ecran.html`. Les maquettes de référence et les deux pages du prototype de Buzz restent utilisables.

Le contrat de module est documenté dans `AGENTS.md`, section « Coder un Mode de jeu ». `npm test --prefix tools/api` couvre contrats, synchronisation et logique des coquilles. `IdentiteDeTestParProfilTest` vérifie la route HTML en dev, recette, prod+recette, prod et sans profil de démonstration, sans Docker.

Le module Karaoké ajoute dans `head` un unique `<link rel="stylesheet" data-mode="karaoke">` vers `css/karaoke.css` et le retire au démontage. C'est la seule exception à la règle des zones. Le pied, le talon et le pilotage commun appartiennent aux coquilles. Les modules regroupent les commandes propres à leur Mode de jeu, dont la barre du Passage du Karaoké. Une capture restaure sans animation. Les réessais restent manuels et conservent clé, méthode, corps et compteurs ; après une confirmation inconnue, ils attendent une capture puis la connexion synchronisée.

La recette Karaoké couvre trois Joueurs distincts, deux Tours, priorité, recherche, refus, confirmation perdue puis tardive, recapture, focus, Échap et retrait du CSS. Pour la recette réelle en dev, vérifier l'unique Joueur de démonstration, la limite, les placements et le retardataire. Vérifier aussi à 390 × 844, 1440 × 900, 1024 × 768, 1920 × 1080 et autour de 1200/860 px : clavier, tactile, agrandissement, textes longs, contraste et mouvement réduit. La simulation réseau ne remplace pas ces essais en salle.

Vérifications de #93 : suite `npm test --prefix tools/api` verte ; recette navigateur exécutée sur réseau simulé avec les six captures ci-dessus (Console également à 1200 et 860 px), sans débordement horizontal et avec le talon de salle visible à 1080 px. Focus de sélection, de commandes après événement et après confirmation, Échap, recapture et retrait du CSS vérifiés. Le port 18080 étant occupé, la même recette a été exécutée sur 18093, sans changer son adresse par défaut dans le dépôt. Restent à vérifier en dev avec PostgreSQL et le catalogue réel, sur téléphone tactile, avec agrandissement et textes longs, et dans un Lieu à cinq mètres. Le contraste n'a pas fait l'objet d'une mesure instrumentée.

### Recette du Passage de Karaoké (#112)

Depuis la racine, lancer `node tools/ecrans/servir-recette.mjs`, puis `playwright-cli open http://127.0.0.1:18080/index.html` et `playwright-cli run-code --filename=tools/ecrans/karaoke-recette-navigateur.mjs`. Le serveur de recette génère les WAV et le script dépose les captures dans `target/issue-112/recette/`. Pour changer de port, régler `CROUSTYQUIZZ_RECETTE_PORT` côté serveur et ouvrir cette même adresse dans Playwright.

Recette exécutée en Chromium sans interface graphique sur le port 18113, avec HTTP et STOMP simulés. Vérifications sur les vraies pages et le lecteur HTML audio :

- Dossier choisi depuis l’Écran de salle, WAV de 6 s décodé et lu jusqu’à sa fin malgré des paroles de 0,8 s, puis clôture signalée avant la fin prévue. Les essais couvrent un fichier unique, l’absence de correspondance et deux correspondances en sous-dossiers. La déclaration contient uniquement le nom, la taille et la durée du fichier.
- Son bloqué puis activé au clavier, appel et confirmation après échéance, pauses de l’appel et du fichier, calage depuis une deuxième Console, erreur au départ puis reprise en paroles seules. Les refus de lecture sont injectés au niveau de `play()`.
- Réouverture en cours de Passage sans son ni signal, seconde fenêtre désignée sans interrompre la lecture, une seule redésignation à la Manche suivante, bandeau d’envoi de salle toujours masqué et retrait complet du module en fin de Manche.
- Manette à 390 × 844 avec confirmation tactile émulée. Console à 1440, 1200, 1024 et 860 px sans débordement avec pilotage et calage visibles. Salle à 1920 × 1080 avec le courant détaché, trois tickets à titres longs et talon visible. Clavier, Échap, retour du focus, mouvement réduit et contraste mesuré sur le bouton « Je suis prêt » et la ligne de paroles active.

Le test constate le décodage, la progression et la fin du WAV dans Chromium. L’audibilité sur le matériel du Lieu, la lecture à cinq mètres, le tactile physique et l’intégration des routes #99 et #100 restent à vérifier en situation.
