package fr.croustyquizz.manche;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import fr.croustyquizz.tempsreel.Evenement;

/**
 * Mode de jeu minimal qui sert de modèle aux vrais modes et de doublure au socle :
 * chaque étape dure {@code tempsParEtape}, et la première action reçue pendant une
 * étape rapporte un point à son Joueur.
 */
final class DeroulementFactice implements DeroulementDeManche {

	record ActionFactice(UUID joueurId, Instant recueLe) implements ActionJoueur {
	}

	record EtapeCommencee(int numero) implements Evenement {
		@Override
		public String type() {
			return "etape-commencee";
		}
	}

	record MancheTerminee() implements Evenement {
		@Override
		public String type() {
			return "manche-terminee";
		}
	}

	private final ContexteDeManche contexte;
	private final List<AttributionDePoints> points = new ArrayList<>();
	private int etape;
	private boolean etapeRemportee;
	private boolean enPause;
	private boolean terminee;
	private Instant finEtapeLe;
	private Duration resteAuMomentDeLaPause;
	private Minuteur minuteur = () -> {
	};

	DeroulementFactice(ContexteDeManche contexte) {
		this.contexte = contexte;
	}

	@Override
	public void demarrer() {
		commencerEtape(1);
	}

	@Override
	public void recevoir(ActionJoueur action) {
		if (terminee || enPause || etapeRemportee || !(action instanceof ActionFactice)) {
			return;
		}
		etapeRemportee = true;
		points.add(new AttributionDePoints(action.joueurId(), 1, "première action de l'étape " + etape));
	}

	@Override
	public void mettreEnPause() {
		if (terminee || enPause) {
			return;
		}
		enPause = true;
		minuteur.annuler();
		resteAuMomentDeLaPause = Duration.between(contexte.horloge().maintenant(), finEtapeLe);
	}

	@Override
	public void reprendre() {
		if (terminee || !enPause) {
			return;
		}
		enPause = false;
		programmerFinEtape(resteAuMomentDeLaPause);
	}

	@Override
	public void passerEtape() {
		if (terminee) {
			return;
		}
		minuteur.annuler();
		enPause = false;
		finirEtape();
	}

	@Override
	public void terminer() {
		if (terminee) {
			return;
		}
		minuteur.annuler();
		cloturer();
	}

	@Override
	public boolean estTerminee() {
		return terminee;
	}

	@Override
	public List<AttributionDePoints> points() {
		return List.copyOf(points);
	}

	private void commencerEtape(int numero) {
		etape = numero;
		etapeRemportee = false;
		contexte.diffuseur().diffuserSalle(contexte.soireeId(), new EtapeCommencee(numero));
		programmerFinEtape(contexte.reglages().tempsParEtape());
	}

	private void programmerFinEtape(Duration delai) {
		finEtapeLe = contexte.horloge().maintenant().plus(delai);
		minuteur = contexte.horloge().programmer(delai, this::finirEtape);
	}

	private void finirEtape() {
		if (etape < contexte.reglages().nombreEtapes()) {
			commencerEtape(etape + 1);
		} else {
			cloturer();
			contexte.signalerFin().run();
		}
	}

	private void cloturer() {
		terminee = true;
		contexte.diffuseur().diffuserSalle(contexte.soireeId(), new MancheTerminee());
	}

}
