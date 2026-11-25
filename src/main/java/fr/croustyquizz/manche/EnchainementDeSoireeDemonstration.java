package fr.croustyquizz.manche;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Enchaînement d'une Soirée de démonstration, absente de la base : elle n'a pas de commande de Soirée, et ne se
 * termine jamais. À l'échéance, la première Manche prête démarre ; sans Manche prête, rien ne se passe.
 */
@Component
@Profile({"dev", "recette"})
@Fallback
final class EnchainementDeSoireeDemonstration implements EnchainementDeSoiree {
	private final RegistreServicesDeManche modes;
	private final DepotDeManches depot;
	private final TransactionTemplate lecture;

	EnchainementDeSoireeDemonstration(RegistreServicesDeManche modes, DepotDeManches depot,
			PlatformTransactionManager transactions) {
		this.modes = modes;
		this.depot = depot;
		lecture = new TransactionTemplate(transactions);
		lecture.setReadOnly(true);
	}

	@Override
	public void mancheSuivante(UUID soireeId, UUID mancheTermineeId) {
		ManchePersistante suivante = lecture.execute(statut -> depot.deLaSoiree(soireeId).stream()
				.filter(manche -> manche.getEtat() == EtatManche.PRETE).findFirst().orElse(null));
		if (suivante == null) {
			return;
		}
		UUID envoiId = UUID.nameUUIDFromBytes(("manche-suivante:" + mancheTermineeId).getBytes(StandardCharsets.UTF_8));
		var envoi = new EnvoiDeCommande(EnchainementDeSoiree.acteur(soireeId),
				"/interne/soirees/" + soireeId + "/manche-suivante", envoiId, Map.of("action", "MANCHE_SUIVANTE"));
		if (modes.pourMode(suivante.getMode()).demarrer(suivante.getMancheId(), envoi) instanceof Refuse refus) {
			throw refus.exception();
		}
	}
}
