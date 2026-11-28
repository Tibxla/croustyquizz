package fr.croustyquizz.campagne;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Journal des SMS simulés sur cette instance. */
final class EnvoiDeSmsFactice implements EnvoiDeSms {
	private static final Logger JOURNAL = LoggerFactory.getLogger(EnvoiDeSmsFactice.class);

	private final ConcurrentMap<String, SmsSimule> envois = new ConcurrentHashMap<>();

	@Override
	public Envoye envoyer(String texte, String numeroInternational) {
		String id = "simulation-" + UUID.randomUUID();
		String numeroMasque = "••" + numeroInternational.substring(numeroInternational.length() - 2);
		envois.put(id, new SmsSimule(texte, numeroMasque));
		JOURNAL.info("SMS simulé ({})", id);
		return new Envoye(id);
	}

	private record SmsSimule(String texte, String numeroMasque) { }
}
