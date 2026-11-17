---
version: 1
slug: "src-main-resources-static-maquettes-index-html"
primary_target: "src/main/resources/static/maquettes/index.html"
related_targets: ["src/main/resources/static/maquettes/ecran-de-salle.html","src/main/resources/static/maquettes/manette.html","src/main/resources/static/maquettes/console.html"]
---

# Direction visuelle commune : Console d'animation, Manette, Écran de salle

Mode : Operate pour la Console et la Manette ; l'Écran de salle se regarde sans commande. Les trois interfaces partagent un seul monde, porté par `src/main/resources/static/css/croustyquizz.css` ; les maquettes de `static/maquettes/` servent de référence aux trois membres de l'équipe.

Public : l'Animateur au comptoir (tablette), les Joueurs debout dans un bar sombre (téléphone, luminosité basse), la salle à 5 m de la télé. Tâches : piloter sans perdre le fil, agir en un geste, suivre de loin.

## Direction contract

THESIS : La Soirée est un rouleau de tickets numérotés : chaque Joueur reçoit le sien en entrant, chaque Passage se détache du rouleau quand son tour vient. On refuse la boîte de nuit violette à néons et le tableau de bord blanc de SaaS.

OWN-WORLD : fond brun tabac (#1C1410) et quatre élévations brunes comme seuls neutres ; tickets en aplats francs orange, vert d'eau, bleu, rose, texte encre brun ; jaune citron réservé à ce qu'on touche ; perforations pointillées comme seul ornement ; chiffres en Barlow Condensed extra-gras, texte en Atkinson Hyperlegible.

STORY : la salle comprend qui chante et qui suit ; le Joueur sait où il en est et que son action est enregistrée ; l'Animateur voit le Passage en cours et la suite sans chercher.

FIRST VIEWPORT : Écran de salle Karaoké, paroles sur les deux tiers gauches (ligne en cours à 4,4 % de la largeur), rouleau de tickets à droite, ticket en cours détaché et incliné en tête, talon jaune du code d'accès en bas. Manette : ticket du Joueur en haut, une seule action pleine largeur en bas. Console : barre de pilotage fixe en haut, colonne En cours / À venir, recherche à droite.

FORM : rouleau de tickets de tombola, candidat 5 de la liste ordonnée, seed:99a5a41affbbf664. Geste signature : le ticket qui se détache le long de ses pointillés, sur les trois écrans en même temps.

FINISH : unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Décisions ouvertes

- Logo : un mot-symbole composé dans la typographie du monde, sans image ; un vrai logo reste à dessiner.
