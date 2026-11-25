# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **L'Animateur**, personne d'un Lieu (bar, salle, association) qui organise des Soirées. Il pilote depuis la Console d'animation, sur un ordinateur ou une tablette, souvent entre deux commandes au comptoir : il doit voir d'un coup d'œil où en est la Soirée et ce qu'il doit faire ensuite. C'est le seul rôle qui a un compte.
- **Les Joueurs**, clients du Lieu, sans compte. Ils arrivent en scannant un QR code et jouent depuis leur Manette, une page web sur leur téléphone, debout ou à table, une boisson à la main, dans le bruit.
- **La salle**, qui regarde l'Écran de salle (télé ou projecteur) sans le toucher : Questions, paroles, Grilles de rimes, scores, invitation à rejoindre.

Le vocabulaire est celui de `GLOSSARY.md`.

## Product Purpose

CroustyQuizz transforme un Lieu en plateau de jeu le temps d'une Soirée : Quiz, Blind test, Karaoké et Freestyle s'enchaînent en Manches, les Joueurs répondent, buzzent, s'inscrivent pour chanter ou rapper et votent depuis leur téléphone, et la salle suit tout sur l'Écran de salle. Le produit remplace les feuilles de réponses, l'enceinte et le papier où l'ordre de passage se perd. Réussir, c'est une Soirée qui tourne sans accroc, même quand l'Animateur doit servir au bar.

Projet de groupe du M1 MIAGE (Université Paris Nanterre), noté individuellement ; le produit est un proof-of-concept démontré en conditions de salle.

## Positioning

- **Un seul accès pour toute la Soirée** : un QR code, un pseudo, puis la Manette change d'elle-même à chaque Manche, sans jamais rescanner.
- **Pilotage automatique avec reprise en main** : la Soirée s'enchaîne seule, l'Animateur peut toujours intervenir.
- **Préparation par l'Assistant de l'Animateur** (Claude, ChatGPT…) via un serveur MCP : CroustyQuizz n'embarque aucune IA.
- **Le public juge les volontaires** au Karaoké et au Freestyle ; aucune voix n'est analysée.

## Operating Context

- Trois interfaces au travail distinct, ouvertes en même temps : Console d'animation (piloter), Manette (agir), Écran de salle (regarder). L'Écran de salle envoie les seules commandes de son lecteur audio. Les actions de jeu passent par la Console et la Manette.
- Lieux sombres, bruyants, télé à plusieurs mètres, réseau Wi-Fi ou 4G instable ; téléphones de toutes marques, souvent à luminosité basse.
- Soirées de plusieurs heures, jusqu'à 60 Joueurs connectés.
- Le serveur fait autorité sur l'identité, le temps, l'ordre des actions et les points ; chaque écran affiche la Vue synchronisée qu'il reçoit (ADR 0003, 0004).

## Capabilities and Constraints

- Front en HTML et JavaScript servis par Spring Boot, sans framework ni chaîne de build (ADR 0001).
- Modes de jeu : Quiz, Blind test, Karaoké, Freestyle. Formats Solo et Équipe, Pilotage Manuel ou Automatique.
- Le Karaoké et le Freestyle ne font marquer de points à personne ; ils ont leur palmarès, séparé du Classement général.
- L'Enregistrement d'un Passage reste sur la Manette (ADR 0005).
- Interface et contenus en français uniquement.
- L’Écran de salle joue la musique du Karaoké depuis un dossier local (ADR 0007). Les fichiers restent sur son ordinateur.

## Brand Commitments

- Le nom est **CroustyQuizz**, conservé pour tous les Modes de jeu et traité comme une marque de soirée. Il n'existe encore ni logo ni ressource graphique : ils naissent avec la direction visuelle.
- **Tutoiement partout**, ton complice de bar, jamais vulgaire ni moqueur. Dans la Console, l'Animateur travaille : textes brefs et factuels.
- Identité propre : on reprend des parcours et des interactions de produits étudiés (PRD #2, « Étude de l'existant »), jamais leurs polices, couleurs de marque, logos, images ou textes.

## Evidence on Hand

Aucun témoignage, aucun client, aucun test en salle réel, aucune statistique d'usage. Les écrans ne doivent rien inventer de tel. Les contenus de démonstration (Questions, Chansons, Instrumentales) sont à fournir et à sourcer.

## Product Principles

1. **La Soirée d'abord** : chaque écran sert le moment collectif ; une action ne doit jamais casser l'ambiance de la salle.
2. **Ce qui s'affiche est vrai** : un envoi n'est annoncé réussi qu'après réponse du serveur, un score validé passe avant son animation, un refus est expliqué.
3. **Une chose à faire à la fois** : la Manette propose l'action du moment ; la Console garde le pilotage visible et sépare « En cours » et « À venir ».
4. **Personne n'est obligé** : jouer ne demande ni compte, ni coordonnées, ni de monter sur scène.
5. **L'Animateur garde la main** : l'automatique aide, il ne décide jamais à sa place sans retour possible.

## Accessibility & Inclusion

- Cible : WCAG 2.2 niveau AA.
- Écran de salle lisible à 5 m dans un bar sombre.
- Manette utilisable d'une main, debout, dans le bruit, avec une luminosité basse ; jamais d'information portée par le seul son du téléphone.
- Une couleur ne porte jamais seule une information : les boutons de Réponse ont un texte, les états ont un libellé.
- Noms accessibles pour les boutons icônes et les dialogues ; le focus revient au contrôle d'origine à la fermeture d'un dialogue.
