---
status: accepted
---

# Le Blind test : extrait Deezer sans divulgation, temps mené par le serveur, points par partie trouvée

Le PRD (#2) fixe le cadre du Blind test : catalogue Deezer (extraits de 30 s sans clé, liens signés qui expirent, identifiant Deezer stocké et lien redemandé au moment de jouer), premier Buzz qui donne la main, Joueur bloqué jusqu'au Morceau suivant après une erreur, correction tolérante, verdict renversable par l'Animateur en Pilotage Manuel, et aucune bonne réponse sur l'Écran de salle avant sa révélation. Il laissait ouverts la diffusion de l'extrait, le temps, le barème et ce qui se passe après un Buzz. Ces points ont été tranchés le 24/11/2026 avant de découper #17.

## Décision

1. **Morceaux d'une Manche** : les réglages portent une playlist Deezer ou un genre Deezer (son classement), et un nombre de Morceaux. Le serveur les tire au hasard parmi ceux qui ont un extrait, sans doublon, et stocke leur identifiant Deezer. La recherche Morceau par Morceau depuis la Console vient avec #19.
2. **Extrait sans divulgation** : la vue de l'Écran de salle porte seulement le lien d'extrait signé rendu par Deezer, redemandé par le serveur quand le Morceau commence ; ni identifiant Deezer, ni titre, ni artiste avant leur révélation. L'Écran le lit avec un `<audio>`, directement depuis Deezer : le serveur ne relaie aucun son.
3. **Temps mené par le serveur** : l'`Horloge` compte les 30 s d'extrait hors pauses et les délais de saisie. Le serveur publie l'état de lecture (en lecture depuis telle position à tel instant, ou en pause à telle position) ; l'Écran de salle aligne son extrait dessus et ne signale rien. Un Écran rechargé reprend à la bonne position.
4. **Son** : tant que le son n'est pas activé, l'Écran de salle affiche « Activer le son » ; un clic de l'Animateur à l'installation suffit pour la session, les navigateurs exigeant un geste avant de jouer du son.
5. **Buzz** : les Manettes buzzent par la route commune `envoyerBuzz`. Pendant une Manche de Blind test, le socle la transmet au déroulement comme une `ActionJoueur` (une seule entrée pour toutes les actions de Joueur, PRD), qui fixe l'ordre, la main et les blocages. Le premier Buzz accepté met l'extrait en pause et donne la main.
6. **Proposition de titre** : deux champs, Titre et Artiste, et 20 s pour proposer. La Manette envoie un brouillon au fil de la saisie ; « Valider » fait juger aussitôt, et à l'échéance, comptée par le serveur, le dernier brouillon reçu est jugé. Le brouillon n'est montré à personne avant le verdict.
7. **Correction** : un champ est juste s'il égale le titre court Deezer (`title_short` : « Halo », pas « Halo (Remastered) ») ou le nom de l'artiste, après la normalisation des pseudos (casse, accents et espaces ignorés). La tolérance aux articles et aux fautes de frappe vient avec #18.
8. **Barème** : le titre rapporte 70 points, l'artiste 30, au premier Joueur qui trouve chaque partie ; les deux ensemble, 100. Aucun point négatif.
9. **Erreur** : un champ faux ou vide bloque le Joueur sur ce Morceau ; il garde les points d'une partie juste. L'extrait reprend et le Buzz se rouvre pour les Joueurs non bloqués.
10. **Partie trouvée** : elle est montrée tout de suite sur l'Écran de salle (« Artiste trouvé par Léa : Beyoncé ») et les Manettes suivantes ne demandent plus que la partie manquante. Le Morceau continue.
11. **Fin d'un Morceau** : révélation complète (titre, artiste, pochette) quand les deux parties sont trouvées, quand les 30 s d'extrait sont écoulées, ou quand plus aucun Joueur ne peut buzzer. En Pilotage Automatique, le Morceau suivant démarre 8 s après ; en Manuel, il attend l'Animateur. « Passer l'étape » révèle d'abord le Morceau en cours, puis passe au suivant, comme au Quiz.
12. **Classement général** : un Morceau compte une bonne réponse pour chaque Joueur qui en a trouvé au moins une partie ; sa rapidité est le temps entre le début de l'extrait et son Buzz gagnant, hors pauses. Le Blind test les transmet par `AttributionDePoints`, à étendre dans le socle (#24).
13. **Arrivée en cours de Manche** : un Joueur qui arrive pendant la Manche peut buzzer aussitôt, comme au Quiz et au Karaoké ; les Morceaux passés ne sont pas rejoués.
14. **Deezer indisponible** : à la création de la Manche, refus 503 avec un code dédié ; l'Animateur réessaie. Pendant la Manche, un Morceau dont le lien d'extrait ne peut être obtenu est sauté sans points, et le suivant démarre.

## Alternatives écartées

- **Relayer l'extrait par le serveur** : plus étanche, mais environ 500 Ko de son à relayer par Morceau et une route de plus à sécuriser, pour un lien signé qui ne révèle déjà ni l'identifiant ni le titre.
- **Donner l'identifiant Deezer à l'Écran de salle** : n'importe qui retrouve le titre en lisant la page (story 58).
- **L'Écran de salle signale la lecture**, comme le lecteur du Karaoké (ADR 0007) : nécessaire pour caler des paroles sur une Chanson de plusieurs minutes, superflu pour 30 s d'extrait ; des routes de plus et un lecteur absent à gérer.
- **Points dégressifs selon le temps** : la rapidité est déjà récompensée par la main ; elle départage les ex aequo au Classement général.
- **Main au deuxième Buzz reçu après une erreur** : celui qui a buzzé tard sans être sûr prendrait la main ; rouvrir le Buzz laisse leur chance aux autres.
- **Soumission par la Manette à la fin du délai** : un envoi arrivé après l'échéance serait refusé, et la proposition perdue sur un réseau lent.

## Conséquences

- Le contrat du Blind test (#17) décrit le bloc `blindtest` par destination, le brouillon et la proposition, l'état de lecture et la partie trouvée ; `envoyerBuzz` et `AttributionDePoints` évoluent avec l'accord de leur propriétaire (#8, #24).
- #18 ne garde que la correction tolérante ; le barème par partie arrive avec #17.
- La salle entend l'extrait à quelques dixièmes de seconde près de l'Horloge du serveur, sans effet sur le jeu : seul le serveur décide de l'ordre des Buzz et des délais.
- Si Deezer change son API ou ses extraits, seule l'implémentation de `CatalogueMusical` change.
