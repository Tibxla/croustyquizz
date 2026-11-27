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
Personne du Lieu qui crée et pilote les Soirées. C'est le seul rôle qui a un compte. Pendant une Soirée, il peut confier une partie de ses pouvoirs par Délégation.
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

**Signalement** :
Alerte envoyée par un Joueur à l'Animateur à propos du pseudo ou du comportement d'un autre Joueur.
_Éviter_ : report, plainte, dénonciation

**Avertissement** :
Demande de l'Animateur à un Joueur de changer de pseudo. Le Joueur ne peut plus jouer tant qu'il ne l'a pas fait.
_Éviter_ : warning, sanction

**Exclusion** :
Retrait définitif d'un Joueur d'une Soirée, décidé par l'Animateur. Elle vaut pour la Soirée et pour la Manette du Joueur.
_Éviter_ : ban, kick, bannissement

**Classement général** :
Cumul des points des Joueurs, ou des Équipes en Format Équipe, sur les Manches de Quiz et de Blind test d'une Soirée. Le Karaoké et le Freestyle n'y comptent pas.
_Éviter_ : score, leaderboard

**Palmarès du Karaoké** :
Classement des Passages de Karaoké d'une Soirée selon le Vote du public, séparé du Classement général.
_Éviter_ : classement karaoké, score de chant

**Palmarès du Freestyle** :
Classement du Freestyle d'une Soirée selon le Vote du public, séparé du Classement général : les Interprètes selon leurs victoires dans les Passages à plusieurs, et les Passages à un seul Interprète selon leur note.
_Éviter_ : classement freestyle, score de rap

**Délégation** :
Pouvoirs limités que l'Animateur confie, le temps d'une Soirée, à la Manette d'un Joueur, par exemple gérer la File d'attente, et qu'il retire quand il veut. Elle ne crée pas de compte et ne permet ni l'Exclusion ni la fin de la Soirée.
_Éviter_ : co-animateur, modérateur, admin

## Modes de jeu

**Mode de jeu** :
Type d'animation jouable pendant une Soirée. Il en existe quatre : Quiz, Blind test, Karaoké, Freestyle.
_Éviter_ : jeu, module, activité

**Quiz** :
Mode de jeu où les joueurs répondent à des questions de culture générale.
_Éviter_ : questionnaire, trivia

**Blind test** :
Mode de jeu où les joueurs reconnaissent un morceau à partir d'un extrait audio.
_Éviter_ : quiz musical, devinette musicale

**Karaoké** :
Mode de jeu où des volontaires chantent à tour de rôle, seuls ou à plusieurs, sur des paroles synchronisées, puis le public vote pour eux. Le chant n'est pas analysé. Même en Format Équipe, il ne fait marquer de points à personne.
_Éviter_ : chant, scène ouverte

**Freestyle** :
Mode de jeu où des volontaires improvisent des rimes en rythme sur une Instrumentale, seuls ou à plusieurs, guidés par une Grille de rimes affichée sur l'Écran de salle, puis le public vote pour eux. L'improvisation n'est pas analysée. Même en Format Équipe, il ne fait marquer de points à personne.
_Éviter_ : rap, battle, impro

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
Signal envoyé depuis une Manette pour réclamer la parole pendant un Blind test. Le premier Buzz donne la main : le droit de faire une Proposition de titre, l'extrait en pause.
_Éviter_ : clic, main levée

**Proposition de titre** :
Titre et artiste d'un Morceau tapés par le Joueur qui a la main, dans deux champs, et jugés par le serveur. Chaque partie juste rapporte ses points ; un champ faux ou vide bloque le Joueur jusqu'au Morceau suivant (ADR 0009). À ne pas confondre avec les propositions d'une Question de Quiz.
_Éviter_ : réponse (réservé au Quiz), devinette

**Révélation** :
Moment où le titre, l'artiste et la pochette d'un Morceau apparaissent sur l'Écran de salle, à la fin du Morceau. Une partie déjà trouvée est montrée dès qu'elle l'est ; rien d'autre ne quitte le serveur avant.
_Éviter_ : solution, correction (réservé au Quiz)

**Chanson** :
Titre musical chanté pendant un Passage de Karaoké, avec ses paroles synchronisées.
_Éviter_ : morceau, piste

**Instrumentale** :
Morceau sans voix, au tempo connu, sur lequel les Interprètes improvisent pendant un Passage de Freestyle.
_Éviter_ : beat, prod, piste

**Grille de rimes** :
Suite de mesures affichée pendant un Passage de Freestyle, qui place sur les temps des mots qui riment entre eux, montrés ou à trouver selon la difficulté choisie.
_Éviter_ : pattern, partition

