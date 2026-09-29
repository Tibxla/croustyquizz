# CroustyQuizz

Plateforme d'animation de soirées pour les lieux qui accueillent du public : le lieu lance un jeu, les clients jouent depuis leur téléphone.

## Soirée

**Lieu** :
Établissement qui organise des Soirées : bar, salle, association.
_Éviter_ : bar, client, établissement

**Soirée** :
Événement organisé dans un Lieu, que les Joueurs rejoignent une seule fois grâce à un code d'accès. Une Soirée enchaîne plusieurs Manches.
_Éviter_ : session, partie, salle

**Manche** :
Séquence d'un seul Mode de jeu à l'intérieur d'une Soirée.
_Éviter_ : partie, round, jeu

**Animateur** :
Personne du Lieu qui crée et pilote les Soirées. C'est le seul rôle qui a un compte.
_Éviter_ : admin, gérant, organisateur, MC

**Joueur** :
Personne connectée à une Soirée depuis sa Manette, identifiée par un pseudo, sans compte. En Format Équipe, il appartient à une Équipe.
_Éviter_ : utilisateur, participant, client

**Format** :
Règle choisie pour toute une Soirée qui fixe qui marque les points : Solo ou Équipe.
_Éviter_ : mode, type de partie

**Solo** :
Format où chaque Joueur marque ses propres points.
_Éviter_ : individuel, mode solo

**Équipe** :
Groupe de Joueurs qui marquent des points ensemble en Format Équipe. La première réponse envoyée par un membre engage toute l'Équipe, il n'y a pas de capitaine.
_Éviter_ : table, groupe, mode équipe

**Pilotage** :
Manière dont une Soirée avance, choisie pour toute la Soirée : Manuel, où l'Animateur fait avancer, ou Automatique, où la Soirée s'enchaîne seule. Dans les deux cas, l'Animateur peut intervenir.
_Éviter_ : mode auto, mode animé

**Concurrent** :
Ce qui marque des points dans une Soirée : le Joueur en Format Solo, l'Équipe en Format Équipe.
_Éviter_ : participant, compétiteur

**Classement général** :
Cumul des points des Joueurs, ou des Équipes en Format Équipe, sur les Manches de Quiz et de Blind test d'une Soirée. Le Karaoké n'y compte pas.
_Éviter_ : score, leaderboard

**Palmarès du Karaoké** :
Classement des passages de Karaoké d'une Soirée selon le Vote du public, séparé du Classement général.
_Éviter_ : classement karaoké, score de chant

## Modes de jeu

**Mode de jeu** :
Type d'animation jouable pendant une soirée. Il en existe trois : Quiz, Blind test, Karaoké.
_Éviter_ : jeu, module, activité

**Quiz** :
Mode de jeu où les joueurs répondent à des questions de culture générale.
_Éviter_ : questionnaire, trivia

**Blind test** :
Mode de jeu où les joueurs reconnaissent un morceau à partir d'un extrait audio.
_Éviter_ : quiz musical, devinette musicale

**Karaoké** :
Mode de jeu où des volontaires chantent seuls à tour de rôle sur des paroles synchronisées, puis le public vote pour eux. Le chant n'est pas analysé. Il reste individuel même en Format Équipe.
_Éviter_ : chant, scène ouverte

**Question** :
Énoncé de Quiz accompagné de quatre propositions, dont une seule est juste.
_Éviter_ : item, carte

**Réponse** :
Proposition choisie par un Joueur sur sa Manette pour une Question, horodatée pour calculer les points selon la vitesse.
_Éviter_ : choix, vote

**Morceau** :
Titre musical dont un extrait est diffusé pendant un Blind test, à reconnaître.
_Éviter_ : chanson, piste, track

**Buzz** :
Signal envoyé depuis une Manette pour réclamer la parole pendant un Blind test. Le premier Buzz donne le droit de proposer un titre.
_Éviter_ : clic, main levée

**Chanson** :
Titre musical choisi par un Joueur pour le chanter au Karaoké, avec ses paroles synchronisées.
_Éviter_ : morceau, piste

**Passage** :
Tour d'un Joueur au Karaoké : une Chanson chantée, puis notée par le Vote du public.
_Éviter_ : prestation, performance, tour de chant

**File d'attente** :
Liste ordonnée des Passages à venir d'une Manche de Karaoké.
_Éviter_ : playlist, queue

**Vote du public** :
Note donnée par un Joueur à un Passage qui n'est pas le sien.
_Éviter_ : score de chant, évaluation

## Interfaces

**Manette** :
Page web ouverte sur le téléphone d'un joueur en scannant un QR code, sans installation. Elle sert à buzzer, répondre, s'inscrire au Karaoké et voter.
_Éviter_ : extension, appli mobile, client mobile

**Écran de salle** :
Page affichée sur la télévision ou le projecteur du lieu, visible par toute la salle : questions, paroles, scores.
_Éviter_ : écran principal, TV, affichage

**Console d'animation** :
Page web ouverte par l'Animateur sur un ordinateur ou une tablette, depuis laquelle il crée une Soirée, lance les Manches et intervient en cours de jeu.
_Éviter_ : back-office, dashboard, admin

**Assistant** :
IA conversationnelle de l'Animateur (Claude, ChatGPT ou autre) connectée à CroustyQuizz, qui prépare des Soirées et rédige des Questions à sa demande. Elle agit de l'extérieur : CroustyQuizz n'embarque aucune IA.
_Éviter_ : bot, agent, chatbot, IA

**Clé d'Assistant** :
Secret propre à un Lieu, généré par un Animateur, qui autorise un Assistant à agir pour ce Lieu.
_Éviter_ : token, clé API, jeton
