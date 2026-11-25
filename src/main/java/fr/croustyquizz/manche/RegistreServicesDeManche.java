package fr.croustyquizz.manche;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import fr.croustyquizz.tempsreel.VerrousSoiree;

/** Route chaque Manche vers le service de son mode, sans dépendre d'un mode concret. */
@Component
public final class RegistreServicesDeManche {
	private final Map<ModeDeJeu, ServiceDeManche<?, ?>> services = new EnumMap<>(ModeDeJeu.class);
	private final DepotDeManches depot;
	private final VerrousSoiree verrous;
	public RegistreServicesDeManche(List<ServiceDeManche<?, ?>> services, DepotDeManches depot, VerrousSoiree verrous) {
		this.depot = depot;
		this.verrous = verrous;
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

	/**
	 * Départ d'un Joueur (#205) : la Manche en cours ou en pause de la Soirée le retire de son état, puis republie
	 * les vues après son commit. Sans Manche active, rien ne change. Prêt, mais encore appelé par aucune commande :
	 * l'Exclusion (#11) l'appellera après son propre commit, pour que les vues publiées ne comptent plus le Joueur parmi
	 * les présents, comme la clôture de la Soirée enchaîne ses transactions. Une déconnexion de Manette ne l'appelle pas.
	 */
	public void retirerJoueurParti(UUID soireeId, UUID joueurId) {
		verrous.executer(soireeId, () -> {
			depot.deLaSoiree(soireeId).stream()
					.filter(manche -> manche.getEtat() == EtatManche.EN_COURS || manche.getEtat() == EtatManche.EN_PAUSE)
					.filter(manche -> services.containsKey(manche.getMode()))
					.forEach(manche -> services.get(manche.getMode()).retirerJoueurParti(soireeId, manche.getMancheId(), joueurId));
			return null;
		});
	}
}
