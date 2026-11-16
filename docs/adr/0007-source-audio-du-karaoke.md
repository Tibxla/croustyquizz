---
status: accepted
---

# La musique du Karaoké vient d'un fichier local de l'Écran de salle, les paroles de LRCLIB calées en un geste

Le Karaoké a besoin de deux choses : une musique sans voix et des paroles qui défilent au bon moment. LRCLIB fournit des paroles synchronisées et leur durée, pas la musique. Le plan initial était de lire une instrumentale, ou une vidéo karaoké, avec le lecteur YouTube intégré.

Un prototype jetable (#20, 16/11/2026) l'a écarté comme source principale : sur onze vidéos de musique annoncées comme intégrables, neuf sont refusées par le lecteur intégré (erreur 150), les ayants droit bloquant la lecture sur les sites tiers. Les grandes chaînes karaoké sont toutes bloquées. Le lecteur lui-même est fiable quand la vidéo passe (position relue toutes les 14 ms, pause, fin de lecture, volume), et le navigateur exige un vrai clic avant de jouer du son.

## Décision

1. **Source audio, dans cet ordre** :
   - **fichier local** : au début de la Soirée, l'Animateur choisit sur l'ordinateur de l'Écran de salle un dossier de fichiers audio (MP3, M4A, OGG, WAV). Ce clic débloque aussi le son. L'Écran de salle cherche le fichier d'une Chanson par son titre et son artiste et le lit. Les fichiers ne quittent jamais cette machine : le serveur ne reçoit, ne stocke ni ne diffuse aucun son, et le dépôt n'en contient aucun ;
   - sinon, **vidéo YouTube intégrée**, seulement si le lecteur l'accepte ;
   - sinon, **paroles seules** : la musique est passée par le Lieu par ses propres moyens, l'Écran de salle fait défiler les paroles à partir de « Je suis prêt ».
2. **Paroles** : LRCLIB. Parmi ses versions synchronisées d'une Chanson, on retient celle dont la durée est la plus proche du fichier ou de la vidéo.
3. **Calage** : la première fois qu'une Chanson passe avec un fichier donné, un geste (« ça commence » au début du chant) calcule le décalage, réglable ensuite par pas de 0,1 s. Le décalage est mémorisé pour cette Chanson et ce fichier, et réappliqué ensuite.
4. Le lecteur de l'Écran de salle est derrière un port (`LecteurDeChanson` : charger, lire, pause, reprise, position, fin, volume, erreur), avec une implémentation par source.

## Alternatives écartées

- **Télécharger l'audio de YouTube** (outil ou script) : copie et rediffusion de musique sous droits, interdite par les conditions de YouTube, bloquée depuis les hébergeurs, et exposée dans un dépôt public.
- **Stocker les fichiers sur le serveur** : diffusion publique de musique sous droits ; le disque de l'hébergement est effacé à chaque redémarrage et la base n'est pas faite pour des fichiers (ADR 0005).
- **Spotify** : fichiers chiffrés, compte Premium exigé, usage public et synchronisation avec un affichage interdits par ses conditions.
- **Caler les paroles par une IA** : sur une instrumentale il n'y a pas de voix à repérer, et CroustyQuizz n'embarque aucune IA (ADR 0002).
- **YouTube comme source principale** : trop de vidéos bloquées pour un catalogue.

## Conséquences

- Avec un fichier local, le lecteur est entièrement maîtrisé : fin réelle du Passage, pause, position, et plus tard tonalité et tempo, différés jusqu'ici.
- La qualité du calage dépend du fichier fourni ; le geste de calage et sa mémorisation rattrapent l'écart.
- Une Chanson n'est inscriptible que si LRCLIB a des paroles synchronisées et une durée (règle déjà validée) ; la présence d'un fichier local n'est pas exigée à l'inscription.
- Dans un produit commercial, la source naturelle serait un catalogue karaoké sous licence, branché comme une implémentation de plus de `LecteurDeChanson`.
