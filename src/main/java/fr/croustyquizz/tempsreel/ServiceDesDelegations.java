package fr.croustyquizz.tempsreel;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatDeSoiree;
import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Pouvoir;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.passage.ParticipantsDePassage;

/**
 * Délégation (#67) : l'Animateur confie à un Joueur présent un ensemble de pouvoirs, qu'il modifie ou retire
 * à tout moment. Attribution et retrait passent par le verrou de la Soirée, comme le contrôle de
 * {@link ControleDesDelegations} : une commande déléguée qui tient ce verrou ne peut pas croiser un retrait.
 */
@Service
public class ServiceDesDelegations {
	static final String EVENEMENT = "delegation-modifiee";
	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceDesDelegations.class);
	private final DepotDeDelegations depot;
	private final EtatDeSoiree soirees;
	private final ParticipantsDePassage participants;
	private final PublicationDeVues publication;
	private final VerrousSoiree verrous;
	private final Horloge horloge;
	private final TransactionTemplate transaction;
	private final TransactionTemplate nouvelleTransaction;

	ServiceDesDelegations(DepotDeDelegations depot, EtatDeSoiree soirees, ParticipantsDePassage participants,
			PublicationDeVues publication, VerrousSoiree verrous, Horloge horloge, PlatformTransactionManager transactions) {
		this.depot = depot;
		this.soirees = soirees;
		this.participants = participants;
		this.publication = publication;
		this.verrous = verrous;
		this.horloge = horloge;
		transaction = new TransactionTemplate(transactions);
		nouvelleTransaction = new TransactionTemplate(transactions);
		nouvelleTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** Remplace l'ensemble des pouvoirs du Joueur ; le même ensemble ne publie rien. */
	Delegation attribuer(Acteur acteur, UUID soireeId, UUID joueurId, Set<Pouvoir> pouvoirs) {
		verifierAnimateur(acteur, soireeId);
		return verrous.executer(soireeId, () -> transaction.execute(statut -> {
			verifierSoiree(soireeId);
			if (soirees.estTerminee(soireeId)) {
				throw new Refuse(409, "ETAT_INCOMPATIBLE", "La Soirée est terminée : plus aucune Délégation.").exception();
			}
			String pseudo = participants.pseudos(soireeId).get(joueurId);
			if (pseudo == null) {
				throw joueurIntrouvable();
			}
			if (!depot.pouvoirs(soireeId, joueurId).equals(pouvoirs)) {
				depot.enregistrer(soireeId, joueurId, pouvoirs, horloge.maintenant());
				publier(soireeId, joueurId);
			}
			return new Delegation(joueurId, pseudo, List.copyOf(EnumSet.copyOf(pouvoirs)));
		}));
	}

	/** Retire la Délégation ; un Joueur sans Délégation n'entraîne aucune publication. */
	void retirer(Acteur acteur, UUID soireeId, UUID joueurId) {
		verifierAnimateur(acteur, soireeId);
		verrous.executer(soireeId, () -> {
			transaction.executeWithoutResult(statut -> {
				verifierSoiree(soireeId);
				if (depot.retirer(soireeId, joueurId)) {
					publier(soireeId, joueurId);
				} else if (!participants.estPresent(soireeId, joueurId)) {
					throw joueurIntrouvable();
				}
			});
			return null;
		});
	}

	/**
	 * La Délégation vit avec la Soirée : la fin de Soirée l'efface. Ce cycle arrive après le commit de la vue
	 * finale, sous le verrou ; aucune erreur ne doit en ressortir, les lectures ignorant déjà une Soirée terminée.
	 */
	@EventListener
	public void effacerALaFin(CycleDeScene cycle) {
		if (!cycle.soireeTerminee()) {
			return;
		}
		try {
			nouvelleTransaction.executeWithoutResult(statut -> depot.effacer(cycle.soireeId()));
		} catch (RuntimeException erreur) {
			if (JOURNAL.isErrorEnabled()) {
				JOURNAL.error("Effacement des Délégations impossible pour la Soirée {}", cycle.soireeId(), erreur);
			}
		}
	}

	private void publier(UUID soireeId, UUID joueurId) {
		publication.publier(soireeId, Set.of(Destination.CONSOLE, Destination.joueur(joueurId)), EVENEMENT);
	}

	private void verifierAnimateur(Acteur acteur, UUID soireeId) {
		AccesAuxVues.verifier(acteur, soireeId, null);
		if (acteur.type() != Acteur.Type.ANIMATEUR) {
			throw new Refuse(403, "ACCES_REFUSE", "Seul l’Animateur attribue ou retire une Délégation.").exception();
		}
	}

	/** Seules les Soirées enregistrées ont de vrais Joueurs à qui confier un pouvoir. */
	private void verifierSoiree(UUID soireeId) {
		if (!soirees.estEnregistree(soireeId)) {
			throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Soirée introuvable.").exception();
		}
	}

	private static RuntimeException joueurIntrouvable() {
		return new Refuse(404, "RESSOURCE_INTROUVABLE", "Joueur introuvable.").exception();
	}

	/** Délégation d'un Joueur présent, pouvoirs dans l'ordre de l'énumération. */
	record Delegation(UUID joueurId, String pseudo, List<Pouvoir> pouvoirs) { }
}
