---
status: accepted
---

# La musique du Karaoké vient d'un fichier local de l'Écran de salle, les paroles de LRCLIB calées en un geste

Le Karaoké a besoin de deux choses : une musique sans voix et des paroles qui défilent au bon moment. LRCLIB fournit des paroles synchronisées et leur durée, pas la musique. Le plan initial était de lire une instrumentale, ou une vidéo karaoké, avec le lecteur YouTube intégré.

Un prototype jetable (#20, 16/11/2026) l'a écarté comme source principale : sur onze vidéos de musique annoncées comme intégrables, neuf sont refusées par le lecteur intégré (erreur 150), les ayants droit bloquant la lecture sur les sites tiers. Les grandes chaînes karaoké sont toutes bloquées. Le lecteur lui-même est fiable quand la vidéo passe (position relue toutes les 14 ms, pause, fin de lecture, volume), et le navigateur exige un vrai clic avant de jouer du son.

## Décision

1. **Source audio, dans cet ordre** :
   - **fichier local** : à la première Manche de Karaoké, l'Animateur choisit sur l'ordinateur de l'Écran de salle un dossier de fichiers audio (MP3, M4A, OGG, WAV). Ce clic débloque aussi le son. La désignation du lecteur exige une Manche active. La fenêtre conserve le dossier pour les Manches de Karaoké suivantes. L'Écran de salle cherche le fichier d'une Chanson par son nom, « Artiste - Titre » ou « Titre - Artiste », sans tenir compte des majuscules, des accents ni de la ponctuation. Aucun fichier ou plusieurs fichiers qui correspondent : on passe à la source suivante, sans choix manuel. Les fichiers ne quittent jamais cette machine : le serveur ne reçoit, ne stocke ni ne diffuse aucun son, et le dépôt n'en contient aucun _(Amendé par l'[ADR 0011](0011-bibliotheque-de-base-de-musique-libre.md) : le serveur sert aussi une Bibliothèque de base de musique libre, consultée après le fichier du Lieu.)_ ;
   - sinon, **vidéo YouTube intégrée**, seulement si le lecteur l'accepte. L'Animateur colle le lien de la vidéo pour une Chanson ; aucune recherche automatique, qui exigerait une clé de l'API YouTube. **Palier 2** : au palier 1, on passe directement du fichier local aux paroles seules ;
   - sinon, **paroles seules** : la musique est passée par le Lieu par ses propres moyens, l'Écran de salle fait défiler les paroles à partir de « Je suis prêt ».
2. **Paroles** : LRCLIB. Parmi ses versions synchronisées d'une Chanson, on retient celle dont la durée est la plus proche du fichier ou de la vidéo. _(Amendé par l'[ADR 0011](0011-bibliotheque-de-base-de-musique-libre.md) : le catalogue de la Bibliothèque de base est consulté avant LRCLIB, et ses Chansons gardent leurs propres paroles.)_
3. **Calage** : la première fois qu'une Chanson passe avec un fichier donné, l'Animateur appuie sur « ça commence » dans la Console d'animation au début du chant ; le décalage est calculé, puis réglable depuis la Console par pas de 0,1 s. Le serveur le mémorise pour cette Chanson et ce fichier, repéré par son nom et sa taille, jamais par son contenu, et l'envoie dans la vue de l'Écran de salle, qui le réapplique ensuite. C'est un nombre, pas du son : il survit à un changement de navigateur ou d'ordinateur.
4. **Fin du Passage** : la fin de lecture signalée par le lecteur (fichier local, puis YouTube au palier 2). Avec les paroles seules, aucun lecteur ne la signale : la fin est la durée de la Chanson augmentée du temps passé en pause, comptée par l'`Horloge`.
5. Le lecteur de l'Écran de salle est derrière un port (`LecteurDeChanson` : charger, lire, pause, reprise, position, fin, volume, erreur), avec une implémentation par source.

## Alternatives écartées

- **Télécharger l'audio de YouTube** (outil ou script) : copie et rediffusion de musique sous droits, interdite par les conditions de YouTube, bloquée depuis les hébergeurs, et exposée dans un dépôt public.
- **Stocker les fichiers sur le serveur** : diffusion publique de musique sous droits ; le disque de l'hébergement est effacé à chaque redémarrage et la base n'est pas faite pour des fichiers (ADR 0005).
- **Spotify** : fichiers chiffrés, compte Premium exigé, usage public et synchronisation avec un affichage interdits par ses conditions.
- **Caler les paroles par une IA** : sur une instrumentale il n'y a pas de voix à repérer, et CroustyQuizz n'embarque aucune IA (ADR 0002).
- **YouTube comme source principale** : trop de vidéos bloquées pour un catalogue.

## Conséquences

- Avec un fichier local, le lecteur est entièrement maîtrisé : fin réelle du Passage, pause, position, et plus tard tonalité et tempo, différés jusqu'ici.
- La qualité du calage dépend du fichier fourni ; le geste de calage et sa mémorisation rattrapent l'écart.
- Une Chanson n'est inscriptible que si LRCLIB a des paroles synchronisées et une durée (règle déjà validée) ; la présence d'un fichier local n'est pas exigée à l'inscription. _(Amendé par l'[ADR 0011](0011-bibliotheque-de-base-de-musique-libre.md) : une Chanson de la Bibliothèque de base est inscriptible sans LRCLIB.)_
- Dans un produit commercial, la source naturelle serait un catalogue karaoké sous licence, branché comme une implémentation de plus de `LecteurDeChanson`.
