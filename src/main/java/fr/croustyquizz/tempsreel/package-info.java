/**
 * Assemble les vues complètes et porte leur Révision, leur capture et leur diffusion STOMP.
 *
 * <p>Le ServiceDeManche prend VerrousSoiree avant la transaction et le garde jusqu'à
 * la publication après commit. AssembleurDeVues combine FournisseurPartieCommune
 * et le FournisseurDeBloc du mode pour salle, Console et Manette. CaptureVueRepli
 * suit ce même chemin avant le premier événement et après redémarrage.
 *
 * <p>Les destinations restent /topic/soirees/{id}/salle, /console et /topic/joueurs/{id}.
 * Les commandes passent par HTTP ; chaque événement remplace la vue complète et
 * porte type, soireeId, revision, emisLe et donnees. Le module /js/tempsreel.js
 * assure la reprise après la barrière d'abonnement du même Acteur.
 *
 * <p>Le package soiree fournit la partie commune réelle (FournisseurCommunSoiree) et
 * reconnaît Joueurs et Écrans de salle (ResolveurDActeurSoiree) ; les autres identités
 * vont au ResolveurDActeur du profil, qui refuse en production avant #5. En dev/recette,
 * ses jetons de démonstration sont décrits dans docs/api/README.md. Le Buzz anonyme /api/test
 * conserve ses vues et son cache distincts du métier sur les mêmes canaux.
 *
 * <p>LimiteDeRequetes protège le Buzz, les Réponses, l'accès à une Soirée (#4), la connexion
 * et l'inscription de l'Animateur (#238) : 429 TROP_DE_REQUETES avec Retry-After, seuils dans
 * ReglagesDesLimites ({@code croustyquizz.limites}).
 *
 * <p>Propriétaire : Tibxla.
 */
package fr.croustyquizz.tempsreel;
