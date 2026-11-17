---
name: CroustyQuizz
description: Le rouleau de tickets d'une Soirée de Quiz, Blind test, Karaoké et Freestyle, lu sur la Console d'animation, la Manette et l'Écran de salle.
colors:
  fond-0: "#1c1410"
  fond-1: "#241a15"
  fond-2: "#2e221b"
  fond-3: "#3a2c24"
  fond-4: "#54443a"
  texte: "#f6ede4"
  texte-2: "#cdbfb2"
  texte-3: "#9c8c7f"
  encre: "#1c1410"
  encre-2: "#3d2c22"
  encre-perforation: "rgb(28 20 16 / 0.55)"
  orange: "#ff7a2f"
  eau: "#7ed8be"
  bleu: "#5aa7ee"
  rose: "#ff9aa8"
  citron: "#f7df4a"
  citron-appuye: "#e5cb2c"
  refus: "#ff8a7a"
  refus-fond: "#4a1f1a"
typography:
  chiffre-geant:
    fontFamily: "Barlow Condensed, Atkinson Hyperlegible, sans-serif"
    fontSize: "5.5rem"
    fontWeight: 800
    lineHeight: 0.8
    fontFeature: "tnum"
  gain:
    fontFamily: "Barlow Condensed, Atkinson Hyperlegible, sans-serif"
    fontSize: "4rem"
    fontWeight: 800
    lineHeight: 0.9
    fontFeature: "tnum"
  code-acces:
    fontFamily: "Barlow Condensed, Atkinson Hyperlegible, sans-serif"
    fontSize: "3rem"
    fontWeight: 800
    lineHeight: 0.95
    letterSpacing: "0.06em"
  numero:
    fontFamily: "Barlow Condensed, Atkinson Hyperlegible, sans-serif"
    fontSize: "2.25rem"
    fontWeight: 800
    lineHeight: 1
    fontFeature: "tnum"
  imprime:
    fontFamily: "Barlow Condensed, Atkinson Hyperlegible, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "0.04em"
  marque:
    fontFamily: "Barlow Condensed, Atkinson Hyperlegible, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "0.02em"
  etiquette:
    fontFamily: "Barlow Condensed, Atkinson Hyperlegible, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "0.12em"
  salle-ligne:
    fontFamily: "Atkinson Hyperlegible, system-ui, sans-serif"
    fontSize: "4.4vw"
    fontWeight: 700
    lineHeight: 1.15
  salle-enonce:
    fontFamily: "Atkinson Hyperlegible, system-ui, sans-serif"
    fontSize: "3.6vw"
    fontWeight: 700
    lineHeight: 1.15
  titre:
    fontFamily: "Atkinson Hyperlegible, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.35
  reponse:
    fontFamily: "Atkinson Hyperlegible, system-ui, sans-serif"
    fontSize: "1.1875rem"
    fontWeight: 700
    lineHeight: 1.35
  corps:
    fontFamily: "Atkinson Hyperlegible, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.45
  libelle:
    fontFamily: "Atkinson Hyperlegible, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 700
    lineHeight: 1.1
rounded:
  rayon: "10px"
  rayon-grand: "18px"
spacing:
  espace-1: "4px"
  espace-2: "8px"
  espace-3: "12px"
  espace-4: "16px"
  espace-5: "24px"
  espace-6: "32px"
  espace-7: "48px"
