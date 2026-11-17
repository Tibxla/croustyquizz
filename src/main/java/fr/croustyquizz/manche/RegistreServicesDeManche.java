package fr.croustyquizz.manche;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Route chaque Manche vers le service de son mode, sans dépendre d'un mode concret. */
@Component
public final class RegistreServicesDeManche {
	private final Map<ModeDeJeu, ServiceDeManche<?, ?>> services = new EnumMap<>(ModeDeJeu.class);
	private final DepotDeManches depot;
	public RegistreServicesDeManche(List<ServiceDeManche<?, ?>> services, DepotDeManches depot) {
		this.depot = depot;
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
	public ServiceDeManche<?, ?> pourManche(UUID soireeId, UUID mancheId) {
		var manche = depot.charger(mancheId);
		if (manche == null || !manche.getSoireeId().equals(soireeId)) {
			throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Manche introuvable.").exception();
		}
		return pourMode(manche.getMode());
	}
}
