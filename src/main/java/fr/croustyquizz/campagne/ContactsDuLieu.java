package fr.croustyquizz.campagne;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Accès aux Contacts du Lieu qui ont donné leur Consentement et sont toujours inscrits. */
@FunctionalInterface
public interface ContactsDuLieu {
	record Contact(UUID id, String numeroInternational, String lienDeDesinscription) {
		@Override
		public String toString() {
			return "Contact[" + id + "]";
		}
	}

	/** Chaque identifiant apparaît au plus une fois. Les liens sont absolus et propres à chaque Contact. */
	List<Contact> eligibles(UUID lieuId);

	/** Relit le Consentement au moment de l’envoi. Le raccordement #32 peut cibler ce Contact en base. */
	default Optional<Contact> eligible(UUID lieuId, UUID contactId) {
		return eligibles(lieuId).stream().filter(contact -> contact.id().equals(contactId)).findFirst();
	}
}