components:
  bouton:
    backgroundColor: "transparent"
    textColor: "{colors.texte}"
    typography: "{typography.libelle}"
    rounded: "{rounded.rayon}"
    padding: "8px 16px"
    height: "44px"
  bouton-hover:
    backgroundColor: "{colors.fond-2}"
    textColor: "{colors.texte}"
  bouton-action:
    backgroundColor: "{colors.citron}"
    textColor: "{colors.encre}"
    typography: "{typography.libelle}"
    rounded: "{rounded.rayon}"
    padding: "8px 16px"
    height: "44px"
  bouton-action-hover:
    backgroundColor: "{colors.citron-appuye}"
    textColor: "{colors.encre}"
  bouton-action-disabled:
    backgroundColor: "{colors.fond-3}"
    textColor: "{colors.texte-3}"
  bouton-grand:
    typography: "{typography.imprime}"
    rounded: "{rounded.rayon-grand}"
    padding: "16px 24px"
    height: "64px"
    width: "100%"
  bouton-lien:
    backgroundColor: "transparent"
    textColor: "{colors.texte-2}"
    height: "44px"
  ticket-souche:
    backgroundColor: "{colors.orange}"
    textColor: "{colors.encre}"
    typography: "{typography.numero}"
    rounded: "{rounded.rayon}"
    width: "4.5rem"
  ticket-corps:
    backgroundColor: "{colors.orange}"
    textColor: "{colors.encre}"
    rounded: "{rounded.rayon}"
    padding: "12px 16px"
  reponse:
    backgroundColor: "{colors.orange}"
    textColor: "{colors.encre}"
    typography: "{typography.reponse}"
    rounded: "{rounded.rayon}"
    padding: "0 16px 0 0"
    height: "68px"
  reponse-eteinte:
    backgroundColor: "{colors.fond-2}"
    textColor: "{colors.texte-3}"
  talon:
    backgroundColor: "{colors.citron}"
    textColor: "{colors.encre}"
    rounded: "{rounded.rayon}"
    padding: "12px"
  champ:
    backgroundColor: "{colors.fond-1}"
    textColor: "{colors.texte}"
    rounded: "{rounded.rayon}"
    padding: "12px 16px"
    height: "56px"
  champ-erreur:
    backgroundColor: "{colors.refus-fond}"
    textColor: "{colors.refus}"
    rounded: "{rounded.rayon}"
    padding: "8px 12px"
  dialogue:
    backgroundColor: "{colors.fond-2}"
    textColor: "{colors.texte}"
    rounded: "{rounded.rayon-grand}"
    padding: "24px"
    width: "min(36rem, calc(100% - 32px))"
  envoi-en-cours:
    backgroundColor: "{colors.fond-2}"
    textColor: "{colors.texte-2}"
    rounded: "{rounded.rayon}"
    padding: "8px 12px"
    height: "44px"
  envoi-accepte:
    backgroundColor: "{colors.eau}"
    textColor: "{colors.encre}"
    rounded: "{rounded.rayon}"
    padding: "8px 12px"
    height: "44px"
  envoi-refuse:
    backgroundColor: "{colors.refus-fond}"
    textColor: "{colors.refus}"
    rounded: "{rounded.rayon}"
    padding: "8px 12px"
    height: "44px"
  resultat:
    backgroundColor: "{colors.fond-2}"
    textColor: "{colors.texte}"
    rounded: "{rounded.rayon-grand}"
    padding: "16px 24px"
  coupure:
    backgroundColor: "{colors.fond-3}"
    textColor: "{colors.texte}"
    rounded: "{rounded.rayon}"
    padding: "12px 16px"
  panneau:
    backgroundColor: "{colors.fond-1}"
    textColor: "{colors.texte}"
    rounded: "{rounded.rayon-grand}"
    padding: "24px"
  pilotage:
    backgroundColor: "{colors.fond-2}"
    textColor: "{colors.texte}"
    padding: "12px 24px"
---

# Design System: CroustyQuizz

## Overview

**Creative North Star: "Le rouleau de tickets"**

