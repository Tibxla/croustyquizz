# Coquilles des trois écrans (#80)

Ouvrir `/test/identite.html?soiree=<UUID>` en profil `dev` ou `recette`. Pour ouvrir deux Manettes, charger la page dans deux onglets avec le même UUID de Soirée, choisir le rôle Joueur dans chacun, puis sélectionner « Joueur 1 » dans le premier et « Joueur 2 » dans le second. Quatre Joueurs de démonstration sont disponibles. La page lit l’UUID stable du Joueur choisi dans la vue du serveur. Le jeton `demo:TYPE:soireeId:acteurId` reste dans le `sessionStorage` de l’onglet (`croustyquizz.jeton`), jamais dans l’URL. La page est stockée dans `resources/demonstration/`, hors de `static/`, et servie par un contrôleur `@Profile({"dev", "recette"})`.

Les destinations et captures sont celles de `docs/api/`. Les trois pages lisent `?soiree=<UUID>`. La Console utilise le Bearer de démonstration ; sans ce jeton, elle demande `/api/csrf` et transmet session et CSRF, en prévision de #5. Le serveur de production refuse encore les identités tant que #5/#10 ne sont pas installées. L’Écran de salle envoie les commandes de son lecteur. La Console et la Manette ont un bouton de recapture. Les commandes métier appartiennent aux modules des modes.

