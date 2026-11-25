package fr.croustyquizz.manche;

import static java.time.temporal.ChronoUnit.MICROS;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import fr.croustyquizz.tempsreel.FournisseurDeChampCommun;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.VerrousSoiree;

/**
 * Transition du Pilotage Automatique entre deux Manches (#123). À la fin d'une Manche d'une Soirée en cours et en
 * Automatique, elle enregistre l'échéance et programme la suite ; à l'échéance, {@link EnchainementDeSoiree}
 * démarre la Manche suivante ou termine la Soirée. Les vues portent {@code transitionManche} tant qu'aucune Manche
 * n'est active. L'Animateur garde ses commandes de Soirée : un démarrage efface la ligne, et le rappel ne fait plus rien.
 * Ne référence jamais {@code soiree} : l'état et la suite de la Soirée passent par des ports.
 */
@Component
public final class PilotageAutomatique implements ObservateurDeFinDeManche, FournisseurDeChampCommun {
	private static final Logger JOURNAL = LoggerFactory.getLogger(PilotageAutomatique.class);
	/** Délai avant de rejouer une échéance quand la base n'a pu ni enchaîner ni effacer la transition. */
	static final Duration NOUVEL_ESSAI = Duration.ofSeconds(5);
	private static final Set<Destination> TOUT = Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);
	private final PilotageDeSoiree pilotage;
	private final Horloge horloge;
	private final VerrousSoiree verrous;
	private final TransactionTemplate ecriture;
	private final TransactionTemplate lecture;
	private final EntityManager entites;
	private final DepotDeManches depot;
	private final EtatDeSoiree etatDeSoiree;
	private final Duration dureeTransition;
	// Paresseux : le socle dépend de cet observateur, la suite et la publication dépendent du socle ou des vues.
	private final ObjectProvider<EnchainementDeSoiree> enchainement;
	private final ObjectProvider<PublicationDeVues> vues;

	/** Champ {@code transitionManche} des vues de la salle, de la Console et des Manettes. */
	public record TransitionManche(Instant finPrevueLe, ModeDeJeu modeSuivant) { }

	PilotageAutomatique(PilotageDeSoiree pilotage, Horloge horloge, VerrousSoiree verrous,
			PlatformTransactionManager transactions, EntityManager entites, DepotDeManches depot, EtatDeSoiree etatDeSoiree,
			ProprietesManche proprietes, ObjectProvider<EnchainementDeSoiree> enchainement,
			ObjectProvider<PublicationDeVues> vues) {
		this.pilotage = pilotage;
		this.horloge = horloge;
		this.verrous = verrous;
		this.entites = entites;
		this.depot = depot;
		this.etatDeSoiree = etatDeSoiree;
		this.dureeTransition = proprietes.dureeTransition();
		this.enchainement = enchainement;
		this.vues = vues;
		ecriture = new TransactionTemplate(transactions);
		ecriture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		lecture = new TransactionTemplate(transactions);
		lecture.setReadOnly(true);
	}

	/** R4 : appelé par le socle dans la transaction de la fin de Manche ; un retour arrière efface la ligne. */
	@Override
	public void surFinDeManche(UUID soireeId, UUID mancheId, Instant maintenant) {
		if (!pilotage.estAutomatique(soireeId) || !etatDeSoiree.estEnCours(soireeId)) {
			return;
		}
		Instant finPrevueLe = maintenant.plus(dureeTransition).truncatedTo(MICROS);
		entites.merge(new TransitionPersistante(soireeId, mancheId, finPrevueLe));
		programmer(soireeId, dureeTransition, finPrevueLe);
	}

	/**
	 * R5 : seul le rappel qui porte l'échéance enregistrée enchaîne. La ligne ne disparaît qu'avec la suite : le
	 * démarrage d'une Manche l'efface dans sa propre transaction ({@link #surDebutDeManche}), et une fin de Soirée
	 * la laisse à supprimer ici. Un arrêt entre les deux laisse la ligne, que {@link #reprendre} rejoue.
	 */
	void echoir(UUID soireeId, Instant finPrevueLe) {
		verrous.executer(soireeId, () -> {
			try {
				UUID terminee = lecture.execute(statut -> aEnchainer(soireeId, finPrevueLe));
				if (terminee != null) {
					enchainement.getObject().mancheSuivante(soireeId, terminee);
				}
				ecriture.execute(statut -> supprimer(soireeId, finPrevueLe));
			} catch (RuntimeException erreur) {
				JOURNAL.error("Échec de l'enchaînement automatique de la Soirée {}", soireeId, erreur);
				if (effacer(soireeId, finPrevueLe)) {
					republier(soireeId);
				} else {
					programmer(soireeId, NOUVEL_ESSAI, finPrevueLe);
				}
			}
			return null;
		});
	}

	/** Le démarrage d'une Manche, automatique ou demandé par l'Animateur, clôt la transition dans sa transaction. */
	@Override
	public void surDebutDeManche(UUID soireeId, UUID mancheId) {
		var transition = entites.find(TransitionPersistante.class, soireeId);
		if (transition != null) {
			entites.remove(transition);
		}
	}

	/** R8 : chaque transition enregistrée reprend pour le temps qui restait, ou disparaît hors Automatique. */
	@EventListener(ApplicationReadyEvent.class)
	void reprendre() {
		List<TransitionPersistante> transitions = lecture.execute(statut -> entites
				.createQuery("select t from TransitionPersistante t", TransitionPersistante.class).getResultList());
		Instant maintenant = horloge.maintenant();
		for (var transition : transitions) {
			UUID soireeId = transition.getSoireeId();
			Instant finPrevueLe = transition.getFinPrevueLe();
			try {
				verrous.executer(soireeId, () -> {
					if (pilotage.estAutomatique(soireeId)) {
						Duration reste = Duration.between(maintenant, finPrevueLe);
						programmer(soireeId, reste.isNegative() ? Duration.ZERO : reste, finPrevueLe);
					} else {
						ecriture.execute(statut -> supprimer(soireeId, finPrevueLe));
					}
					return null;
				});
			} catch (RuntimeException erreur) {
				JOURNAL.error("Échec de la reprise de la transition de la Soirée {}", soireeId, erreur);
			}
		}
	}

	@Override
	public String champ() { return "transitionManche"; }

	@Override
	public boolean concerne(Destination destination) {
		return destination.type() == Destination.Type.SALLE || destination.type() == Destination.Type.CONSOLE
				|| destination.type() == Destination.Type.JOUEUR;
	}

	/** R7 : appelé sous le verrou, dans la transaction de la publication ou dans une lecture propre. */
	@Override
	public Object fournir(UUID soireeId, Destination destination, Map<String, Object> partieCommune) {
		return lecture.execute(statut -> {
			var transition = entites.find(TransitionPersistante.class, soireeId);
			if (transition == null) {
				return null;
			}
			var manches = depot.deLaSoiree(soireeId);
			if (manches.stream().anyMatch(PilotageAutomatique::estActive) || !etatDeSoiree.estEnCours(soireeId)) {
				return null;
			}
			ModeDeJeu suivant = manches.stream().filter(manche -> manche.getEtat() == EtatManche.PRETE)
					.map(ManchePersistante::getMode).findFirst().orElse(null);
			return new TransitionManche(transition.getFinPrevueLe(), suivant);
		});
	}

	private void programmer(UUID soireeId, Duration delai, Instant finPrevueLe) {
		horloge.programmer(delai, () -> echoir(soireeId, finPrevueLe));
	}

	/** La Manche terminée quand la ligne porte cette échéance et que la suite peut partir ; sinon {@code null}. */
	private UUID aEnchainer(UUID soireeId, Instant finPrevueLe) {
		var transition = entites.find(TransitionPersistante.class, soireeId);
		if (transition == null || !transition.getFinPrevueLe().equals(finPrevueLe)) {
			return null;
		}
		boolean libre = depot.deLaSoiree(soireeId).stream().noneMatch(PilotageAutomatique::estActive);
		return libre && etatDeSoiree.estEnCours(soireeId) ? transition.getMancheTermineeId() : null;
	}

	/** Supprime la ligne de cette échéance seulement : une fin de Manche plus récente l'a peut-être remplacée. */
	private Void supprimer(UUID soireeId, Instant finPrevueLe) {
		var transition = entites.find(TransitionPersistante.class, soireeId);
		if (transition != null && transition.getFinPrevueLe().equals(finPrevueLe)) {
			entites.remove(transition);
		}
		return null;
	}

	/**
	 * Après un échec, la ligne échue ne doit plus apparaître dans la vue republiée. Si la base ne répond pas non
	 * plus, la ligne reste et l'échéance est rejouée après {@link #NOUVEL_ESSAI}.
	 */
	private boolean effacer(UUID soireeId, Instant finPrevueLe) {
		try {
			ecriture.execute(statut -> supprimer(soireeId, finPrevueLe));
			return true;
		} catch (RuntimeException erreur) {
			JOURNAL.error("Échec de la suppression de la transition de la Soirée {}, nouvel essai dans {} s", soireeId,
					NOUVEL_ESSAI.toSeconds(), erreur);
			return false;
		}
	}

	/** Aucun écran ne reste sur « dans 0 s » : la vue sans transition repart, l'Animateur garde MANCHE_SUIVANTE. */
	private void republier(UUID soireeId) {
		try {
			ecriture.execute(statut -> {
				vues.getObject().publier(soireeId, new Accepte(null, TOUT, "manche-terminee"));
				return null;
			});
		} catch (RuntimeException erreur) {
			JOURNAL.error("Échec de la republication des vues de la Soirée {}", soireeId, erreur);
		}
	}

	private static boolean estActive(ManchePersistante manche) {
		return manche.getEtat() == EtatManche.EN_COURS || manche.getEtat() == EtatManche.EN_PAUSE;
	}
}
