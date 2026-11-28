# Coquilles des trois écrans (#80)

Ouvrir `/test/identite.html?soiree=<UUID>` en profil `dev` ou `recette`. Pour ouvrir deux Manettes, charger la page dans deux onglets avec le même UUID de Soirée, choisir le rôle Joueur dans chacun, puis sélectionner « Joueur 1 » dans le premier et « Joueur 2 » dans le second. Quatre Joueurs de démonstration sont disponibles. La page lit l’UUID stable du Joueur choisi dans la vue du serveur. Le jeton `demo:TYPE:soireeId:acteurId` reste dans le `sessionStorage` de l’onglet (`croustyquizz.jeton`), jamais dans l’URL. La page est stockée dans `resources/demonstration/`, hors de `static/`, et servie par un contrôleur `@Profile({"dev", "recette"})`.

Les destinations et captures sont celles de `docs/api/`. Les trois pages lisent `?soiree=<UUID>`. Le formulaire d’identité vérifie le jeton de l’Animateur par la capture de la Console. Sans ce jeton, la Console demande `/api/csrf` et transmet session et CSRF, en prévision de #5. Sur une Soirée enregistrée, la Manette relit l’identité renvoyée par le serveur et enregistrée par `rejoindre.html`. La préparation ouvre l’Écran de salle avec `#cle=…` dans l’adresse. La coquille conserve la clé pour cette Soirée dans le stockage de l’onglet, puis retire le fragment. L’Écran de salle envoie les commandes de son lecteur. La Console et la Manette ont un bouton de recapture. Les commandes de Soirée appartiennent à la Console et celles de Manche aux modules des modes.

