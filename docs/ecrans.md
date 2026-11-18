# Coquilles des trois écrans (#80)

Ouvrir `/test/identite.html?soiree=<UUID>` en profil `dev` ou `recette`, dans un onglet par rôle. Le jeton `demo:TYPE:soireeId:acteurId` reste dans `sessionStorage` (`croustyquizz.jeton`), jamais dans l'URL. La page demande une vue au serveur avant de déposer l'identité ; le Joueur prend son UUID stable dans cette projection. La page est physiquement dans `resources/demonstration/`, hors de `static/`, et servie par un contrôleur `@Profile({"dev", "recette"})` à l'URL demandée : aucun fichier statique ne contourne cette restriction en production.

Les destinations et captures sont celles de `docs/api/`. Les trois pages lisent `?soiree=<UUID>`. La Console utilise le Bearer de démonstration ; sans ce jeton, elle demande `/api/csrf` et transmet session et CSRF, en prévision de #5. Le serveur de production refuse encore les identités tant que #5/#10 ne sont pas installées. La salle n'a aucune commande ; la Console et la Manette ont un bouton de recapture. Les commandes métier appartiendront aux modules des modes.

## Module factice

La case « Afficher le module factice » ajoute `factice=1` au lien de l'écran. Cet alias explicite, utilisable avec un jeton de démonstration, charge `modes/factice/<ecran>.mjs` à la place du module réel. Il affiche le contenu du bloc correspondant au vrai `modeEnCours`, sans ajouter de valeur `FACTICE` au contrat HTTP. Sans ce paramètre, les chemins sont exactement `modes/quiz|blindtest|karaoke|freestyle/<ecran>.mjs` ; les modules réels restent hors périmètre. Un module absent produit un message explicite, la partie commune reste visible.

Le socle #79 ne livre aucun service de mode en application : son mode Java factice n'est installé que dans les tests (`BancDuSocle`, bloc `quiz`). Les routes `/api/test/.../manches` nécessitent un tel service. Pour une recette navigateur sans base, `node tools/ecrans/servir-recette.mjs` sert les ressources sur le port local 18080, puis `playwright-cli run-code --filename=tools/ecrans/recette-navigateur.mjs` fournit des captures/événements STOMP fictifs à la frontière réseau (après `playwright-cli open`). Il exerce les vrais modules et les vraies coquilles, avec le client STOMP du WebJar extrait de Maven ; voir l'en-tête du script. Cela vérifie le rendu et le raccordement navigateur, pas l'intégration PostgreSQL.

## Vérification visuelle

Tester Manette à 390 × 844, Console à 1440 × 900 et 1024 × 768, salle à 1920 × 1080. Vérifier la capture, un événement, une coupure puis la recapture, la sortie de Manche et le focus clavier. Le talon n'affiche que le code effectivement reçu ; l'arrivée par QR code reste à #6/#10. Les maquettes de référence et les deux pages du prototype de Buzz restent utilisables.

Le contrat de module est documenté dans `AGENTS.md`, section « Coder un Mode de jeu ». `npm test --prefix tools/api` couvre contrats, synchronisation et logique des coquilles. `IdentiteDeTestParProfilTest` vérifie la route HTML en dev, recette, prod+recette, prod et sans profil de démonstration, sans Docker.
