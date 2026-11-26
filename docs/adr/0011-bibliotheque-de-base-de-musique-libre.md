---
status: accepted
---

# Une Bibliothèque de base de musique libre est livrée avec l'application

Jusqu'ici, l'Écran de salle n'avait de musique que si l'Animateur choisissait un dossier sur son ordinateur (ADR 0007, ADR 0010, #140). Pour une démonstration, ou pour un Lieu qui démarre, il ne se passait donc rien : pas de Musique d'attente, pas d'Instrumentale pour le Freestyle, et le Karaoké en paroles seules. Le 26 novembre 2026, Tibxla décide de livrer une **Bibliothèque de base** avec l'application, utilisable sans rien préparer (#270). Le Lieu garde la possibilité d'ajouter sa propre musique.

## Décision

1. **Seulement de la musique libre**, dont la licence permet la diffusion publique dans un bar et l'affichage des paroles synchronisées.
   - Acceptées : CC0, domaine public, CC BY, CC BY-SA, Licence Art Libre.
   - Refusées : toute clause NC, parce que les bars en font un usage commercial ; toute clause ND, parce que des paroles synchronisées sont une adaptation de l'œuvre ; tout ce qui vient de YouTube ou d'une plateforme « libre de droits » propriétaire.
2. **Contenu** : la Musique d'attente reprend des morceaux de Kevin MacLeod sous CC BY 4.0. Les Instrumentales, des beats de Kevin MacLeod sous CC BY 4.0 au tempo connu, arrivent avec le Freestyle. Les Chansons sont de vraies chansons chantées sous licence libre, avec des paroles synchronisées que nous préparons nous-mêmes en LRC, puisque LRCLIB n'a rien pour elles.
3. **Les fichiers sont livrés avec l'application**, dans `src/main/resources/static/bibliotheque/`, recompressés pour rester sous 40 Mo au total. `catalogue.json` décrit chaque entrée : son type (`attente`, `instrumentale` ou `chanson`), son titre, son artiste, sa durée, sa licence, l'URL de sa source, son fichier et la taille de celui-ci. Une Instrumentale ajoute son tempo (`tempoBpm`) et le départ du beat (`departBeatMs`). Une Chanson ajoute son fichier LRC et un `numero`, entier positif stable qui forme son identifiant. Le catalogue nomme aussi chaque licence utilisée avec son lien.
4. **Le catalogue est vérifié au démarrage.** Chaque licence doit appartenir à la liste acceptée, avec son lien officiel. Chaque fichier doit exister avec la taille déclarée. Chaque LRC doit se lire en entier, contenir au moins une ligne chantée et tenir dans la durée de la Chanson. Un catalogue invalide empêche l'application de démarrer.
5. **Les fichiers sont servis en statique** sous `/bibliotheque/…`. Ils ne changent qu'avec une version de l'application : les sons et les LRC partent avec un cache d'un an. Un fichier modifié doit donc changer de nom. `catalogue.json` reste revalidé à chaque chargement, pour qu'un navigateur ne garde pas un ancien catalogue après une mise à jour.
6. **La musique du Lieu reste locale.** « Choisir le dossier de musique » ajoute ses fichiers, qui ne quittent pas l'ordinateur. Pour la Musique d'attente, les morceaux d'`Attente` du dossier s'ajoutent à ceux de la Bibliothèque de base ; un morceau du dossier remplace celui de la bibliothèque qui porte le même nom. Pour une Chanson, les sources se suivent dans cet ordre : le fichier du Lieu, trouvé par son nom comme le prévoit l'ADR 0007 ; sinon le fichier de la Bibliothèque de base, trouvé par son URL ; sinon les paroles seules. L'Écran de salle déclare le fichier de la bibliothèque comme un fichier local, par son nom et sa taille : le calage et sa mémoire restent les mêmes.
7. **Paroles** : la recherche de Chansons interroge le catalogue avant LRCLIB et propose les deux, chacune avec son origine. Une Chanson de la bibliothèque prend ses paroles du catalogue, sans consulter LRCLIB : elle reste inscriptible quand LRCLIB est indisponible. Si LRCLIB tombe pendant une recherche, le serveur rend les Chansons du catalogue qui correspondent et ne répond « source indisponible » que s'il n'en a aucune. Le choix de la version la plus proche du fichier (ADR 0007) ne s'applique pas à une Chanson de la bibliothèque : son LRC a été calé sur son fichier.
8. **Crédit** : pendant une Chanson ou un morceau d'attente de la bibliothèque, l'Écran de salle affiche le titre, l'auteur et la licence, discrètement mais lisible à 5 m. Josh Woodward l'exige pour une diffusion en direct, et CC BY l'exige de toute façon. Le crédit d'une Chanson suit l'origine de ses paroles : il reste affiché en paroles seules ou avec le fichier du Lieu. Une page des crédits, générée depuis le catalogue et liée depuis la Console et l'Écran de salle, reprend chaque entrée avec sa licence et sa source. Elle rappelle que les paroles synchronisées des titres sous BY-SA sont publiées sous la même licence.

## Identifiant d'une Chanson de la bibliothèque

L'identifiant d'une Chanson garde sa forme d'UUID opaque, avec un préfixe propre à la bibliothèque (`63726f75-7374-7962-…`). Il encode le `numero` du catalogue, comme l'identifiant LRCLIB encode l'identifiant de LRCLIB (`63726f75-7374-7900-…`). Le serveur reconnaît la famille au préfixe avant toute consultation. En base, ce numéro occupe la colonne de l'identifiant de version source : aucune migration n'est nécessaire.

## Alternatives écartées

- **Stocker la musique en base** : le plan gratuit de Neon est trop petit, et chaque Écran de salle retéléchargerait la musique depuis la base.
- **Téléverser la musique du Lieu vers le serveur** : diffusion de fichiers dont nous ne connaissons pas la licence, et disque de l'hébergement effacé à chaque redémarrage (ADR 0007).
- **Musique sous licence NC ou ND**, ou venue d'une plateforme « libre de droits » propriétaire : interdite dans un bar, ou interdite d'adaptation en paroles synchronisées.
- **Chercher les paroles des Chansons libres dans LRCLIB** : il n'a rien pour elles, et une version prise ailleurs ne serait pas calée sur notre fichier.

## Conséquences

- L'application fonctionne sans aucun fichier du Lieu : Musique d'attente, Chansons de la bibliothèque et, avec le Freestyle, Instrumentales. La disponibilité de la Musique d'attente signalée au serveur (#249, #251, #252) compte les morceaux de la bibliothèque.
- Le déblocage du son garde son clic, « Activer le son » : les navigateurs interdisent la lecture automatique. Une fenêtre sans dossier ne devient lectrice des Chansons qu'après ce clic.
- Le dépôt contient désormais des sons, tous sous licence libre et crédités. `.gitattributes` traite les MP3 en binaire et les LRC en texte à fins de ligne LF.
- Cette décision amende l'ADR 0007 (« le dépôt n'en contient aucun », « Paroles : LRCLIB »). Elle amende aussi l'ADR 0010, encore sur la branche du Freestyle : les Instrumentales de la bibliothèque s'y ajouteront à celles du dossier, et sa note d'amendement viendra avec elles.
