package fr.croustyquizz.campagne;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Refuse;

@Service
class ServiceCampagnesSms {
	record Apercu(String exemple, int destinataires, int segments, long coutEstimeCentimes, Instant envoiPrevuLe,
			boolean immediat) { }

	record Creation(CampagneSms campagne, boolean nouvelle) { }

	record Liste(List<CampagneSms> campagnes, int destinatairesEligibles) { }

	private final DepotCampagnesSms depot;
	private final ContactsDuLieu contacts;
	private final PlanificationCampagnes planification;
	private final Horloge horloge;
	private final TransactionTemplate transactions;
	private final int tarifParSegment;

	ServiceCampagnesSms(DepotCampagnesSms depot, ContactsDuLieu contacts, PlanificationCampagnes planification,
			Horloge horloge, PlatformTransactionManager gestionnaire,
			@Value("${croustyquizz.campagnes.tarif-par-segment:5}") int tarifParSegment) {
		if (tarifParSegment < 0) {
			throw new IllegalArgumentException("Le tarif par segment doit être positif ou nul.");
		}
		this.depot = depot;
		this.contacts = contacts;
		this.planification = planification;
		this.horloge = horloge;
		this.transactions = new TransactionTemplate(gestionnaire);
		this.tarifParSegment = tarifParSegment;
	}

	Apercu apercu(UUID lieuId, String saisie) {
		return composerApercu(nettoyer(saisie), contacts.eligibles(lieuId), maintenant());
	}

	Creation creer(UUID lieuId, UUID cle, String saisie) {
		String texte = nettoyer(saisie);
		Creation creation = transactions.execute(transaction -> enregistrer(lieuId, cle, texte));
		if (!creation.nouvelle()) {
			return creation;
		}
		planification.ajouter(creation.campagne());
		return new Creation(consulterCampagne(lieuId, creation.campagne().id()), true);
	}

	Liste lister(UUID lieuId) {
		return new Liste(depot.lister(lieuId), contacts.eligibles(lieuId).size());
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public CampagneSms.Detail consulter(UUID lieuId, UUID id) {
		return new CampagneSms.Detail(consulterCampagne(lieuId, id), depot.envois(id));
	}

	private Creation enregistrer(UUID lieuId, UUID cle, String texte) {
		depot.verrouillerLieu(lieuId);
		var existante = depot.parCle(lieuId, cle);
		if (existante.isPresent()) {
			if (!existante.get().texte().equals(texte)) {
				throw new Refuse(409, "CLE_IDEMPOTENCE_REUTILISEE", "Cette demande correspond à un autre texte.").exception();
			}
			return new Creation(existante.get(), false);
		}
		var destinataires = contacts.eligibles(lieuId);
		Instant demande = maintenant();
		var apercu = composerApercu(texte, destinataires, demande);
		if (destinataires.isEmpty()) {
			throw new Refuse(409, "AUCUN_CONTACT_ELIGIBLE", "Ton Lieu n’a encore aucun Contact éligible.").exception();
		}
		var campagne = new CampagneSms(UUID.randomUUID(), texte, demande, apercu.envoiPrevuLe(),
				CampagneSms.Etat.PROGRAMMEE, 0, 0);
		depot.creer(lieuId, cle, campagne, destinataires.stream().map(ContactsDuLieu.Contact::id).toList());
		return new Creation(campagne, true);
	}

	private CampagneSms consulterCampagne(UUID lieuId, UUID id) {
		return depot.consulter(lieuId, id).orElseThrow(() ->
				new Refuse(404, "RESSOURCE_INTROUVABLE", "Campagne SMS introuvable.").exception());
	}

	private Apercu composerApercu(String texte, List<ContactsDuLieu.Contact> destinataires, Instant demande) {
		try {
			String exemple = TexteSms.preparer(texte, TexteSms.LIEN_EXEMPLE);
			int segments = TexteSms.segments(exemple);
			for (var contact : destinataires) {
				String complet = TexteSms.preparer(texte, contact.lienDeDesinscription());
				segments = Math.max(segments, TexteSms.segments(complet));
			}
			Instant envoiPrevu = CreneauxSms.prochain(demande);
			return new Apercu(exemple, destinataires.size(), segments, (long) segments * destinataires.size() * tarifParSegment,
					envoiPrevu, envoiPrevu.equals(demande));
		} catch (IllegalArgumentException invalide) {
			throw texteInvalide(invalide);
		}
	}

	private static String nettoyer(String texte) {
		try {
			return TexteSms.nettoyer(texte);
		} catch (IllegalArgumentException invalide) {
			throw texteInvalide(invalide);
		}
	}

	private static RuntimeException texteInvalide(IllegalArgumentException invalide) {
		return new Refuse(422, "TEXTE_SMS_INVALIDE", invalide.getMessage()).exception();
	}

	private Instant maintenant() {
		return horloge.maintenant().truncatedTo(ChronoUnit.MICROS);
	}
}
