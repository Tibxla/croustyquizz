package fr.croustyquizz.contact;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.campagne.ContactsDuLieu;
import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Refuse;

@Service
class ServiceContacts implements ContactsDuLieu {
	record Information(String mention, String declaration) { }

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Recu(Instant consentiLe, String lienDeDesinscription) {
		@Override
		public String toString() {
			return "Recu[" + consentiLe + "]";
		}
	}

	record ContactActif(String prenom, String nom, String numero, Instant consentiLe, String mention) {
		@Override
		public String toString() {
			return "ContactActif[***]";
		}
	}

	private static final String DECLARATION = "J’accepte de recevoir les offres de ce Lieu par SMS.";
	private static final String PAGE = "/desinscription.html";
	private final DepotContacts depot;
	private final Horloge horloge;
	private final String cheminPublic;
	private final String lienAbsolu;

	ServiceContacts(DepotContacts depot, Horloge horloge,
			@Value("${croustyquizz.contacts.adresse-publique}") URI adressePublique,
			@Value("${server.servlet.context-path:}") String contexte) {
		if (!List.of("http", "https").contains(adressePublique.getScheme()) || adressePublique.getHost() == null
				|| adressePublique.getQuery() != null || adressePublique.getFragment() != null
				|| adressePublique.getUserInfo() != null) {
			throw new IllegalArgumentException("Une adresse publique HTTP(S) est requise pour les liens de désinscription.");
		}
		this.depot = depot;
		this.horloge = horloge;
		this.cheminPublic = contexte + PAGE;
		this.lienAbsolu = adressePublique.toASCIIString().replaceFirst("/$", "") + PAGE;
	}

	Information informer(UUID soiree) {
		return new Information(mention(lieu(soiree)), DECLARATION);
	}

	@Transactional
	public Recu inscrire(UUID soiree, CoordonneesContact coordonnees, String mentionAcceptee, String declaration) {
		var lieu = lieu(soiree);
		String mention = mention(lieu);
		if (!mention.equals(mentionAcceptee) || !DECLARATION.equals(declaration)) {
			throw new Refuse(409, "MENTION_MODIFIEE", "La mention a changé. Lis-la avant de donner ton Consentement.")
					.exception();
		}
		depot.verrouillerLieu(lieu.id());
		var secrets = depot.secrets();
		if (depot.opposition(lieu.id(), secrets.empreinte(lieu.id(), coordonnees.numero()))) {
			throw new Refuse(409, "CONTACT_DESINSCRIT", "Ce numéro a fait l’objet d’une désinscription auprès de ce Lieu.")
					.exception();
		}
		Instant consenti = horloge.maintenant().truncatedTo(ChronoUnit.MICROS);
		Instant expiration = consenti.atOffset(ZoneOffset.UTC).plusYears(3).toInstant();
		var fiche = new DepotContacts.Fiche(UUID.randomUUID(), coordonnees.prenom(), coordonnees.nom(),
				coordonnees.numero(), consenti, mention + "\n" + DECLARATION, expiration);
		UUID id = depot.enregistrer(lieu.id(), fiche);
		String lien = id.equals(fiche.id()) ? cheminPublic + secrets.fragment(id) : null;
		return new Recu(consenti, lien);
	}

	@Transactional
	public void desinscrire(String capacite) {
		var secrets = depot.secrets();
		UUID id = secrets.verifier(capacite).orElseThrow(ServiceContacts::lienInvalide);
		var lieu = depot.lieuDuContact(id);
		if (lieu.isPresent()) {
			UUID lieuId = lieu.get();
			depot.verrouillerLieu(lieuId);
			var numero = depot.retirer(id, horloge.maintenant());
			if (numero.isPresent()) {
				depot.enregistrerOpposition(id, lieuId, secrets.empreinte(lieuId, numero.get()));
				return;
			}
		}
		if (!depot.dejaRetire(id)) {
			throw lienInvalide();
		}
	}

	List<ContactActif> lister(UUID lieuId) {
		return depot.actifs(lieuId, horloge.maintenant()).stream()
				.map(fiche -> new ContactActif(fiche.prenom(), fiche.nom(), fiche.numero(), fiche.consentiLe(), fiche.mention()))
				.toList();
	}

	@Override
	public List<Contact> eligibles(UUID lieuId) {
		var fiches = depot.actifs(lieuId, horloge.maintenant());
		if (fiches.isEmpty()) {
			return List.of();
		}
		var secrets = depot.secrets();
		return fiches.stream().map(fiche -> pourCampagne(fiche, secrets)).toList();
	}

	@Override
	public Optional<Contact> eligible(UUID lieuId, UUID contactId) {
		return depot.actifPourEnvoi(lieuId, contactId, horloge.maintenant())
				.filter(fiche -> fiche.expireLe().isAfter(horloge.maintenant()))
				.map(fiche -> pourCampagne(fiche, depot.secrets()));
	}

	@Scheduled(fixedDelay = 1, timeUnit = java.util.concurrent.TimeUnit.HOURS)
	void purger() {
		depot.purger(horloge.maintenant());
	}

	private Contact pourCampagne(DepotContacts.Fiche fiche, SecretsDesContacts secrets) {
		return new Contact(fiche.id(), fiche.numero(), lienAbsolu + secrets.fragment(fiche.id()));
	}

	private DepotContacts.Lieu lieu(UUID soiree) {
		return depot.lieuDeLaSoiree(soiree).orElseThrow(() ->
				new Refuse(404, "RESSOURCE_INTROUVABLE", "Cette Soirée n’est rattachée à aucun Lieu.").exception());
	}

	private static String mention(DepotContacts.Lieu lieu) {
		return lieu.nom() + " utilisera ton prénom, ton nom et ton numéro pour t’envoyer ses offres par SMS. "
				+ "Tes coordonnées sont conservées pendant trois ans après ton dernier Consentement. "
				+ "Tu peux te désinscrire à tout moment grâce à ton lien personnel.";
	}

	private static RuntimeException lienInvalide() {
		return new Refuse(404, "LIEN_INVALIDE", "Ce lien de désinscription est invalide ou a expiré.").exception();
	}
}
