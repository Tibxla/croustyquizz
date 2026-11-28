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
 * <p>Le package soiree fournit la partie commune réelle (FournisseurCommunSoiree) et le
 * ResolveurDActeur principal (ResolveurDActeurSoiree). Sans Bearer, une requête HTTP est celle
 * de l'Animateur de la session, pour une Soirée de son Lieu (401 sans session, 404 hors du Lieu).
 * Un Bearer est un jeton de reconnexion de Joueur ou une clé d'Écran ; s'il n'en est pas un, il va
 * au repli {@code @Fallback} ResolveurDActeurDuProfil : ResolveurDActeurDemonstration en dev et
 * recette, dont les jetons décrits dans docs/api/README.md ne visent que des Soirées fictives, et
 * ResolveurDActeurProduction ailleurs, qui refuse en 401.
 *
 * <p>En STOMP, PoigneeDeMainAnimateur garde la session HTTP du handshake. ControleAccesStomp n'en
 * fait l'Animateur que si le CONNECT porte son jeton CSRF, puis vérifie le Lieu à chaque SUBSCRIBE ;
 * sans Bearer ni jeton CSRF, la connexion reste anonyme en dev et recette (canal de salle d'une
 * Soirée fictive) et passe ailleurs par le repli, qui la refuse. La fin de la session HTTP ferme
 * les connexions de la Console. Le Buzz anonyme /api/test conserve ses vues et son cache
 * distincts du métier sur les mêmes canaux.
 *
 * <p>PresenceDesManettes suit les connexions STOMP des Joueurs dans ConnexionsDesManettes, que lit
 * FournisseurCommunSoiree pour le champ connecte : la fermeture de la dernière connexion d'une
 * Manette, puis son retour, publient joueurs-modifies sous le verrou de la Soirée (#8).
 *
 * <p>LimiteDeRequetes, filtre placé devant les contrôleurs, limite le Buzz, les Réponses et
 * l'accès à une Soirée (#4) : 429 TROP_DE_REQUETES avec Retry-After, seuils dans
 * ReglagesDesLimites ({@code croustyquizz.limites}).
 *
 * <p>Propriétaire : Tibxla.
 */
package fr.croustyquizz.tempsreel;