Une Soirée CroustyQuizz est un rouleau de tickets numérotés. Chaque Joueur reçoit le sien en entrant, chaque Passage se détache du rouleau quand son tour vient, et les trois interfaces (Console d'animation, Manette, Écran de salle) montrent le même rouleau à trois distances : le comptoir, la main, cinq mètres. Tout le système découle de cet objet : des aplats de couleur francs portant de l'encre brune, une perforation pointillée avec ses deux encoches rondes, un chiffre condensé très gras qui se lit d'un coup d'œil.

Le fond est un brun tabac profond, pensé pour ne pas éblouir une salle sombre ni un téléphone à luminosité basse ; cinq paliers de brun sont les seuls neutres du produit. Sur ce fond, les tickets prennent quatre teintes (orange, vert d'eau, bleu, rose) qui servent à distinguer, jamais à signifier seules. Le jaune citron est mis de côté pour ce qu'on touche et pour le talon d'accès : il désigne l'action, il ne décore pas. La densité suit l'interface : large et vue de loin sur l'Écran de salle (échelle en largeur d'écran), une action en bas de la Manette quand l'écran a une action unique, une Console dense mais dont la barre de pilotage ne quitte jamais l'écran.

Le monde refuse explicitement la boîte de nuit violette à néons et le tableau de bord blanc de logiciel en ligne. Les perforations sont le seul ornement ; il n'y a ni photo, ni illustration, ni dégradé de couleur.

**Key Characteristics:**
- Fond brun tabac, cinq paliers de brun comme seuls neutres.
- Tickets en aplats orange, eau, bleu, rose, toujours en encre brune.
- Jaune citron réservé à ce qu'on touche, au focus et au talon d'accès.
- Perforation pointillée et encoches rondes comme seul ornement.
- Chiffres et libellés imprimés en Barlow Condensed 800, texte en Atkinson Hyperlegible.
- Un geste signature commun aux trois interfaces : le ticket qui se détache.

## Colors

Un fond chaud et sourd, quatre teintes de ticket vives et une couleur d'action unique.

### Primary
- **Jaune citron** (citron): la seule couleur d'action. Fond du bouton d'action (ce qui fait avancer la Soirée ou l'action du Joueur), anneau de la Réponse choisie, bordure du champ actif, curseur de saisie, contour de focus, sélection de texte, et fond du talon d'accès. Sa variante **Citron appuyé** (citron-appuye) sert au survol du bouton d'action.

### Secondary
- **Orange ticket** (orange): première teinte de ticket et de Réponse (A), fond de l'accent du mot-symbole, barre du minuteur, numéro du Passage dans le titre des paroles, et repli de la partie chantée des paroles.
- **Vert d'eau** (eau): deuxième teinte de ticket et de Réponse (B), ticket par défaut, pastille « connecté » de la Console, fond de l'envoi accepté et couleur du gain d'une Question.
- **Bleu ticket** (bleu): troisième teinte de ticket et de Réponse (C).
- **Rose ticket** (rose): quatrième teinte de ticket et de Réponse (D).

### Tertiary
- **Refus** (refus) sur **Fond de refus** (refus-fond): le seul état d'erreur, pour un envoi refusé par le serveur.

### Neutral
- **Brun tabac** (fond-0): fond de page des trois interfaces, fond d'encoche par défaut.
- **Brun comptoir** (fond-1): colonne latérale de l'Écran de salle, panneaux de la Console, fond des champs.
- **Brun banquette** (fond-2): barre de pilotage, survol des boutons et des lignes, envoi en cours, résultat, Réponse éteinte.
- **Brun bois** (fond-3): bandeau de coupure, fond de la barre du minuteur, bouton d'action désactivé, filets de séparation.
- **Brun bouchon** (fond-4): bordure des boutons et des champs au repos, barre de défilement.
- **Crème** (texte): texte principal sur fond brun.
- **Crème fanée** (texte-2): texte secondaire, aides, titres de colonne de la salle.
- **Crème usée** (texte-3): texte désactivé, lignes de paroles hors ligne en cours, textes indicatifs des champs.
- **Encre brune** (encre): tout texte posé sur une teinte de ticket ou sur le citron. Même valeur que fond-0, rôle distinct.
- **Encre délavée** (encre-2): texte secondaire sur un ticket.
- **Encre de perforation** (encre-perforation): l'encre brune à 55 %, en pointillé de 3 px, pour toutes les perforations. À ne pas confondre avec `--perforation`, la largeur de la souche.

### Named Rules
**La règle du Citron.** Le jaune citron ne marque que ce qu'on touche (bouton d'action, Réponse choisie, champ actif) et le focus, plus le talon d'accès. Sur l'Écran de salle, il n'apparaît nulle part ailleurs que sur le talon.

**La règle des Aplats.** Un ticket est un aplat franc portant de l'encre brune : jamais de dégradé de couleur, jamais de texte clair sur une teinte de ticket.

**La règle de la Couleur jamais seule.** Une teinte ne porte jamais seule une information : chaque Réponse a sa lettre et son texte, chaque état d'envoi a son libellé, la bonne Réponse est dite en toutes lettres.

**La règle de la Teinte du Passage.** La partie déjà chantée de la ligne en cours prend la teinte du ticket du Passage en cours, transmise par la variable `--teinte-passage` (repli : orange).

## Typography

**Display Font:** Barlow Condensed 800 (repli Atkinson Hyperlegible, sans-serif)
**Body Font:** Atkinson Hyperlegible 400 et 700 (repli system-ui, sans-serif)

**Character:** Un chiffre de ticket de tombola, étroit et très gras, posé à côté d'un texte courant dessiné pour la lisibilité en basse vision. Les deux familles sont auto-hébergées sous licence OFL.

### Hierarchy
- **Chiffre géant** (Barlow 800, 5.5rem, 0.8): le numéro du ticket du Joueur sur sa Manette.
- **Gain** (Barlow 800, 4rem, 0.9): les points validés d'une Question, en vert d'eau.
- **Code d'accès** (Barlow 800, 3rem, 0.95, espacement 0.06em): le code du talon.
- **Numéro** (Barlow 800, 2.25rem, 1): la souche d'un ticket ; la lettre d'une Réponse est à 2rem.
- **Imprimé** (Barlow 800, 1.75rem, 1, espacement 0.04em, capitales): libellé des boutons grand format.
- **Mot-symbole** (Barlow 800, 1.5rem, 1, espacement 0.02em, capitales): « Crousty » suivi de « Quizz » sur un accent orange ; le Mode de jeu de la barre de pilotage reprend ce corps.
- **Étiquette** (Barlow 800, 0.8125rem, 1, espacement 0.12em, capitales): l'état imprimé dans le coin d'un ticket (« Sur scène »), l'étiquette du ticket de la Manette (1rem).
- **Ligne chantée** (Atkinson 700, 4.4vw, 1.15): la ligne en cours des paroles sur l'Écran de salle ; les lignes voisines à 2.6vw en texte-3, 28ch au plus.
- **Énoncé de salle** (Atkinson 700, 3.6vw, 1.15): l'énoncé d'une Question sur l'Écran de salle, 30ch au plus, lignes équilibrées.
- **Titre** (Atkinson 700, 1.5rem, 1.35): le titre d'un écran de Manette ; les titres de panneau de la Console sont à 1.375rem.
- **Réponse** (Atkinson 700, 1.1875rem, 1.35): le texte d'une Réponse sur la Manette.
- **Corps** (Atkinson 400, 1rem, 1.45): texte courant ; la Manette monte à 1.125rem.
- **Libellé** (Atkinson 700, 1rem, 1.1): libellé des boutons ordinaires.

L'Écran de salle a sa propre échelle, entièrement en largeur d'écran (base 1.15vw, titres de colonne 1.5vw, numéro de souche 3.6vw), pour rester lisible à 5 m quelle que soit la télé.

### Named Rules
**La règle du Ticket imprimé.** Barlow Condensed 800 sert aux chiffres et aux libellés courts imprimés comme sur un ticket : boutons grand format, Mode de jeu, titres de colonne de l'Écran de salle, état d'un ticket, lettres des Réponses, mot-symbole. Jamais au texte courant, jamais à une phrase, jamais à un surtitre posé au-dessus d'un titre.

**La règle des Chiffres tabulaires.** Tout nombre qui change sous les yeux (numéro, score, temps restant, durée) s'affiche en chiffres tabulaires.

## Layout

Trois mises en page pour un seul monde, sur une échelle d'espacement de 4 px (4, 8, 12, 16, 24, 32, 48).

- **Écran de salle** : plein écran sans défilement, deux colonnes (contenu principal à gauche, colonne de 30vw à droite sur le brun comptoir). Le Karaoké met les paroles sur la gauche et le rouleau de la File d'attente à droite, ticket en cours détaché en tête, talon d'accès en bas. Le Quiz met l'énoncé, les quatre Réponses en grille 2 × 2 et le minuteur à gauche, les souches des Joueurs qui ont répondu à droite. Toutes les mesures sont en vw.
- **Manette** : une colonne pleine hauteur (100svh), marges de 16 et 24 px, zone de sécurité du bas respectée. Le ticket du Joueur en haut ; quand l'écran a une action unique, elle est pleine largeur et poussée en bas, à portée du pouce. La File d'attente du Karaoké garde les tickets personnels en haut et la recherche dessous ; seule l'inscription dans la fiche est une action citron.
- **Console d'animation** : barre de pilotage collée en haut, puis un plan en deux colonnes (File d'attente de 360 à 440 px, catalogue à droite). À 1200 px et moins, la colonne passe de 300 à 360 px et les marges se resserrent ; à 860 px et moins, une seule colonne.

### Named Rules
**La règle de la Barre sur une ligne.** À la largeur tablette (1200 px et moins), la barre de pilotage reste sur une ligne : Pause en icône seule, « Passer » en libellé court, et chaque commande garde son nom accessible.

**La règle de l'Adresse insécable.** L'adresse d'accès ne se coupe jamais sur un trait d'union : chaque segment est insécable et la seule coupure permise tombe avant le point.

## Elevation & Depth

Le système est plat. La profondeur vient des paliers de brun (fond-0 à fond-4) : une surface plus haute est un brun plus clair. Deux ombres existent ; le dialogue modal reprend celle du détachement pour isoler la décision.

### Shadow Vocabulary
- **Détachement** (`box-shadow: 0 10px 24px rgb(0 0 0 / 0.45)`): le corps d'un ticket détaché et le dialogue modal (`--ombre`).
- **Barre collée** (`box-shadow: 0 4px 12px rgb(0 0 0 / 0.35)`): la barre de pilotage de la Console, qui reste au-dessus du contenu qui défile.

### Named Rules
**La règle de l'Ombre du détachement.** Une ombre portée signifie qu'un ticket a quitté le rouleau ou qu'un dialogue exige une décision. Hors du ticket détaché, du dialogue modal et de la barre de pilotage, rien ne flotte.

## Shapes

Des coins doucement arrondis (rayon, 10 px) pour les tickets, boutons, champs et bandeaux ; des coins plus ronds (rayon-grand, 18 px) pour les grandes pièces : bouton grand format, ticket de la Manette, panneaux de la Console, résultat. Les seules formes rondes sont les encoches de perforation (cercles de 14 px, 22 px sur la Manette, 1vw sur la salle) et la pastille d'état.

Un ticket se compose d'une souche et d'un corps séparés par une perforation : pointillé de 3 px à l'encre de perforation, une encoche ronde à chaque bout. L'encoche est un trou : sa couleur doit valoir le fond de la surface qui porte le ticket, transmise par `--fond-encoche` (brun comptoir dans la colonne de la salle et dans la Console). Dans un rouleau, les tickets sont collés les uns aux autres, séparés par une perforation horizontale, et seuls les coins extérieurs de la bande restent arrondis. Quand la souche reste seule, un bord net de 3 px marque la déchirure.

## Components

### Buttons
Des boutons francs, à bordure épaisse, qui se laissent viser debout et d'une main.
- **Shape:** coins doucement arrondis (10 px), bordure de 2 px, hauteur minimale de 44 px.
- **Ordinaire:** fond transparent, bordure brun bouchon, texte crème en Atkinson 700.
- **Action:** fond et bordure citron, texte encre. Un seul par écran, pour ce qui fait avancer la Soirée ou l'action du Joueur.
- **Grand format:** pleine largeur, 64 px de haut, coins de 18 px, libellé Barlow 800 en capitales à 1.75rem. Sur la Manette, quand l'écran a une action unique, le bouton grand format la porte.
- **Lien:** sans bordure, texte crème fanée souligné, pour l'action de repli (« Me désinscrire », « Lancer maintenant »).
- **Hover / Focus:** survol en brun banquette avec une bordure crème usée ; l'action passe en citron appuyé ; enfoncement de 1 px à l'appui ; transitions de 120 ms sur la courbe de sortie. Le focus est un contour citron de 3 px décalé de 3 px, sur tout élément.
- **Désactivé:** texte crème usée et bordure brun bois ; l'action désactivée passe sur brun bois.
- **Icône:** icônes Lucide à trait de 2 px, 1.25em, dans la couleur du texte ; un bouton qui n'a que son icône porte un nom accessible.

### Inputs / Fields
- **Style:** fond brun comptoir, bordure de 2 px brun bouchon, coins de 10 px, 56 px de haut sur la Manette (52 px dans la recherche de la Console), texte à 1.25rem, curseur citron.
- **Focus:** la bordure passe au citron ; la recherche de la Console allume sa bordure dès que son champ a le focus.
- **Aide:** une ligne en crème fanée sous le champ.
- **Erreur (`.champ--erreur`):** message sous le champ en `--refus` sur `--refus-fond`, avec un texte explicite toujours présent quand l'erreur est affichée. Relier ce message au champ par `aria-describedby` et poser `aria-invalid="true"` ; la couleur ne suffit jamais. La classe se pose sur le message, pas sur le conteneur `.champ`.
- `.champ`, `.recherche` et `.panneau` sont dans `croustyquizz.css` et utilisables sur les trois écrans.

### Dialogue modal
`<dialog class="dialogue" aria-labelledby="titre-dialogue">` : fond `--fond-2`, texte `--texte`, ombre `--ombre`, coins `--rayon-grand`, marge intérieure de 24 px. Le voile reprend le brun tabac à 80 %. Donner un titre, un contrôle de fermeture et une action nommée. Utiliser `ouvrirDialogue` (`js/dialogue.mjs`) : ouverture par `showModal()`, focus sur le contrôle `autofocus` ou le premier contrôle (à défaut, rendre le dialogue focalisable avec `tabindex="-1"`), fermeture native par Échap ou `close()`, puis retour au contrôle d'origine s'il existe encore. Le focus conserve son contour citron visible.

### Navigation
- **Barre de pilotage (Console):** collée en haut, brun banquette, filet brun bois et ombre de barre collée. À gauche le mot-symbole et la Soirée, au centre le Mode de jeu en Barlow 800 avec la progression et les indicateurs, à droite les commandes, l'action citron en dernier.

### Ticket
La pièce maîtresse. Une souche numérotée (4.5rem de large, numéro Barlow 800) et un corps (nom en Atkinson 700 à 1.125rem, détail en encre délavée), chacun dans la teinte du ticket, séparés par la perforation. L'état éventuel s'imprime dans le coin haut droit du corps, en étiquette. Le ticket de la Manette est la même pièce posée à la verticale : numéro géant en haut, détail en bas, encoches sur les côtés.

### Rouleau
Des tickets empilés sans espace et reliés par une perforation horizontale, comme une bande continue : la File d'attente sur la salle et dans la Console. La teinte dépend du numéro modulo 4 : orange (0), eau (1), bleu (2), rose (3). Sur la Console, un ticket sélectionné garde sa grille, porte `aria-pressed="true"`, un contour épais dans sa teinte et un fond légèrement éclairci, sans anneau citron ; le focus commun reste visible.

### Réponses
Une Réponse a l'anatomie d'un ticket : la lettre (Barlow 800) dans la souche, une perforation, puis le texte. Elle porte toujours un texte. Les quatre Réponses prennent les quatre teintes dans l'ordre (A orange, B eau, C bleu, D rose). La Réponse choisie s'entoure d'un anneau citron de 3 px séparé par 3 px de brun tabac ; les autres s'éteignent en brun banquette, texte crème usée ; la bonne Réponse le dit en toutes lettres. Sur l'Écran de salle, les Réponses ne sont pas des commandes et ne portent jamais l'anneau citron.

### Talon d'accès
Le seul aplat citron de l'Écran de salle : QR code sur carré blanc, phrase d'invitation, code d'accès en Barlow 800, adresse insécable.

### Envoi, résultat, coupure
- **Envoi en cours:** brun banquette, crème fanée, roue de 800 ms ; rien n'est encore annoncé. Après une panne réseau sur mutation, `.envoi--en-cours` signifie une confirmation inconnue et affiche « Confirmation indisponible : vérifie l'état affiché. ». Le délai du Karaoké affiche « Confirmation à vérifier. » près de l'action, sans inventer de refus ni de succès.
- **Envoi accepté:** vert d'eau, encre, coche, libellé qui dit ce qui est enregistré.
- **Envoi refusé:** fond de refus, texte de refus, avec l'explication.
- **Résultat:** les points validés en gain vert d'eau, puis le total et le rang.
- **Coupure:** bandeau brun bois avec icône, qui dit ce que la Manette sait et qu'elle se reconnecte.

### Mot-symbole
« Crousty » en crème suivi de « Quizz » en encre sur un accent orange, en Barlow 800 capitales. Il tient lieu de logo tant qu'aucun logo n'est dessiné.

### Named Rules
**La règle du Détachement.** Le geste signature : quand son tour vient, le corps du ticket se détache le long de la perforation, s'incline et prend l'ombre de détachement, pendant que la souche reste sur le rouleau. Même mécanique sur les trois interfaces (900 ms sur la courbe de sortie), réduite à un instant si le mouvement réduit est demandé.

**La règle du Serveur d'abord.** Aucun succès n'est annoncé sans réponse du serveur, et la valeur validée d'un score s'affiche telle quelle avant toute animation, qui part de cette valeur.

## Do's and Don'ts

### Do:
- **Do** poser toute teinte de ticket ou tout citron sous de l'encre brune (encre ou encre-2), jamais sous du crème.
- **Do** réserver le citron au bouton d'action, à la Réponse choisie, au champ actif, au focus et au talon d'accès.
- **Do** donner à chaque Réponse sa lettre et son texte, et à chaque état son libellé.
- **Do** composer en Barlow Condensed 800 les chiffres et les libellés courts imprimés sur un ticket, et tout le reste en Atkinson Hyperlegible.
- **Do** régler `--fond-encoche` sur le fond de la surface qui porte un ticket.
- **Do** attendre la réponse du serveur avant d'annoncer un envoi réussi, et afficher la valeur validée d'un score avant de l'animer.
- **Do** garder la barre de pilotage sur une ligne à 1200 px et moins, avec Pause en icône seule et « Passer » en libellé court.
- **Do** n'utiliser que des données de démonstration fictives et des ressources auto-hébergées.

### Don't:
- **Don't** mettre du citron sur l'Écran de salle ailleurs que sur le talon d'accès.
- **Don't** composer du texte courant, une phrase ou un surtitre en Barlow Condensed.
- **Don't** laisser une couleur porter seule une information.
- **Don't** poser un dégradé de couleur sur un ticket.
- **Don't** ajouter d'ornement autre que la perforation et ses encoches.
- **Don't** faire flotter une surface avec une ombre hors du ticket détaché, du dialogue modal et de la barre de pilotage.
- **Don't** couper l'adresse d'accès sur un trait d'union.
- **Don't** charger une police, une icône ou un script depuis une ressource externe.
- **Don't** glisser vers la boîte de nuit violette à néons ni vers le tableau de bord blanc de logiciel en ligne.