**Couplet** :
Suite de mesures qu'un seul Interprète rappe avant de laisser la place au suivant, dans un Passage de Freestyle à plusieurs.
_Éviter_ : tour, round, relais

**Passage** :
Moment où un à quatre Joueurs passent au micro, au Karaoké sur une Chanson ou au Freestyle sur une Instrumentale, noté ensuite par le Vote du public.
_Éviter_ : prestation, performance, tour de chant

**Interprète** :
Joueur qui fait partie d'un Passage, qu'il l'ait inscrit ou qu'il y ait été invité et ait accepté.
_Éviter_ : chanteur, rappeur, artiste

**Invitation** :
Proposition qu'un Joueur fait à un autre, en inscrivant un Passage, de devenir Interprète de ce Passage. L'invité l'accepte ou la refuse ; sans réponse, elle expire à l'appel du Passage, à son retrait ou à la fin de la Manche.
_Éviter_ : demande d'ami, ajout

**File d'attente** :
Liste ordonnée des Passages à venir d'une Manche de Karaoké ou de Freestyle. Elle avance par Tours : le deuxième Passage d'un Interprète vient après le premier de chacun des autres.
_Éviter_ : playlist, queue

**Tour** :
Cycle de la File d'attente au cours duquel chaque Interprète a au plus un Passage. Un Passage à plusieurs compte pour chacun de ses Interprètes, et un Passage placé en priorité compte pour le Tour de ses Interprètes.
_Éviter_ : round, rotation (pour le cycle), tour de chant

**Vote du public** :
Avis donné par un Joueur sur un Passage dont il n'est pas Interprète : une note, ou, dans un Passage de Freestyle à plusieurs, le choix du meilleur Interprète.
_Éviter_ : score de chant, évaluation

**Enregistrement** :
Captation sonore d'un Passage par la Manette d'un Interprète qui l'a demandée. Elle reste sur son téléphone.
_Éviter_ : record, captation, replay

## Interfaces

**Manette** :
Page web ouverte sur le téléphone d'un joueur en scannant un QR code, sans installation. Elle sert à buzzer, répondre, s'inscrire à un Passage et voter.
_Éviter_ : extension, appli mobile, client mobile

**Écran de salle** :
Page affichée sur la télévision ou le projecteur du lieu, visible par toute la salle : Questions, paroles, Grilles de rimes, scores.
_Éviter_ : écran principal, TV, affichage

**Musique d'attente** :
Musique que l'Écran de salle joue entre les Manches et entre les Passages, prise dans les fichiers de l'ordinateur qui l'affiche. Elle se tait pendant tout son de Manche, et l'Animateur peut la couper.
_Éviter_ : playlist, fond sonore, ambiance

**Console d'animation** :
Page web ouverte par l'Animateur sur un ordinateur ou une tablette, depuis laquelle il crée une Soirée, lance les Manches et intervient en cours de jeu.
_Éviter_ : back-office, dashboard, admin

**Assistant** :
IA conversationnelle de l'Animateur (Claude, ChatGPT ou autre) connectée à CroustyQuizz, qui prépare des Soirées et rédige des Questions à sa demande. Elle agit de l'extérieur : CroustyQuizz n'embarque aucune IA.
_Éviter_ : bot, agent, chatbot, IA

**Clé d'Assistant** :
Secret propre à un Lieu, généré par un Animateur, qui autorise un Assistant à agir pour ce Lieu.
_Éviter_ : token, clé API, jeton

**Vue synchronisée** :
Ce qu'une Console d'animation, une Manette ou l'Écran de salle affiche à un instant donné, tenu à jour par le serveur : une capture complète, puis chaque événement qui la remplace dans l'ordre des Révisions.
_Éviter_ : état, snapshot, cache

**Révision** :
Numéro d'ordre d'une Vue synchronisée sur une destination (la salle, la Console ou une Manette), contigu et croissant. Deux destinations ont des Révisions indépendantes.
_Éviter_ : version, séquence

## Relation avec la clientèle

**Contact du Lieu** :
Personne qui a donné son nom, son prénom et son numéro de téléphone à un Lieu, et accepté de recevoir ses offres par SMS. Devenir Contact n'est jamais obligatoire pour jouer.
_Éviter_ : client, prospect, abonné, lead

**Consentement** :
Accord explicite, horodaté et révocable d'un Contact du Lieu pour recevoir des Campagnes SMS. Le texte exact accepté est conservé.
_Éviter_ : opt-in, acceptation

**Campagne SMS** :
Message promotionnel envoyé par un Lieu à ses Contacts qui ont donné leur Consentement.
_Éviter_ : newsletter, pub, relance, notification
