package fr.croustyquizz.campagne;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Minuteur;
import jakarta.annotation.PreDestroy;

@Component
class PlanificationCampagnes {
	private static final Logger JOURNAL = LoggerFactory.getLogger(PlanificationCampagnes.class);
	private static final Duration DELAI_REPRISE = Duration.ofSeconds(30);
	private static final Minuteur INACTIF = () -> { };

	private final DepotCampagnesSms depot;
	private final ExecutionCampagnes execution;
	private final Horloge horloge;

	private Minuteur minuteur = INACTIF;
	private boolean arretee;

	PlanificationCampagnes(DepotCampagnesSms depot, ExecutionCampagnes execution, Horloge horloge) {
		this.depot = depot;
		this.execution = execution;
		this.horloge = horloge;
	}

	@EventListener(ApplicationReadyEvent.class)
	void recharger() {
		recharger(Duration.ZERO);
	}

	void ajouter(CampagneSms campagne) {
		if (campagne.envoiPrevuLe().isAfter(horloge.maintenant())) {
			recharger();
		} else {
			executer(campagne.id());
		}
	}

	private synchronized void recharger(Duration delaiMinimum) {
		if (arretee) {
			return;
		}
		annulerMinuteur();
		try {
			var prochaine = depot.prochaine();
			prochaine.ifPresent(echeance -> {
				Instant maintenant = horloge.maintenant();
				Instant reveil = echeance.envoiPrevuLe().isAfter(maintenant)
						? echeance.envoiPrevuLe() : maintenant;
				Duration attente = Duration.between(maintenant, reveil);
				if (attente.compareTo(delaiMinimum) < 0) {
					attente = delaiMinimum;
				}
				// L’Horloge système programme en millisecondes. Arrondir vers le haut évite un réveil anticipé.
				Duration delai = attente.equals(Duration.ofMillis(attente.toMillis())) ? attente
						: Duration.ofMillis(attente.toMillis() + 1);
				minuteur = horloge.programmer(delai, () -> executer(echeance.id()));
			});
		} catch (RuntimeException panne) {
			JOURNAL.warn("Impossible de charger les échéances des Campagnes SMS", panne);
			minuteur = horloge.programmer(DELAI_REPRISE, this::recharger);
		}
	}

	private void executer(UUID id) {
		try {
			execution.executer(id);
		} catch (RuntimeException panne) {
			JOURNAL.warn("Exécution interrompue pour la Campagne SMS {}", id, panne);
			recharger(DELAI_REPRISE);
			return;
		}
		recharger();
	}

	synchronized void annulerMinuteur() {
		minuteur.annuler();
		minuteur = INACTIF;
	}

	@PreDestroy
	synchronized void arreter() {
		arretee = true;
		annulerMinuteur();
	}
}
