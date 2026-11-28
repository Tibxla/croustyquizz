package fr.croustyquizz.campagne;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record CampagneSms(UUID id, String texte, Instant demandeeLe, Instant envoiPrevuLe, Etat etat,
		long envoyes, long echoues) {
	enum Etat { PROGRAMMEE, EN_COURS, TERMINEE }

	record Envoi(UUID contactId, String statut, Instant envoyeLe, String motif, String identifiantPrestataire) { }

	record Detail(CampagneSms campagne, List<Envoi> envois) { }
}
