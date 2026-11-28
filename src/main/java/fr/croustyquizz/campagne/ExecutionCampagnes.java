package fr.croustyquizz.campagne;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import fr.croustyquizz.manche.Horloge;

@Service
class ExecutionCampagnes {
	private final DepotCampagnesSms depot;
	private final ContactsDuLieu contacts;
	private final EnvoiDeSms sms;
	private final Horloge horloge;
	private final TransactionTemplate transactions;

	ExecutionCampagnes(DepotCampagnesSms depot, ContactsDuLieu contacts, EnvoiDeSms sms, Horloge horloge,
			PlatformTransactionManager gestionnaire) {
		this.depot = depot;
		this.contacts = contacts;
		this.sms = sms;
		this.horloge = horloge;
		this.transactions = new TransactionTemplate(gestionnaire);
	}

	void executer(UUID id) {
		// Le verrou PostgreSQL couvre une tentative, et chaque résultat est validé avant le suivant.
		while (Boolean.TRUE.equals(transactions.execute(transaction -> traiterUnContact(id)))) {
			// Continuer tant que la Campagne avance dans le créneau.
		}
	}

	private boolean traiterUnContact(UUID id) {
		var plan = depot.verrouiller(id);
		Instant maintenant = horloge.maintenant();
		if (plan.etat() == CampagneSms.Etat.TERMINEE || plan.envoiPrevuLe().isAfter(maintenant)) {
			return false;
		}
		var contactId = depot.prochainContact(id);
		if (contactId.isEmpty()) {
			depot.changerEtat(id, CampagneSms.Etat.TERMINEE);
			return false;
		}
		var contact = contacts.eligible(plan.lieuId(), contactId.get());
		Instant tentative = horloge.maintenant();
		Instant prochainCreneau = CreneauxSms.prochain(tentative);
		if (prochainCreneau.isAfter(tentative)) {
			depot.reporter(id, prochainCreneau);
			return false;
		}
		if (plan.etat() == CampagneSms.Etat.PROGRAMMEE) {
			depot.changerEtat(id, CampagneSms.Etat.EN_COURS);
		}
		if (contact.isPresent()) {
			var resultat = envoyer(plan.texte(), contact.get());
			depot.enregistrer(id, contactId.get(), resultat, tentative);
		} else {
			depot.ecarter(id, contactId.get());
		}
		return true;
	}

	private EnvoiDeSms.Resultat envoyer(String texte, ContactsDuLieu.Contact contact) {
		String complet;
		try {
			complet = TexteSms.preparer(texte, contact.lienDeDesinscription());
		} catch (IllegalArgumentException invalide) {
			return new EnvoiDeSms.Echec(invalide.getMessage());
		}
		try {
			return sms.envoyer(complet, contact.numeroInternational());
		} catch (RuntimeException erreur) {
			return new EnvoiDeSms.Echec("L’envoi du SMS n’a pas pu être confirmé.");
		}
	}
}
