---
status: accepted
---

# L'Enregistrement d'un Passage se fait sur la Manette et ne passe jamais par le serveur

Un Interprète peut vouloir garder le son de son Passage de Karaoké ou de Freestyle. Nous faisons capter ce son par sa propre Manette, avec le micro de son téléphone, et le fichier reste sur ce téléphone : CroustyQuizz ne le reçoit, ne le stocke et ne le diffuse jamais. L'Enregistrement est proposé avant le Passage, désactivé par défaut, et chaque Interprète qui le veut l'accepte sur sa Manette. L'Animateur peut l'interdire pour tout son Lieu.

Le serveur ne traite ainsi aucune voix : pas de durée de conservation à fixer, pas de demande d'effacement à honorer, pas de stockage de fichiers à payer ni à sauvegarder. L'hébergement de recette et de démo (Render gratuit, dont le disque est effacé à chaque redémarrage, et une base Neon faite pour des données relationnelles) n'aurait d'ailleurs pas permis de conserver ces fichiers sans ajouter un service de stockage.

## Alternative écartée

- **Capter le son depuis l'Écran de salle**, avec le micro de l'ordinateur du Lieu, envoyer le fichier au serveur et le rendre à l'Interprète par un QR code. Le son aurait été plus proche de celui de la salle et un seul appareil aurait enregistré, mais le serveur aurait conservé des voix de clients du Lieu, avec les obligations qui vont avec, et il aurait fallu un stockage de fichiers dédié.

## Conséquences

- La qualité dépend du téléphone de l'Interprète et de sa place dans la salle. Dans un Passage à plusieurs, chaque Interprète qui l'a accepté obtient son propre fichier.
- Aucune route HTTP ni aucun événement STOMP ne transporte de son : le contrat de `docs/api/` ne porte que l'autorisation du Lieu et l'accord de l'Interprète.
- Revenir à un enregistrement côté serveur demanderait un stockage de fichiers, une politique de conservation et une nouvelle information des Joueurs.
