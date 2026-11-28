package fr.croustyquizz.campagne;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simulation enregistrée par la Campagne, sans conservation du texte ni du numéro. */
final class EnvoiDeSmsFactice implements EnvoiDeSms {
	private static final Logger JOURNAL = LoggerFactory.getLogger(EnvoiDeSmsFactice.class);

	@Override
	public Envoye envoyer(String texte, String numeroInternational) {
		String id = "simulation-" + UUID.randomUUID();
		JOURNAL.info("SMS simulé ({})", id);
		return new Envoye(id);
	}
}
