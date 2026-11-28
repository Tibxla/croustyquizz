/**
 * Campagnes SMS d’un Lieu. Le port {@link fr.croustyquizz.campagne.ContactsDuLieu}
 * attend de #32 les seuls Contacts de ce Lieu dont le Consentement est toujours valide.
 * L’adaptateur fournit un identifiant stable, un numéro international normalisé et un lien
 * personnel de désinscription. Il relit le Consentement pour chaque tentative d’envoi.
 * Les identifiants du public choisi sont conservés pour la reprise. Les numéros et les liens
 * restent dans le port et dans le texte remis à {@link fr.croustyquizz.campagne.EnvoiDeSms}.
 *
 * <p>Le port d’envoi est simulé. L’adaptateur réel devra gérer le cas où le prestataire accepte
 * un SMS alors que le résultat ne peut pas être enregistré dans PostgreSQL.</p>
 */
package fr.croustyquizz.campagne;
