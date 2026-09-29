/**
 * Temps réel : canaux WebSocket entre le serveur, les Manettes, l'Écran de salle
 * et la Console d'animation.
 *
 * <p>Les pages se connectent en STOMP sur {@code /ws} et s'abonnent à :
 * <ul>
 * <li>{@code /topic/soirees/{soireeId}/salle} : toute la salle d'une Soirée ;</li>
 * <li>{@code /topic/soirees/{soireeId}/console} : la Console d'animation ;</li>
 * <li>{@code /topic/joueurs/{joueurId}} : la Manette d'un Joueur.</li>
 * </ul>
 * Chaque message est une {@link fr.croustyquizz.tempsreel.Enveloppe} JSON
 * {@code {type, donnees}}. Côté page, {@code /js/tempsreel.js} gère la connexion.
 *
 * <p>Limite connue : n'importe quel client peut encore s'abonner à n'importe quel
 * canal. Le contrôle des abonnements arrive avec le jeton du Joueur (#10) et la
 * connexion de l'Animateur (#5).
 *
 * <p>Propriétaire : Tibxla.
 */
package fr.croustyquizz.tempsreel;
