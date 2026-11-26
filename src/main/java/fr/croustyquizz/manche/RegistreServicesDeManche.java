package fr.croustyquizz.manche;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import fr.croustyquizz.tempsreel.VerrousSoiree;

/** Route chaque Manche vers le service de son mode, sans dépendre d'un mode concret. */
@Component
public final class RegistreServicesDeManche {
	private static final Logger JOURNAL = LoggerFactory.getLogger(RegistreServicesDeManche.class);
	private final Map<ModeDeJeu, ServiceDeManche<?, ?>> services = new EnumMap<>(ModeDeJeu.class);
	private final DepotDeManches depot;
	private final VerrousSoiree verrous;
	private final TransactionTemplate lecture;
	public RegistreServicesDeManche(List<ServiceDeManche<?, ?>> services, DepotDeManches depot, VerrousSoiree verrous,
			PlatformTransactionManager transactions) {
		this.depot = depot;
		this.verrous = verrous;
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
			for (MancheActive manche : manchesActives(soireeId)) {
				UUID mancheId = manche.mancheId();
				// L'Exclusion est déjà validée : l'échec d'une Manche, que son service a déjà évincée, ne doit ni lui
				// faire répondre une erreur ni priver les autres Manches du retrait.
				try { services.get(manche.mode()).retirerJoueurParti(soireeId, mancheId, joueurId); }
				catch (RuntimeException erreur) {
					JOURNAL.error("Départ du Joueur {} non appliqué à la Manche {}", joueurId, mancheId, erreur);
				}
			}
			return null;
		});
	}

	/**
	 * Lit les Manches actives dans sa propre transaction, refermée avant les services : chacun ouvre la sienne et
	 * recharge sa Manche.
	 */
	private List<MancheActive> manchesActives(UUID soireeId) {
		return lecture.execute(statut -> depot.deLaSoiree(soireeId).stream()
				.filter(manche -> manche.getEtat() == EtatManche.EN_COURS || manche.getEtat() == EtatManche.EN_PAUSE)
				.filter(manche -> services.containsKey(manche.getMode()))
				.map(manche -> new MancheActive(manche.getMancheId(), manche.getMode()))
				.toList());
	}

	private record MancheActive(UUID mancheId, ModeDeJeu mode) { }
}