Le navigateur revalide les scripts, les feuilles de style, les pages HTML et les WebJars à chaque chargement (`Cache-Control: no-cache` et `Last-Modified`) : après un déploiement, il ne mélange pas un module ancien et un module neuf (#132). Un fichier inchangé coûte une réponse `304`. La page `/test/identite.html` reste en `no-store`. Cette revalidation repose sur la date des fichiers dans le JAR : ne pas ajouter `project.build.outputTimestamp` au `pom.xml` (builds reproductibles) sans passer à un ETag calculé sur le contenu, sinon tous les fichiers garderaient la même date et le navigateur resservirait d'anciens modules.

## Recette de bout en bout avant une livraison

Ce parcours se joue sur `develop` déployée (recette Render) après chaque vague de fusions, et en entier avant toute fusion vers `main`. Il ne remplace pas les recettes de chaque fonctionnalité : il vérifie que tout tient ensemble, et que chaque écran se comprend sans explication. Prévoir un ordinateur pour la Console, un deuxième écran ou une télé pour l'Écran de salle, et deux téléphones. Compter de 30 à 45 minutes.

### Le parcours

1. **Le Lieu.** Créer un compte depuis `/inscription.html`, se connecter, créer une Soirée, puis « Ouvrir aux Joueurs ». Ouvrir la Console et l'Écran de salle depuis la préparation.
2. **L'arrivée.** Sur chaque téléphone, scanner le QR code de l'Écran de salle et choisir un pseudo. Les Joueurs apparaissent en direct sur la Console et l'Écran de salle, avec le nombre de Joueurs en ligne.
3. **Le Buzz.** Buzzer depuis un téléphone : le dernier Buzz s'affiche sur les trois écrans.
4. **La Musique d'attente.** Sur l'Écran de salle, « Activer le son » : un morceau de la Bibliothèque de base joue, avec son crédit en bas de l'écran. Couper puis relancer la musique depuis la Console. Ouvrir un second Écran de salle : la musique ne se dédouble pas.
5. **Le Karaoké.** Ajouter une Manche de Karaoké au programme et la lancer. Sur un téléphone, chercher et inscrire une Chanson de la Bibliothèque de base, en duo avec l'autre Joueur. L'appeler depuis la Console, confirmer « Je suis prêt » : la musique joue, les paroles défilent en mesure, le crédit reste affiché. Noter le Passage depuis le second téléphone, puis terminer la Manche : le Palmarès s'affiche.
6. **Le Freestyle.** Lancer une Manche de Freestyle à plusieurs, sur une Instrumentale, puis le vote du meilleur Interprète.
7. **La fin.** Terminer la Soirée : le Podium final s'affiche sur l'Écran de salle, puis le Palmarès.
8. **Les coupures.** À n'importe quel moment, fermer puis rouvrir un téléphone : le Joueur passe hors ligne puis revient. Recharger l'Écran de salle : il retrouve son état.

### À vérifier à chaque étape

**Lisibilité et placement**
- Aucun texte n'est coupé, ne déborde de son cadre et ne chevauche un autre élément. Un texte long, comme un pseudo de 30 caractères ou un titre de Chanson, se termine par « … » ou passe proprement à la ligne.
- Rien ne demande de défiler sur l'Écran de salle, en 1920 × 1080 comme en 1280 × 720. Le code d'accès et le QR code restent visibles en entier.
- Sur un téléphone de 390 px de large, aucun défilement horizontal, et les boutons se touchent sans viser.
- L'Écran de salle se lit à 5 m dans une salle sombre : titres, paroles, notes, Palmarès.

**Compréhension sans explication**
- Sur chaque écran, on sait ce qui se passe et ce qu'il faut faire ensuite, sans avoir lu de consigne : quel bouton presser, qui doit agir, ce qu'on attend.
- Les libellés emploient les mots du glossaire et disent l'action (« Je suis prêt », « Appeler le suivant »), jamais un terme technique.
- Un refus ou une erreur dit ce qui bloque et comment s'en sortir, au bon endroit.
- Une action n'est jamais annoncée réussie avant la réponse du serveur. Une attente se voit (« Envoi en cours… »).
- Les trois écrans racontent la même chose au même moment.

Noter chaque écart avec une capture, l'écran, la taille et l'étape, puis ouvrir une issue par écart.

## Recette du programme de la Soirée (#9)

1. Créer une Soirée dans `/preparer-soiree.html`. Reprendre son UUID dans `/test/identite.html?soiree=<UUID>`, choisir le rôle Animateur, garder le Pilotage actuel et décocher le module factice.
2. Dans « Programme », ajouter un Quiz avec 3 étapes de 20 secondes puis un Karaoké. Déplacer le Karaoké en tête au clavier et vérifier le retour du focus à une commande de la même Manche.
3. Ouvrir la Soirée depuis la page de préparation. Dans la Console, vérifier le passage à « Ouverte », retirer le Quiz et recharger pour vérifier l’ordre enregistré.
4. Couper la connexion, vérifier que la Console suspend les commandes puis que la reconnexion restaure le programme. Démarrer ensuite le Karaoké par la route de test de la recette #114, avec l’identifiant de la Manche ajoutée, et vérifier que le programme reste visible avec la mention « Le programme est figé. ».

## Recette du déroulement manuel d’une Soirée (#12)

1. En profil `dev`, créer une Soirée en Pilotage Manuel dans `/preparer-soiree.html`. Ouvrir sa Console via `/test/identite.html?soiree=<UUID>`, rôle Animateur, Pilotage actuel et module factice décoché. Ajouter deux Manches de Karaoké, puis déplacer la seconde en tête. Ouvrir la Soirée aux Joueurs depuis la préparation.
2. Depuis la préparation, ouvrir l’Écran de salle et vérifier que `#cle=…` disparaît de l’adresse. Rejoindre la Soirée avec le QR code de cet Écran ou saisir le code dans `/rejoindre.html`. Vérifier que la Manette affiche le pseudo choisi, puis rescanner le QR code dans le même navigateur pour retrouver le même Joueur. Garder la Manette et la salle ouvertes pendant le parcours.
3. Dans la Console, lancer la Soirée. Vérifier le bandeau d’envoi puis le reçu HTTP. Les trois écrans affichent la première Manche du programme et la Console fige l’ordre. Pendant cette Manche, « Manche suivante » est désactivé. Avec un Passage actif, exercer Pause, Reprendre et Passer dans le module Karaoké. Recharger l’Écran de salle en cours de Manche et vérifier la capture.
4. Terminer la Manche avec le bouton du Karaoké, puis cliquer sur « Manche suivante » dans la barre. Vérifier que la deuxième Manche arrive sur les trois écrans. Couper puis rétablir la connexion de la Console. Vérifier que les commandes se réactivent après la capture.
5. Ouvrir « Terminer la Soirée ». Le focus initial est sur « Annuler ». Tester Échap et le retour du focus au déclencheur, puis rouvrir et confirmer. La Console, l’Écran de salle et la Manette affichent « Soirée terminée ». Recharger l’Écran avec la même clé et vérifier cette vue finale.

Le test `DeroulementSoireeTempsReelTest` exécute le parcours HTTP et STOMP avec PostgreSQL, une Horloge factice et deux Manches factices. Pour vérifier la disposition et les interactions des coquilles, utiliser une Console à 1440 × 900 et 1024 × 768, une Manette à 390 × 844 et un Écran de salle à 1920 × 1080.

## Recette d’un CONNECT refusé (#241)

1. En profil `dev`, créer une Soirée dans `/preparer-soiree.html` et noter son UUID. La page d’identité refuse déjà un Écran ou un Joueur de démonstration sur cette Soirée : déposer le jeton à la main, dans la console du navigateur d’un onglet de l’application, avec `sessionStorage.setItem('croustyquizz.jeton', 'demo:ECRAN:<UUID>:<un autre UUID>')`, puis ouvrir `/ecran.html?soiree=<UUID>` dans ce même onglet.
2. Vérifier que l’état passe à « Connexion refusée. » et que l’alerte affiche « Cette Soirée n'accepte pas d'Écran de démonstration : ouvre l'Écran de salle avec la clé créée depuis la Console. ». Dans l’onglet réseau, la WebSocket `/ws` reçoit une trame `ERROR`, puis se ferme (code 1002). Attendre trente secondes : aucune nouvelle connexion `/ws` ne part.
3. Refaire l’essai avec `demo:JOUEUR:<UUID>:<un autre UUID>` et `/manette.html?soiree=<UUID>`. La Manette affiche « Cette Soirée n'accepte pas d'identité de démonstration : rejoins-la avec son code. » et ne se reconnecte pas seule. « Resynchroniser » relance une seule tentative, qui reçoit le même refus.
4. Ouvrir l’Écran de salle depuis la préparation, avec sa clé : la vue se synchronise. Couper puis rétablir le réseau : la reconnexion automatique reprend comme avant.

## Accès d'un vrai Joueur (#10)

Un Joueur arrive par `rejoindre.html`, puis `manette.html?soiree=<UUID>`. Son jeton de reconnexion opaque et son UUID sont rangés par Soirée dans `localStorage` (`js/acces-manette.mjs`, clés `croustyquizz.manette.<soireeId>`), jusqu'au vidage du navigateur, comme le veut le PRD : ils survivent à la mise en veille, à la fermeture de l'onglet et à un QR code rescanné. Si `localStorage` est refusé ou plein, l'accès se replie sur `sessionStorage` : il survit au rechargement, pas à la fermeture de l'onglet. Sans aucun stockage, `rejoindre.html` le dit avant de créer le Joueur ; une écriture qui échoue juste après la création est signalée au lieu d'ouvrir une Manette sans accès. Un QR code rescanné reprend le Joueur de cette Soirée, jamais celui d'une autre. Ouverte sans code, `rejoindre.html` ne reprend que la dernière Soirée rejointe depuis moins de 12 heures : un ancien pseudo ne revient pas tout seul des jours plus tard. Seul un refus du jeton (401 ou 404) ou une Soirée terminée l'efface ; une coupure le garde et réessaie. Les identités de démonstration restent dans `sessionStorage`. Une Manette ouverte avant ce stockage durable avait rangé son jeton et sa Soirée dans `sessionStorage` (`croustyquizz.jeton`, `croustyquizz.soiree`) : `rejoindre.html` les reprend, lit l'UUID du Joueur et le code dans la capture, les range dans `localStorage`, puis efface l'ancien emplacement.

## Module factice

La case « Afficher le module factice » ajoute `factice=1` au lien de l'écran. Cet alias explicite, utilisable avec un jeton de démonstration, charge `modes/factice/<ecran>.mjs` à la place du module réel. Il affiche le contenu du bloc correspondant au vrai `modeEnCours`, sans ajouter de valeur `FACTICE` au contrat HTTP. Sans ce paramètre, les chemins sont exactement `modes/quiz|blindtest|karaoke|freestyle/<ecran>.mjs` ; le Karaoké et le Blind test sont livrés, les autres modules réels restent hors périmètre.

Si le bloc du Mode de jeu est absent ou nul, le gestionnaire démonte le module en place et seule la partie commune reste visible. C’est le cas du bloc du Freestyle, qui est encore réservé. Si le bloc est présent mais que le module ne peut pas être chargé, la coquille affiche un message d’erreur et garde la partie commune visible.

Le Karaoké livre désormais ses trois modules de File d'attente (#93), avec recherche, fiche, inscriptions et désinscriptions personnelles, placements, priorité et limite. Les autres modules réels restent hors périmètre ; le mode Java factice du socle n'est installé que dans les tests (`BancDuSocle`, bloc `quiz`). Les routes `/api/test/.../manches` nécessitent un service de mode. Pour une recette navigateur sans base, `node tools/ecrans/servir-recette.mjs` sert les ressources sur le port local 18080, puis `playwright-cli run-code --filename=tools/ecrans/karaoke-recette-navigateur.mjs` simule HTTP et STOMP à la frontière réseau (après `playwright-cli open`). Les vrais HTML, CSS et modules sont servis localement, avec le client STOMP du WebJar extrait de Maven ; aucun accès au catalogue externe. L'ancienne `recette-navigateur.mjs` conserve la recette des coquilles avec le mode factice. Ces recettes ne vérifient pas l'intégration PostgreSQL.

## Vérification visuelle

Tester Manette à 390 × 844, Console à 1440 × 900 et 1024 × 768, salle à 1920 × 1080. Vérifier la capture, un événement, une coupure puis la recapture, la sortie de Manche et le focus clavier. Le talon affiche le code de la Soirée ouvert aux arrivées. Pour une Soirée enregistrée, il affiche aussi le QR code d’arrivée.

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

### Recette de la note moyenne du Karaoké (#116)

Vérification navigateur à réaliser à 1920 × 1080 puis à 1280 × 720, avec l’Écran de salle
visible à cinq mètres. Reprendre la Soirée de démonstration et ouvrir deux Manettes de votants
en plus de celle de l’Interprète.

1. Terminer un Passage et envoyer les notes 4 et 5. La salle affiche le numéro, la Chanson,
   les Interprètes, le nombre de votes et le temps restant dans le bandeau au-dessus du rouleau.
   Pendant le vote, vérifier l’absence de moyenne sur les trois écrans et de nombre de votes
   sur la Manette. Le changement du nombre garde le bandeau en place et l’appel ou les paroles
   restent dans la zone principale.
2. Laisser le vote se clore. Vérifier « 4,5 sur 5 » et « 2 votes » sur les trois écrans, la note
   personnelle sur la Manette et une seule annonce du résultat au lecteur d’écran. Recharger
   la Manette et vérifier que le résultat reste lisible sans nouvelle annonce. Refaire la
   clôture sans voter et vérifier « Aucun vote ».
3. Démarrer un Passage suivant avant la clôture, puis mettre la Manche en pause après la
   clôture. La moyenne reste dix secondes après la clôture, délai qui continue pendant la
   pause. Refaire avec un Passage suivant simplement appelé. La moyenne reste au-delà des
   dix secondes et s’efface dès « Je suis prêt ». Sauter alors ce Passage, en appeler un autre
   et recharger la salle. Le bandeau échu reste masqué. Refaire en sautant le Passage dès
   l’appel, avant « Je suis prêt ». Ce saut laisse la moyenne affichée.
4. Terminer deux Passages en moins de trente secondes pour obtenir deux votes ouverts et
   mettre la Manche en pause. Vérifier les deux bandeaux au-dessus du rouleau, le plus récent
   en haut, les temps figés sous « Pause », les textes lisibles, le talon visible et la colonne
   latérale sans débordement aux deux résolutions. En terminant la Manche pendant un vote,
   vérifier que les bandeaux disparaissent.
5. En Pilotage Automatique, provoquer une clôture qui appelle le Passage suivant. Vérifier
   que la Manette annonce aussi la moyenne à cette clôture et que la salle garde le bandeau
   jusqu’au démarrage du Passage ou à l’expiration de l’affichage, selon le dernier des deux.

### Recette du Palmarès du Karaoké (#117)

Vérification navigateur à réaliser en Pilotage Manuel avec la Console, l’Écran de salle et
deux Manettes de la même Soirée de démonstration.

1. Terminer trois Passages de Karaoké et les noter depuis la Manette du public, par exemple
   3, 5 et 4 dans l’ordre des Passages. Choisir « Terminer la Manche » pendant le dernier vote,
   après réception de sa note.
2. Vérifier que « Palmarès du Karaoké » apparaît sur la salle et la Console, dans l’ordre des
   notes 5, 4, 3, et que le Classement général conserve ses Points. Vérifier que la Manette
   n’affiche pas de Palmarès. Recharger la salle et la Console et contrôler le même résultat.
3. Refaire le parcours avec un quatrième Passage sans note. Vérifier qu’il figure à la fin de
   la liste de la Console, sans rang et avec « Aucun vote ». Sur la salle, vérifier qu’il n’apparaît
   pas, que « et 1 autre Passage » suit les trois lignes, que les trois souches prennent la teinte
   du numéro de leur Passage et que les lignes restent lisibles à 1920 × 1080 et à 1280 × 720.
4. Démarrer la Manche suivante selon la recette #114. Vérifier que le Palmarès disparaît de
   la salle et de la Console à la réception de la nouvelle vue.
   Tant que le Palmarès est présent, il prend sur la salle la place du Classement général
   et de la note d’attente (Q24, #123) ; une photo à l’affiche le masque comme eux.
   Une fois la Soirée terminée, le Podium final passe avant lui sur la salle (#224) :
   la salle masque le Palmarès, la Console le garde.
5. Terminer une Manche de Karaoké sans Passage terminé dans une Soirée neuve. Vérifier
   « Aucun Passage noté. » sur la salle et la Console.
6. Terminer la Soirée juste après une Manche de Karaoké notée. Vérifier que la salle
   affiche le « Podium final » sans le Palmarès, et que la Console affiche toujours
   le Palmarès du Karaoké.

### Recette du Palmarès au-delà de trois Passages (#236)

L’Écran de salle reçoit le Palmarès complet, comme la Console, mais n’en montre que les trois
premiers Passages classés, suivis d’une ligne « et N autres Passages » quand il y en a d’autres.
N compte aussi les Passages sans vote. La salle ne défile pas et la liste n’a pas de cadre à
hauteur bornée. La Console garde la liste complète.

Le Palmarès réunit les Passages de toutes les Manches de Karaoké de la Soirée : on obtient 3, 5
puis 10 Passages notés en enchaînant trois Manches de Karaoké dans la même Soirée de démonstration,
en Pilotage Manuel, avec la Console, l’Écran de salle et deux Manettes. Noter chaque Passage
depuis une Manette, avec des notes différentes pour départager les rangs. Faire chaque contrôle
à 1920 × 1080 puis à 1280 × 720, en rechargeant la salle à chaque résolution.

1. **3 Passages.** Terminer la première Manche après trois Passages notés. Vérifier sur la salle
   les trois lignes en entier, la troisième comprise, sans ligne « et … autres », sans barre de
   défilement, et le talon d’accès entièrement visible en bas de l’écran.
2. **5 Passages.** Démarrer une deuxième Manche de Karaoké, noter deux Passages de plus, puis la
   terminer. Vérifier sur la salle les trois premiers du Palmarès, puis « et 2 autres Passages »
   sur une seule ligne, sous la liste. La Console affiche les cinq Passages. Le talon reste
   visible et rien ne déborde de la colonne principale.
3. **10 Passages.** Avec une troisième Manche, noter cinq Passages de plus. Vérifier
   « et 7 autres Passages », la même hauteur de liste qu’à l’étape 2 et les dix Passages sur la
   Console. Recharger la salle : même affichage, sans animation.
4. **Passage sans vote.** Dans une quatrième Manche, terminer un Passage sans le noter, puis
   terminer la Manche. Vérifier « et 8 autres Passages » sur la salle, et le Passage sans rang
   en fin de liste sur la Console.
5. **Aucun Passage classé.** Dans une Soirée neuve, terminer une Manche de Karaoké après un ou
   deux Passages sans note. Vérifier « Aucun Passage noté. » seul sur la salle, sans ligne
   « et … autres », et les Passages « Aucun vote » sur la Console.
6. Avec un lecteur d’écran sur la salle, vérifier que la liste annonce trois éléments, chacun lu
   une seule fois, puis la ligne « et N autres Passages ».
7. **Noms longs.** Refaire l’étape 2 avec, sur les trois premiers Passages, un duo de pseudos
   de 30 caractères, dont les Interprètes passent sur deux lignes. Vérifier à 1920 × 1080 puis à
   1280 × 720 que le talon d’accès reste entier à l’écran.

### Musique d'attente (#140)

Sur `/ecran.html`, choisir le dossier de musique depuis la coquille. Les MP3, M4A, OGG et WAV du sous-dossier `Attente`, y compris dans ses sous-dossiers, alimentent la Musique d'attente. La casse et les accents du nom `Attente` sont ignorés. La recherche locale des Chansons écarte ces fichiers. Le navigateur garde le dossier en mémoire le temps de l'ouverture de la page.

Un morceau est écarté jusqu'au prochain choix de dossier si ses métadonnées ne se chargent pas ou si sa lecture échoue deux fois de suite. Une lecture menée à son terme efface l'erreur précédente du morceau. Si le navigateur exige un geste pour activer le son, ce refus ne compte pas comme une erreur de lecture.

Sur l'Écran de salle, les modules appellent `contexte.signalerSonDeManche(true)` au départ du son de Manche et attendent que la promesse rende `true` avant de démarrer le son. Le signal vaut aussi lors d'une capture en cours de Passage pour faire taire la Musique d'attente. Le module appelle `contexte.signalerSonDeManche(false)` après avoir arrêté son lecteur, à la clôture et au démontage. Le Karaoké garde la Musique d'attente pendant l'appel et la suspend dès le départ du Passage, jusqu'à sa clôture, y compris pendant une pause du Passage.

La coordination locale utilise `BroadcastChannel` et les Web Locks. Sans eux (page en `http://` hors `localhost`), chaque fenêtre se croit seule : elle joue dès qu'elle a des morceaux dans `Attente` et signale leur absence sans regarder les autres. La fenêtre ouverte le plus récemment parmi celles dont le son est activé et dont le dossier contient des morceaux dans `Attente` prend la main. Une fenêtre sans dossier, ou dont le dossier n'a rien dans `Attente`, peut activer le son sans faire taire celle qui joue ; son propre son de Manche la fait taire comme ailleurs (#226). Une fenêtre qui a écarté comme illisibles tous les morceaux de son `Attente` fait de même : elle rend la main et n'a plus de morceaux jusqu'au prochain choix de dossier (#250). Le serveur garde un seul signal de disponibilité par Soirée. Dans un même navigateur, les fenêtres de cette Soirée publient ce signal l'une après l'autre. Avant de signaler une absence, une fenêtre vérifie à son tour qu'aucune autre fenêtre de la même Soirée n'a de morceaux, y compris si la dernière qui en avait s'est fermée pendant l'attente (#252). Une fenêtre muette faute de son activé compte aussi : elle a des morceaux. Une fenêtre sans dossier ne signale rien. Un Passage déjà démarré garde son lecteur désigné jusqu'à sa clôture.

Pour la recette, ouvrir deux fenêtres du même profil de navigateur en HTTPS ou sur `localhost`. Choisir dans la première un dossier avec plusieurs morceaux dans `Attente`. Vérifier l'enchaînement puis l'appel d'un Passage de Karaoké. Confirmer le départ, vérifier le silence de la Musique d'attente puis sa reprise à la clôture. Choisir le dossier dans la deuxième fenêtre et vérifier qu'elle prend la main. Fermer la deuxième, puis rouvrir ou recharger l'Écran de salle et choisir à nouveau le dossier. À chaque relais, écouter la fin de la première lecture avant la suivante. Ouvrir ensuite une troisième fenêtre, cliquer sur « Choisir le dossier de musique » puis annuler le sélecteur : le son est activé, mais la fenêtre qui joue continue. Choisir dans cette troisième fenêtre un dossier sans `Attente` : la musique continue toujours dans l'autre. Choisir ensuite un dossier dont `Attente` ne contient que des fichiers illisibles (un texte renommé en `.mp3`, par exemple) : la fenêtre qui jouait se tait un instant puis reprend, et la Console affiche toujours que la musique d'attente joue. Choisir enfin un dossier sans `Attente` dans chacune des autres fenêtres, ou les fermer : la Console n'affiche « Aucun morceau dans Attente » qu'après la dernière. Refaire l'essai avec une Chanson présente uniquement dans `Attente`.

### Pilotage de la Musique d’attente (#141)

La Console coupe ou relance la Musique d’attente depuis la barre de pilotage, y compris sans Manche en cours. La Musique d’attente se tait pendant la resynchronisation puis suit le réglage de la Soirée. La Console affiche « Aucun morceau dans Attente » lorsqu'aucune fenêtre de salle n'a de morceaux dans `Attente`. Le serveur ne reçoit que la présence ou l’absence de morceaux.

1. En `dev` ou `recette`, utiliser un UUID neuf pour une Soirée de démonstration sans Manche. Ouvrir la Console et l’Écran de salle avec les identités correspondantes. La même recette s’applique à une Soirée enregistrée.
2. Hors Manche, choisir un dossier avec des morceaux dans `Attente`. Vérifier leur lecture et l’état affiché dans la Console. Couper la musique, recharger l’Écran de salle et choisir de nouveau le dossier. Vérifier le silence, puis relancer depuis la Console.
3. Couper de nouveau et redémarrer le serveur avec la même base. Après resynchronisation, vérifier que la Console propose toujours de relancer et que la salle reste silencieuse. Choisir un dossier sans morceaux dans `Attente` et vérifier le message dans la Console.
4. Avec un dossier contenant des morceaux, vérifier que la relance pendant un Passage laisse la Chanson audible seule et que la Musique d’attente reprend à la clôture.

### Enregistrement (#71)

Le son reste sur le téléphone de l’Interprète : la Manette capte le micro avec `MediaRecorder`, et le serveur ne reçoit que l’autorisation de la Soirée et l’accord de l’Interprète (ADR 0005, `docs/api/README.md`, « Enregistrement d’un Passage »). La machine d’états de la prise (`modes/karaoke/enregistrement.mjs`) et sa mise en page sur la Manette (`enregistrement-manette.mjs`) sont testées dans `tools/ecrans/karaoke-enregistrement.test.mjs`, avec un faux `MediaRecorder`. Le micro n’existe que dans un contexte sécurisé : sur un vrai téléphone, passer par la recette en HTTPS (Render) ou par un tunnel HTTPS, pas par l’adresse locale du poste en HTTP.

Reprendre la Soirée de démonstration de la recette #114 avec la Console, l’Écran de salle et deux Manettes.

1. Sur la Manette du Joueur 1, inscrire une Chanson. Le ticket en attente propose « Enregistrer mon Passage ». Le toucher : après le reçu, le bouton devient « Retirer mon accord » et « Autoriser le micro » apparaît. Aucun dialogue du navigateur ne s’ouvre avant ce second geste.
2. Toucher « Autoriser le micro » et accepter. La Manette affiche « Micro prêt » et garde l’écran allumé (verrou d’écran) jusqu’à la fin de la prise. Vérifier que la Manette du Joueur 2 et l’Écran de salle n’affichent rien de l’accord, et que la Console ne montre que son bouton « Interdire les Enregistrements ».
3. Appeler le Passage, confirmer « Je suis prêt » et chanter quelques secondes. La Manette affiche « Enregistrement en cours ». Mettre la Manche en pause puis la reprendre, et laisser le Passage se terminer. La Manette annonce « Ton Enregistrement est prêt », et « Tes Enregistrements » propose « Télécharger mon Enregistrement », un lecteur et « Retirer ». Le retrait demande une confirmation. Écouter la prise : la pause n’y laisse pas de blanc. Le fichier porte le titre de la Chanson et la date du Passage.
4. Rejouer en refusant le micro. La Manette affiche « Le micro est refusé. Ton Passage aura lieu sans Enregistrement. » et le Passage se déroule normalement.
5. Rejouer en rechargeant la Manette pendant le Passage. La prise est perdue et la Manette affiche « Le Passage a déjà commencé. Une prise ne redémarre pas après un rechargement. » Couper puis rétablir le réseau pendant une autre prise : elle continue, car le micro ne dépend pas de la connexion. Faire manquer `passage-demarre` à la Manette (couper le réseau juste après « Je suis prêt », puis le rétablir dans les secondes qui suivent) : la capture de reprise lance la prise, en retard. Au-delà de 15 secondes après le départ, la Manette dit que la prise n’a pas pu démarrer avec le Passage. Laisser partir un Passage sans avoir autorisé le micro, puis pendant la demande du navigateur : chaque cas a son propre message, sans parler de rechargement.
6. Depuis la Console, choisir « Interdire les Enregistrements » pendant qu’un Joueur a donné son accord. L’option disparaît des Manettes sans accord. Si une prise tournait, elle s’arrête : la prise interrompue par l’interdiction reste proposée au Joueur, avec la mention « Cette prise a été interrompue. » Pendant l’interdiction, l’Interprète qui avait donné son accord garde seulement « Retirer mon accord », avec « Ton accord est suspendu » ; « Autoriser le micro » ne revient pas. Réautoriser : l’accord donné avant l’interdiction revient, sauf si l’Interprète l’a retiré entre-temps, et le micro attend de nouveau son geste.
7. Terminer la Manche avant de télécharger, puis démarrer une autre Manche de Karaoké : le fichier revient dans « Tes Enregistrements ». Il vit dans la page, hors du module de la Manche, jusqu’au retrait ou à la fermeture de la page. Rien n’est écrit sur le téléphone : un rechargement le perd, et la Manette le dit sous « Tes Enregistrements ».
8. Verrouiller l’écran pendant « Micro prêt » : si le système coupe le micro, la Manette revient à « Autoriser le micro ». Pendant la prise, un micro coupé arrête la prise, et le fichier partiel est proposé comme interrompu. Une prise sans aucun son reste affichée comme un échec.
9. Ouvrir la Manette en HTTP, sur l’adresse locale du poste : « Enregistrer mon Passage » n’est pas proposé, et la Manette explique que la page n’est pas en HTTPS.

L’interdiction ne se règle que depuis la Console, pendant une Manche de Karaoké active, en attendant #180 qui la rattachera au Lieu. Hors Manche de Karaoké, le dernier réglage de la Soirée reste en vigueur.

Le serveur gère déjà un accord par Interprète dans un Passage à plusieurs, mais l’inscription à plusieurs arrive avec #66 : ce cas reste à vérifier à ce moment-là.

À vérifier à la main, sur un téléphone Android (Chrome) et sur un iPhone (Safari) :

- la demande de permission au geste, puis son refus et son blocage durable dans les réglages du site ;
- le format choisi : `audio/mp4` (fichier `.m4a`) dès que le navigateur le sait, sinon `audio/webm;codecs=opus` (fichier `.webm`) ; le fichier se lit hors du navigateur (Fichiers, lecteur du téléphone) ;
- le téléchargement : sur iPhone, le lien `blob:` ouvre un aperçu ou propose « Télécharger » selon la version d’iOS ; vérifier que le fichier arrive dans Fichiers avec son nom ;
- la prise quand l’écran se verrouille ou que l’Interprète change d’application, malgré le verrou d’écran : iOS coupe le micro d’une page en arrière-plan, Android peut le garder ; noter ce qui reste dans le fichier partiel ;
- le micro ouvert entre « Autoriser le micro » et le départ : l’indicateur du système reste allumé pendant l’attente de l’appel ;
- le niveau du son dans un Lieu bruyant, près des enceintes, et la durée d’une prise de quatre minutes ;
- le mode silencieux de l’iPhone et un casque Bluetooth branché pendant la prise.

Recette exécutée le 24/11/2026 sur l'application réelle (PostgreSQL), dans Chromium sans interface, avec le faux micro de Chromium (`--use-fake-device-for-media-stream`). Le Joueur 1 s'inscrit, donne son accord, puis autorise le micro (« Micro prêt »). « Je suis prêt » lance la prise (« Enregistrement en cours »). La pause de la Manche la suspend, et la reprise la relance. Après « Passer », la Manette propose « Télécharger mon Enregistrement » : un WebM d'environ 97 Ko, nommé `Metro Station - Shake It - 2026-11-24.webm`, avec « Écouter ». « Interdire les Enregistrements » retire l'option des Manettes. Rien ne déborde à 390 px. Le format MP4 de Safari, le micro réel et le téléchargement sur iOS restent à vérifier sur téléphone.

### Recharger l’Écran pendant l’appel (#144)

1. Appeler un Passage dont la Chanson correspond à un fichier audio lisible. Pendant l’appel, recharger l’Écran de salle.
2. Une fois le compteur à « 0 s », choisir de nouveau le dossier sur l’Écran. Attendre que la Console affiche « Fichier local » avec le nom du fichier.
3. Confirmer « Je suis prêt » sur la Manette et vérifier que l’Écran joue le fichier.
4. Sur un autre Passage, confirmer avant que l’Écran ait déclaré une lecture. Vérifier que les paroles défilent et que la Console affiche « Paroles seules : Écran lecteur pas prêt ». Si le chargement finit ensuite, vérifier que le fichier reste silencieux.

L’échéance de l’appel sert de repère. Tant que le Passage est appelé, l’Écran prépare le fichier et déclare le résultat de la recherche et du chargement, même si les métadonnées arrivent après l’échéance.

## Blind test (#178)

Les modules `modes/blindtest/` ne contiennent aucune règle de jeu : ils lisent le bloc `blindtest` (contrat de #174, ADR 0009). `logique.mjs` regroupe les calculs purs (position de l'extrait, comptes à rebours, verdict lu dans la vue, envoi espacé du brouillon) et `lecteur-extrait.mjs` le son de la salle. Le module charge `css/blindtest.css` par un unique `<link data-mode="blindtest">`. Les tests `node --test` sont dans `tools/ecrans/blindtest-*.test.mjs`.

- **Écran de salle.** Le module réserve le son de Manche (`contexte.signalerSonDeManche(true)`) dès qu'une Manche de Blind test est affichée : la Musique d'attente se tait pendant toute la Manche, Révélations comprises, et reprend au démontage, après l'arrêt de l'extrait (`signalerSonDeManche(false)`). C'est un écart avec #178, qui la rendait après chaque Révélation : l'extrait continuant pendant la Révélation, les deux sons se chevaucheraient. Rien ne joue tant que la promesse ne rend pas `true`. Sans dossier de musique, la coquille propose « Activer le son » dès le chargement ; tant que le son n'est pas activé, le module l'indique pendant l'extrait. L'extrait est un `<audio>` propre au module, aligné sur `lecture` (position, instant `depuisLe`, pause) selon `contexte.maintenantServeur()`, sans rien signaler au serveur. Une seule fenêtre de salle le joue : parmi les fenêtres dont le son est activé, la première qui obtient le Web Lock `croustyquizz:son:extrait-blindtest` le garde jusqu'au démontage ; les autres restent muettes, en file d'attente sur le verrou, et la suivante prend la relève dès que l'élue se ferme. Sans Web Locks (`http://` hors `localhost`), chaque fenêtre se croit seule, comme pour la Musique d'attente : deux fenêtres de salle ouvertes joueraient alors toutes les deux. Si le navigateur refuse la lecture (`NotAllowedError`), la salle le dit et propose « Lancer l'extrait », qui appelle `play()` pendant le clic, sur l'élément même de l'extrait : il doit ainsi être débloqué aussi sur un navigateur qui débloque élément par élément, comme Safari (à vérifier en recette). Une autre erreur (lien signé expiré, média illisible) affiche « L'extrait ne peut pas être lu. » jusqu'au Morceau suivant ; une lecture interrompue par un changement de source ne dit rien. Un Morceau sauté faute d'extrait (`morceau-saute`, ADR 0009, point 14) est annoncé pendant la préparation du suivant ; une capture ne le connaît pas et ne l'affiche pas. Pendant la Révélation, quelle qu'en soit la cause, la fenêtre qui jouait laisse l'extrait aller jusqu'à sa fin, d'où il était, pour garder l'ambiance : il suit la pause de la Manche et s'arrête au Morceau suivant. La vue ne portant plus de lecture, une salle rechargée pendant la Révélation reste muette, et un extrait fini ne repart pas. Une capture restaure l'extrait à sa position, sans rejouer d'animation ; seuls `buzz-recu`, `proposition-jugee`, `morceau-revele`, `morceau-saute` et `verdict-renverse` animent leur élément, et une capture retire l'animation en cours.
- **Manette.** Le bouton Buzz nomme la Manche et le Morceau (`envoyerBuzz`) ; il suit `peutBuzzer` et la connexion, et ne se réessaie qu'à la main, avec le même `envoiId`. Le module annonce lui-même l'envoi puis le reçu (`annoncer: false`), qui s'efface à la vue suivante. Le Joueur qui a la main voit les champs encore demandés, le compte à rebours calé sur l'échéance du serveur et « Valider ». Le brouillon part 500 ms après la dernière frappe, une requête à la fois, sans nouvel essai automatique, et jamais pendant une coupure : la saisie courante part au retour de la synchronisation. Un échec se dit près des champs, avec « Enregistrer ma saisie », qui renvoie la saisie courante avec un numéro supérieur. Après un rechargement, la saisie et la numérotation repartent de `monBrouillon`. Pendant l'envoi de « Valider » et tant que sa confirmation est inconnue, la saisie est figée : un nouvel essai repart avec le même `envoiId` et le même contenu. Quand le formulaire ou le Buzz disparaît avec le focus, le focus passe au titre. Le verdict vient de la vue publiée, jamais de l'accusé.
- **Console.** La coquille ne porte que les commandes de Soirée : le module pilote sa Manche. Sa barre propose « Pause » ou « Reprendre », « Terminer la Manche » après confirmation, puis l'action citron « Révéler le Morceau », « Morceau suivant » pendant la Révélation, ou « Finir la Manche » après le dernier Morceau. Chaque commande nomme le Morceau affiché (`etapeId`). Le module montre aussi la réponse attendue, l'état de l'extrait, qui a la main avec son temps restant, les parties trouvées et les Joueurs bloqués. En Pilotage Manuel, « Propositions » liste chaque Proposition jugée du Morceau avec le texte saisi : « Accorder » un champ faux non vide, « Refuser » un champ juste (#294, ADR 0009, point 15). La vue publiée apporte les points, les parties et l'annonce de la salle (« L'Animateur accorde l'artiste à Léa. »), que la Manette du Joueur concerné reprend ; le texte saisi ne quitte jamais la Console. En Pilotage Automatique, aucun geste n'est proposé. Le formulaire du programme ajoute une Manche de Blind test avec sa source Deezer (playlist ou genre), son identifiant et son nombre de Morceaux.

### Recette du Blind test

À faire avec l'application lancée en `dev` (`./mvnw spring-boot:run`) et l'accès au catalogue Deezer, l'Écran de salle sur un PC relié à des haut-parleurs et deux Manettes sur des téléphones du même réseau. Le Blind test ne se recette pas avec une Soirée de démonstration (`/test/identite.html`) : le Buzz de Soirée exige une Soirée enregistrée et y répond « Soirée introuvable. ».

1. Créer une Soirée en Pilotage Manuel dans `/preparer-soiree.html`, puis ouvrir sa Console. Dans « Programme », choisir « Blind test », « Playlist », un identifiant de playlist Deezer publique et 3 Morceaux, puis ajouter la Manche. Vérifier « 3 Morceaux · playlist Deezer … » dans le programme. Refaire avec un identifiant inexistant : le refus ne doit rien ajouter.
2. Ouvrir la Soirée, puis l'Écran de salle depuis la préparation, sur `localhost`. Vérifier que « Activer le son » est proposé sans choisir de dossier. Choisir un dossier qui contient `Attente` : la Musique d'attente joue. Rejoindre la Soirée avec deux téléphones par le QR code (pseudos « Léa » et « Sam »).
3. Lancer la Soirée. La Musique d'attente se tait, l'Écran de salle passe de « Le Morceau arrive… » à « Écoutez et buzzez ! » et l'extrait démarre avec le temps restant. Les deux Manettes montrent « Buzzer ».
4. Buzzer avec Léa. L'extrait se met en pause, la salle affiche « Léa a la main » avec le compte à rebours, la Manette de Léa ouvre la saisie avec le focus et celle de Sam affiche « Léa a la main ». Taper l'artiste seul, recharger la Manette de Léa en pleine saisie : la saisie, les champs demandés et le temps restant reviennent. Couper le Wi-Fi du téléphone, modifier la saisie, puis le rétablir : la saisie part au retour de la connexion.
5. Valider. La salle et les Manettes affichent « Artiste trouvé par Léa : … » ; la Manette de Léa dit « Artiste trouvé, +30 points. Le reste est faux : tu es bloqué sur ce Morceau. » et ne propose plus le Buzz. L'extrait reprend d'où il était.
6. Recharger l'Écran de salle pendant l'extrait : il reprend à la bonne position après l'activation du son. Le recharger pendant la saisie de Sam : l'extrait reste en pause.
7. Buzzer avec Sam et laisser passer les 20 s avec un titre juste dans la saisie : le brouillon est jugé, puis la Révélation montre titre, artiste et pochette, et l'extrait continue jusqu'à sa fin. « Morceau suivant » dans la barre du module Console mène au Morceau suivant ; mettre la Manche en pause pendant un extrait, vérifier que la salle et la Manette affichent « Pause » et que « Valider » est désactivé pendant une saisie.
8. Ouvrir une seconde fenêtre d'Écran de salle dans le même navigateur et y activer le son : une seule fenêtre joue l'extrait. Fermer celle qui joue : l'autre prend la relève aussitôt. Sur Safari (Mac ou iPhone), vérifier que l'extrait joue après « Activer le son », ou après « Lancer l'extrait » si la salle le propose.
9. Arbitrage (#294) : sur un Morceau, faire proposer à Léa le bon titre et un artiste avec une faute de frappe. Dans « Propositions » de la Console, cliquer « Accorder » sur l'artiste : la Révélation part, la salle annonce « L'Animateur accorde l'artiste à Léa. », la Manette de Léa le dit et le Classement gagne 30 points. « Refuser » l'artiste : les 30 points repartent et la Révélation reste affichée. Vérifier que le texte saisi n'apparaît ni sur la salle ni sur les Manettes.
10. Après le dernier Morceau, la Manche se termine : les modules se retirent, la Musique d'attente reprend et le Classement général montre les points de Léa et Sam.

Vérifier aussi la Manette à 390 × 844, la Console à 1440 × 900 et 1024 × 768 et la salle à 1920 × 1080 : focus clavier, textes longs, contraste et mouvement réduit.

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

## Recette navigateur du saut à l’échéance de l’appel (#121)

1. Reprendre les étapes 1 à 3 de la recette #120 avec deux Manettes, sans préparer de fichier audio.
2. Laisser le Joueur 1 attendre sans cliquer sur « Je suis prêt ». À zéro, vérifier que sa Manette retire le bouton et affiche « Le temps est écoulé. » si la vue d’appel est encore affichée. Si un envoi en cours reçoit `HORS_DELAI`, vérifier le détail « Le temps pour confirmer est écoulé. ». Rejouer en coupant le réseau du Joueur 1 juste après « Je suis prêt » vers vingt-huit secondes, puis en le rétablissant : « Réessayer le même envoi » apparaît, puis disparaît à zéro.
3. Vérifier que le Joueur 2 est appelé, que son décompte démarre à trente secondes et qu’aucun Vote du public ne s’ouvre. Inscrire un troisième Passage, puis laisser expirer l’appel du Joueur 2. Vérifier que le troisième Passage est appelé. Pour un dernier appel sans attente ni vote ouvert, suivre la recette #122.
4. Rejouer en mettant la Manche en pause lorsqu’il reste dix secondes à l’appel. Attendre plus de trente secondes. Vérifier que le Passage reste appelé et que « Je suis prêt » est indisponible pendant la pause. À la reprise, vérifier que le saut arrive après les dix secondes restantes.
5. Rejouer en Pilotage Manuel. À zéro, vérifier « Le temps est écoulé, tu peux encore confirmer. », puis confirmer et vérifier que le Passage démarre.

Vérification dans un navigateur à effectuer.

## Recette navigateur de la fin automatique du Karaoké (#122)

1. Ouvrir la Console, l’Écran de salle et deux Manettes dans une Soirée neuve en Pilotage Automatique, puis démarrer une Manche de Karaoké comme dans la recette #120. Vérifier qu’elle reste ouverte avec une File d’attente vide au démarrage.
2. Confirmer un premier Passage et inscrire le second pendant le chant. Retirer le second depuis la Console. Vérifier que le chant continue et que, sous « Passage courant », la Console affiche « File d'attente vide : la Manche se terminera après ce Passage et son vote. ».
3. Laisser le premier Passage se terminer, puis voter depuis la Manette du public. Vérifier que la note de la Console devient « File d'attente vide : la Manche se terminera à la clôture du vote en cours. » pendant le vote et que le Karaoké reste affiché. À la clôture du vote, vérifier que le Karaoké se retire des trois écrans comme après « Terminer la Manche », puis recharger les écrans pour vérifier leurs captures.
4. Rejouer et inscrire un Passage depuis une Manette pendant le dernier vote. Vérifier l’appel immédiat et la poursuite de la Manche à la clôture de ce vote.
5. Dans une Manche neuve, laisser expirer l’unique appel. Pendant l’appel, vérifier que la Console affiche « File d'attente vide : la Manche se terminera après ce Passage, puis son vote s'il est chanté. ». Vérifier la fin automatique sur les trois écrans. Dans une autre Manche, mettre l’unique appel en pause, cliquer sur « Passer » et vérifier que la Manche reste en pause jusqu’à « Reprendre », qui la termine.
6. Rejouer le chant et le vote en Pilotage Manuel. Vérifier l’absence de la note sur la Console et la poursuite de la Manche après la clôture du vote.

Vérification dans un navigateur à effectuer.

## Photos de la salle (#72)

La Manette propose « Envoyer une photo » tant que les envois sont ouverts.
Le champ n’impose pas l’appareil photo (`accept="image/*"`, sans `capture`) :
le téléphone propose de prendre une photo ou d’en choisir une dans la
photothèque. La Manette respecte l’orientation de l’image, la réduit à 1600
pixels de côté au plus et la réencode en JPEG avant l’envoi. Ce réencodage
retire les métadonnées EXIF, dont la position GPS. Si le JPEG dépasse 1,5 Mo,
la Manette réduit de nouveau ses dimensions. Une image illisible ou trop lourde
affiche une erreur sous le champ. « Photo envoyée » confirme la réponse du
serveur. Pendant l’envoi, le champ reste focalisable mais `aria-disabled` et
n’ouvre pas le sélecteur. Après une confirmation inconnue ou un refus, choisir
de nouveau le même fichier (même nom, taille et date) renvoie le même JPEG avec
la même `Idempotency-Key` : un envoi déjà enregistré rend son reçu au lieu d’un
doublon. Seuls un autre fichier ou un succès tirent une nouvelle clé. Si
l’Animateur coupe les envois pendant un envoi déjà parti, la Manette masque le
champ sans annuler la requête et affiche sa réponse.

Le serveur borne la mémoire avant de lire le corps : `Content-Length` au-delà de
1,5 Mo refusé, quatre envois lus à la fois au plus, gardes de Soirée, de Joueur
et de file vérifiées sous le verrou avant la lecture puis de nouveau à l’ajout.
Chaque Soirée garde au plus 16 Mo de photos (en attente, acceptées et à
l’affiche) et trente photos acceptées, et toutes les Soirées 64 Mo. Une Soirée
sans commande photo ni changement d’affiche pendant douze heures perd ses
photos et ses reçus ; un réglage coupé reste coupé.

Le panneau « Photos de la salle » de la Console suit l’ordre d’arrivée. Chaque
vignette et son pseudo permettent d’accepter ou de refuser la photo. Pendant
la commande, le bouton garde le focus avec `aria-disabled`. Une fois la photo
modérée, le focus passe au même bouton de la ligne suivante, sinon de la
précédente, ou au titre du panneau quand la file est vide. L’interrupteur
« Recevoir les photos des Joueurs » garde ce libellé fixe, qui est aussi son nom accessible ;
la case indique l’état. Couper les envois masque la commande sur les Manettes
et laisse les photos déjà reçues dans leurs files. L’Écran de salle affiche les
photos dans l’ordre d’acceptation, huit secondes chacune hors Manche, dans le
cadre principal, et masque alors la note d’attente et le classement de salle.
Un début de Manche retire immédiatement la photo à l’affiche. Elle repassera
au prochain créneau. Une Manche en pause occupe la scène.

Recette sur téléphone Android et iPhone : prendre une photo et en choisir une
depuis la photothèque, puis vérifier l’orientation d’un portrait et d’un paysage.
Sur iPhone, inclure une photo au format HEIC et vérifier le message si le
navigateur ne la décode pas. Provoquer un refus et réessayer sans recharger.
Côté Console, couper puis rouvrir les envois et valider des photos dans un ordre
autre que celui de leur arrivée. Sur deux Écrans de salle, recharger l’un d’eux
au milieu d’un affichage. Vérifier la même photo et la même fin d’affichage,
puis démarrer et terminer une Manche. Vérifier le portrait et le paysage à 5 m.

Recette exécutée le 24/11/2026 sur l'application réelle (PostgreSQL), dans Chromium sans
interface : Soirée enregistrée et ouverte, deux Écrans de salle ouverts par leur clé, deux
Manettes arrivées par le code, et la Console. Une image illisible est refusée sous le champ,
puis un nouvel essai passe sans recharger. Un paysage de 3000 × 2000 et un portrait de
1200 × 1800 arrivent en 1600 × 1066 et 1066 × 1600. La photo de Sam, acceptée avant celle
de Léa, passe la première sur les deux Écrans, et un Écran rechargé retrouve la même photo.
L'affiche se vide après le dernier créneau. Couper les envois retire le champ des Manettes.
Aucun des écrans ne déborde. Restent l'orientation EXIF, le HEIC, l'appareil photo sur
téléphone et la lecture à 5 m.