Le navigateur revalide les scripts, les feuilles de style, les pages HTML et les WebJars à chaque chargement (`Cache-Control: no-cache` et `Last-Modified`) : après un déploiement, il ne mélange pas un module ancien et un module neuf (#132). Un fichier inchangé coûte une réponse `304`. La page `/test/identite.html` reste en `no-store`. Cette revalidation repose sur la date des fichiers dans le JAR : ne pas ajouter `project.build.outputTimestamp` au `pom.xml` (builds reproductibles) sans passer à un ETag calculé sur le contenu, sinon tous les fichiers garderaient la même date et le navigateur resservirait d'anciens modules.

## Recette du programme de la Soirée (#9)

1. Créer une Soirée dans `/preparer-soiree.html`. Reprendre son UUID dans `/test/identite.html?soiree=<UUID>`, choisir le rôle Animateur, garder le Pilotage actuel et décocher le module factice.
2. Dans « Programme », ajouter un Quiz avec 3 étapes de 20 secondes puis un Karaoké. Déplacer le Karaoké en tête au clavier et vérifier le retour du focus à une commande de la même Manche.
3. Ouvrir la Soirée depuis la page de préparation. Dans la Console, vérifier le passage à « Ouverte », retirer le Quiz et recharger pour vérifier l’ordre enregistré.
4. Couper la connexion, vérifier que la Console suspend les commandes puis que la reconnexion restaure le programme. Démarrer ensuite le Karaoké par la route de test de la recette #114, avec l’identifiant de la Manche ajoutée, et vérifier que le programme reste visible avec la mention « Le programme est figé. ».

## Accès d'un vrai Joueur (#10)

Un Joueur arrive par `rejoindre.html`, puis `manette.html?soiree=<UUID>`. Son jeton de reconnexion opaque et son UUID sont rangés par Soirée dans `localStorage` (`js/acces-manette.mjs`, clés `croustyquizz.manette.<soireeId>`), jusqu'au vidage du navigateur, comme le veut le PRD : ils survivent à la mise en veille, à la fermeture de l'onglet et à un QR code rescanné. Si `localStorage` est refusé ou plein, l'accès se replie sur `sessionStorage` : il survit au rechargement, pas à la fermeture de l'onglet. Sans aucun stockage, `rejoindre.html` le dit avant de créer le Joueur ; une écriture qui échoue juste après la création est signalée au lieu d'ouvrir une Manette sans accès. Un QR code rescanné reprend le Joueur de cette Soirée, jamais celui d'une autre. Ouverte sans code, `rejoindre.html` ne reprend que la dernière Soirée rejointe depuis moins de 12 heures : un ancien pseudo ne revient pas tout seul des jours plus tard. Seul un refus du jeton (401 ou 404) ou une Soirée terminée l'efface ; une coupure le garde et réessaie. Les identités de démonstration restent dans `sessionStorage`. Une Manette ouverte avant ce stockage durable avait rangé son jeton et sa Soirée dans `sessionStorage` (`croustyquizz.jeton`, `croustyquizz.soiree`) : `rejoindre.html` les reprend, lit l'UUID du Joueur et le code dans la capture, les range dans `localStorage`, puis efface l'ancien emplacement.

## Module factice

La case « Afficher le module factice » ajoute `factice=1` au lien de l'écran. Cet alias explicite, utilisable avec un jeton de démonstration, charge `modes/factice/<ecran>.mjs` à la place du module réel. Il affiche le contenu du bloc correspondant au vrai `modeEnCours`, sans ajouter de valeur `FACTICE` au contrat HTTP. Sans ce paramètre, les chemins sont exactement `modes/quiz|blindtest|karaoke|freestyle/<ecran>.mjs` ; le Karaoké est livré, les autres modules réels restent hors périmètre.

Si le bloc du Mode de jeu est absent ou nul, le gestionnaire démonte le module en place et seule la partie commune reste visible. C’est le cas des blocs du Blind test et du Freestyle, qui sont encore réservés. Si le bloc est présent mais que le module ne peut pas être chargé, la coquille affiche un message d’erreur et garde la partie commune visible.

Le Karaoké livre désormais ses trois modules de File d'attente (#93), avec recherche, fiche, inscriptions et désinscriptions personnelles, placements, priorité et limite. Les autres modules réels restent hors périmètre ; le mode Java factice du socle n'est installé que dans les tests (`BancDuSocle`, bloc `quiz`). Les routes `/api/test/.../manches` nécessitent un service de mode. Pour une recette navigateur sans base, `node tools/ecrans/servir-recette.mjs` sert les ressources sur le port local 18080, puis `playwright-cli run-code --filename=tools/ecrans/karaoke-recette-navigateur.mjs` simule HTTP et STOMP à la frontière réseau (après `playwright-cli open`). Les vrais HTML, CSS et modules sont servis localement, avec le client STOMP du WebJar extrait de Maven ; aucun accès au catalogue externe. L'ancienne `recette-navigateur.mjs` conserve la recette des coquilles avec le mode factice. Ces recettes ne vérifient pas l'intégration PostgreSQL.

## Vérification visuelle

Tester Manette à 390 × 844, Console à 1440 × 900 et 1024 × 768, salle à 1920 × 1080. Vérifier la capture, un événement, une coupure puis la recapture, la sortie de Manche et le focus clavier. Le talon n'affiche que le code effectivement reçu. Le QR code de l'arrivée est pour l'instant sur `ecran-de-salle.html` (#38) ; il rejoindra le talon d'`ecran.html`. Les maquettes de référence et les deux pages du prototype de Buzz restent utilisables.

Le contrat de module est documenté dans `AGENTS.md`, section « Coder un Mode de jeu ». `npm test --prefix tools/api` couvre contrats, synchronisation et logique des coquilles. `IdentiteDeTestParProfilTest` vérifie la route HTML en dev, recette, prod+recette, prod et sans profil de démonstration, sans Docker.

Le module Karaoké ajoute dans `head` un unique `<link rel="stylesheet" data-mode="karaoke">` vers `css/karaoke.css` et le retire au démontage. C'est la seule exception à la règle des zones. Le pied, le talon et le pilotage commun appartiennent aux coquilles. Les modules regroupent les commandes propres à leur Mode de jeu, dont la barre du Passage du Karaoké. Un ticket créé à partir d'une capture apparaît immobile. Sur l'Écran de salle, le ticket déjà affiché poursuit son détachement quand un événement actualise son état. Les réessais restent manuels et conservent clé, méthode, corps et compteurs. Après une confirmation inconnue, ils attendent une capture puis la connexion synchronisée. Pour un envoi avec `etapeId` refusé avec `ETAPE_INACTIVE`, la prochaine capture permet de former une nouvelle commande avec l'étape courante.

La recette Karaoké couvre trois Joueurs distincts, deux Tours, priorité, recherche, refus, confirmation perdue puis tardive, recapture, focus, Échap et retrait du CSS. En dev, vérifier les quatre Joueurs de démonstration, la limite, les placements et le retardataire. Vérifier aussi à 390 × 844, 1440 × 900, 1024 × 768, 1920 × 1080 et autour de 1200/860 px : clavier, tactile, agrandissement, textes longs, contraste et mouvement réduit. La simulation réseau ne remplace pas ces essais en salle.

Pour la recette du ticket #114, lancer `./mvnw spring-boot:run` et ouvrir la Console et les Manettes des Joueurs 1 et 2 dans trois onglets avec un même UUID de Soirée de démonstration. Décocher « Afficher le module factice » sur les trois onglets.

1. Avec le jeton Bearer de la Console, créer une Manche via `POST /api/test/soirees/{soireeId}/manches` avec le corps JSON `{"mode":"KARAOKE","reglages":{}}`.
2. Avec le même jeton et un nouvel UUID dans l’en-tête `Idempotency-Key`, appeler `POST /api/test/soirees/{soireeId}/manches/{mancheId}/demarrer` en reprenant le `mancheId` reçu à la création.
3. Rechercher une Chanson et inscrire un Passage depuis la Manette du Joueur 1. Vérifier que le Passage apparaît dans la File d’attente de la Manette du Joueur 2 sans recapture.

Vérification de #114 dans Chrome, Manettes en 390 × 844 : le champ « Joueur de démonstration » n'apparaît que pour le rôle Joueur. Les Manettes des Joueurs 1 et 2 affichent chacune leur pseudo et la liste « Joueurs (4) ». Le Passage inscrit par le Joueur 1 apparaît dans « Ensuite » chez le Joueur 2 sans recapture, puis le Joueur 2 s'inscrit en position 2.

Vérifications de #93 : suite `npm test --prefix tools/api` verte ; recette navigateur exécutée sur réseau simulé avec les six captures ci-dessus (Console également à 1200 et 860 px), sans débordement horizontal et avec le talon de salle visible à 1080 px. Focus de sélection, de commandes après événement et après confirmation, Échap, recapture et retrait du CSS vérifiés. Le port 18080 étant occupé, la même recette a été exécutée sur 18093, sans changer son adresse par défaut dans le dépôt. Restent à vérifier en dev avec PostgreSQL et le catalogue réel, sur téléphone tactile, avec agrandissement et textes longs, et dans un Lieu à cinq mètres. Le contraste n'a pas fait l'objet d'une mesure instrumentée.

### Recette du Passage de Karaoké (#112)

Depuis la racine, lancer `node tools/ecrans/servir-recette.mjs`, puis `playwright-cli open http://127.0.0.1:18080/index.html` et `playwright-cli run-code --filename=tools/ecrans/karaoke-recette-navigateur.mjs`. Le serveur de recette génère les WAV et le script dépose les captures dans `target/issue-112/recette/`. Pour changer de port, régler `CROUSTYQUIZZ_RECETTE_PORT` côté serveur et ouvrir cette même adresse dans Playwright.

Le geste « Activer le son » joue le silence sur le même élément audio que le fichier. Pendant une coupure, il débloque le son immédiatement et la préparation reprend à la prochaine Vue synchronisée.

Recette exécutée en Chromium sans interface graphique sur le port 18113, avec HTTP et STOMP simulés. Vérifications sur les vraies pages et le lecteur HTML audio :

- Dossier choisi depuis l’Écran de salle, WAV de 6 s décodé et lu jusqu’à sa fin malgré des paroles de 0,8 s, puis clôture signalée avant la fin prévue. Les essais couvrent un fichier unique, l’absence de correspondance et deux correspondances en sous-dossiers. La déclaration contient uniquement le nom, la taille et la durée du fichier.
- Son bloqué puis activé au clavier, appel et confirmation après échéance, pauses de l’appel et du fichier, calage depuis une deuxième Console, erreur au départ puis reprise en paroles seules. Les refus de lecture sont injectés au niveau de `play()`.
- Réouverture en cours de Passage sans son ni signal, seconde fenêtre désignée sans interrompre la lecture, une seule redésignation à la Manche suivante, bandeau d’envoi de salle toujours masqué et retrait complet du module en fin de Manche.
- Manette à 390 × 844 avec confirmation tactile émulée. Console à 1440, 1200, 1024 et 860 px sans débordement avec pilotage et calage visibles. Salle à 1920 × 1080 avec le courant détaché, trois tickets à titres longs et talon visible. Clavier, Échap, retour du focus, mouvement réduit et contraste mesuré sur le bouton « Je suis prêt » et la ligne de paroles active.

Le test constate le décodage, la progression et la fin du WAV dans Chromium. L’audibilité sur le matériel du Lieu, la lecture à cinq mètres, le tactile physique et l’intégration des routes #99 et #100 restent à vérifier en situation.

### Recette du Vote du public (#115)

Reprendre la Soirée de démonstration du ticket #114 avec la Console, l’Écran de salle et deux
Manettes distinctes. Sur la Manette du Joueur 1, inscrire une Chanson. L’appeler depuis la Console,
confirmer « Je suis prêt » depuis sa Manette et laisser le Passage se terminer.

1. Vérifier que « Vote du public » apparaît avant « En scène » et que la Manette de l’Interprète
   indique que le public vote pour son Passage. La Manette du Joueur 2 propose les cinq notes.
2. Sur la Manette du Joueur 2, choisir 4, puis envoyer. Vérifier la sélection au clavier,
   le bouton désactivé avant le choix et la confirmation « Ta note est enregistrée : 4 sur 5. »
   après le reçu HTTP. La Console compte un vote. Recharger la Manette et vérifier sa note.
3. Rejouer le parcours en mettant la Manche en pause avant l’envoi. Vérifier « en pause » dans
   la Console et l’indication de reprise sur la Manette. Après la reprise, vérifier que le temps
   restant repart de sa valeur au moment de la pause.
4. Terminer deux Passages à moins de trente secondes d’intervalle. Vérifier que la Console
   suit les deux délais, puis que chaque vote se clôt à son échéance. Si le Joueur 2 est appelé
   entre-temps, « Je suis prêt » prend l’action citron. Avec deux votes accessibles, le plus récent
   porte cette action.
5. Dans une autre Manche, choisir « Terminer la Manche » pendant un vote. Vérifier la disparition
   des commandes de vote et l’absence de nouveaux Points dans le Classement général.

Les tests automatisés couvrent le rendu des modules, les reçus et les destinations STOMP. Cette
recette décrit les vérifications à réaliser dans le navigateur.

### Musique d'attente (#140)

Sur `/ecran.html`, choisir le dossier de musique depuis la coquille. Les MP3, M4A, OGG et WAV du sous-dossier `Attente`, y compris dans ses sous-dossiers, alimentent la Musique d'attente. La casse et les accents du nom `Attente` sont ignorés. La recherche locale des Chansons écarte ces fichiers. Le navigateur garde le dossier en mémoire le temps de l'ouverture de la page.

Sur l'Écran de salle, les modules appellent `contexte.signalerSonDeManche(true)` au départ du son de Manche et attendent que la promesse rende `true` avant de démarrer le son. Le signal vaut aussi lors d'une capture en cours de Passage pour faire taire la Musique d'attente. Le module appelle `contexte.signalerSonDeManche(false)` après avoir arrêté son lecteur, à la clôture et au démontage. Le Karaoké garde la Musique d'attente pendant l'appel et la suspend dès le départ du Passage, jusqu'à sa clôture, y compris pendant une pause du Passage.

La coordination locale utilise `BroadcastChannel` et les Web Locks. La fenêtre ouverte le plus récemment parmi celles dont le son est activé prend la main. Un Passage déjà démarré garde son lecteur désigné jusqu'à sa clôture.

Pour la recette, ouvrir deux fenêtres du même profil de navigateur en HTTPS ou sur `localhost`. Choisir dans la première un dossier avec plusieurs morceaux dans `Attente`. Vérifier l'enchaînement puis l'appel d'un Passage de Karaoké. Confirmer le départ, vérifier le silence de la Musique d'attente puis sa reprise à la clôture. Choisir le dossier dans la deuxième fenêtre et vérifier qu'elle prend la main. Fermer la deuxième, puis rouvrir ou recharger l'Écran de salle et choisir à nouveau le dossier. À chaque relais, écouter la fin de la première lecture avant la suivante. Refaire l'essai avec un dossier sans `Attente`, puis avec une Chanson présente uniquement dans `Attente`.

### Recharger l’Écran pendant l’appel (#144)

1. Appeler un Passage dont la Chanson correspond à un fichier audio lisible. Pendant l’appel, recharger l’Écran de salle.
2. Une fois le compteur à « 0 s », choisir de nouveau le dossier sur l’Écran. Attendre que la Console affiche « Fichier local » avec le nom du fichier.
3. Confirmer « Je suis prêt » sur la Manette et vérifier que l’Écran joue le fichier.
4. Sur un autre Passage, confirmer avant que l’Écran ait déclaré une lecture. Vérifier que les paroles défilent et que la Console affiche « Paroles seules : Écran lecteur pas prêt ». Si le chargement finit ensuite, vérifier que le fichier reste silencieux.

L’échéance de l’appel sert de repère. Tant que le Passage est appelé, l’Écran prépare le fichier et déclare le résultat de la recherche et du chargement, même si les métadonnées arrivent après l’échéance.

## Recette navigateur du Pilotage de démonstration (#119)

1. Ouvrir la page d’identité en profil `dev` ou `recette` avec un UUID de Soirée de démonstration neuf. Le champ « Pilotage de la Soirée » apparaît uniquement pour le rôle Animateur. Son choix par défaut est « Garder le Pilotage actuel ».
2. Choisir « Automatique » et ouvrir la Console. Vérifier que son résumé affiche « Pilotage automatique ». Rouvrir la page d’identité avec le même UUID et le choix par défaut doit conserver ce résumé.
3. Créer puis démarrer une Manche de Karaoké avec les deux requêtes de la recette #114. Revenir à la page d’identité de la même Soirée et essayer « Manuel ». Le refus « La Soirée a commencé : son Pilotage ne change plus. » doit apparaître près du formulaire, qui reste ouvert. La Console conserve « Pilotage automatique ».
4. Après redémarrage du serveur, rouvrir la Console avec le choix par défaut. Son résumé doit afficher « Pilotage manuel », y compris si la Manche est restaurée.

Vérification de #119 dans Chrome, branche lancée en local avec PostgreSQL : le champ « Pilotage de la Soirée » n'apparaît que pour l'Animateur, « Automatique » donne « Pilotage automatique » sur la Console, « Manuel » après le démarrage d'une Manche affiche le refus sans quitter la page, focus sur le champ, et la Soirée repasse en Pilotage manuel après un redémarrage du serveur.

## Recette navigateur de l’appel automatique des Passages (#120)

1. Choisir « Automatique » sur la page d’identité de l’Animateur pour une Soirée neuve. Ouvrir la Console, l’Écran de salle et les Manettes des Joueurs 1 et 2 avec le même UUID, puis créer et démarrer une Manche de Karaoké comme dans la recette #114.
2. Inscrire une Chanson depuis la première Manette. Vérifier que les trois écrans affichent aussitôt le Passage appelé et que seul le Joueur 1 peut cliquer sur « Je suis prêt ». La barre de la Console affiche « Appel automatique » à la dernière place.
3. Inscrire une Chanson différente depuis la deuxième Manette. Vérifier que le Joueur 2 attend son Tour. Désigner l’Écran de salle comme lecteur et choisir un dossier qui contient les deux fichiers audio nommés selon la recette #112.
4. Confirmer le premier Passage après la préparation de son fichier. À la fin de lecture, vérifier que le Joueur 2 est appelé, que l’Écran de salle prépare son fichier et que le Vote du public du premier Passage s’ouvre sur sa Manette. Confirmer le deuxième Passage une fois préparé et vérifier que le fichier local démarre.
5. Rejouer avec une pause pendant un Passage alors que le Joueur 2 attend. Cliquer sur « Passer » et vérifier que le Joueur 2 reste en attente jusqu’à « Reprendre ».
6. Rejouer dans une Soirée neuve en Manuel. Vérifier que « Appeler le suivant » est disponible quand la File d’attente est libre et que l’Animateur doit l’utiliser après la première inscription et après la fin du Passage.
