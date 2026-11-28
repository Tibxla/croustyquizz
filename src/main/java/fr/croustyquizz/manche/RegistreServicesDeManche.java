package fr.croustyquizz.manche;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Route chaque Manche vers le service de son mode, sans dépendre d'un mode concret. */
@Component
public final class RegistreServicesDeManche {
	private final Map<ModeDeJeu, ServiceDeManche<?, ?>> services = new EnumMap<>(ModeDeJeu.class);
	private final DepotDeManches depot;
	private final TransactionTemplate lecture;
	public RegistreServicesDeManche(List<ServiceDeManche<?, ?>> services, DepotDeManches depot,
			PlatformTransactionManager transactions) {
		this.depot = depot;
		lecture = new TransactionTemplate(transactions);
		lecture.setReadOnly(true);
		for (var service : services) {
			if (this.services.put(service.mode(), service) != null) {
				throw new IllegalArgumentException("Deux services pour le même Mode de jeu.");
			}
		}
	}
	public ServiceDeManche<?, ?> pourMode(ModeDeJeu mode) {
		var service = services.get(mode);
		if (service == null) { throw new Refuse(409, "ETAT_INCOMPATIBLE", "Ce Mode de jeu n'est pas encore disponible.").exception(); }
		return service;
	}
	/**
	 * Lit le mode de la Manche dans sa propre transaction en lecture seule (#240), refermée avant le refus et avant le
	 * service, qui ouvre la sienne et recharge la Manche.
	 */
	public ServiceDeManche<?, ?> pourManche(UUID soireeId, UUID mancheId) {
		ModeDeJeu mode = lecture.execute(statut -> {
			var manche = depot.charger(mancheId);
			return manche == null || !manche.getSoireeId().equals(soireeId) ? null : manche.getMode();
		});
		if (mode == null) {
			throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Manche introuvable.").exception();
		}
		return pourMode(mode);
	}
}
