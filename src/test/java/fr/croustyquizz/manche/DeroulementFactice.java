package fr.croustyquizz.manche;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import java.util.Set;

/**
 * Mode de jeu minimal qui sert de modèle aux vrais modes et de doublure au socle :
 * chaque étape dure {@code tempsParEtape}, et la première action reçue pendant une
 * étape rapporte un point à son Joueur.
 */
final class DeroulementFactice implements DeroulementDeManche {

	record ActionFactice(UUID joueurId, Instant recueLe) implements ActionJoueur {
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
	public ResultatDeCommande recevoir(ActionJoueur action) {
		if (terminee || etape == 0 || enPause || etapeRemportee || !(action instanceof ActionFactice)) {
			return refus();
		}
		etapeRemportee = true;
		long tempsMs = contexte.reglages().tempsParEtape().minus(Duration.between(action.recueLe(), finEtapeLe)).toMillis();
		points.add(new AttributionDePoints(action.joueurId(), 1, "première action de l'étape " + etape, true, tempsMs));
		return new Accepte(null, Set.of(Destination.CONSOLE), "classement-modifie");
	}

	@Override
	public ResultatDeCommande mettreEnPause(CommandePilotage commande) {
		if (terminee || enPause || etape == 0) {
			return refus();
		}
		enPause = true;
		minuteur.annuler();
		resteAuMomentDeLaPause = Duration.between(contexte.horloge().maintenant(), finEtapeLe);
		return accepte(commande, "manche-en-pause");
	}

	@Override
	public ResultatDeCommande reprendre(CommandePilotage commande) {
		if (terminee || !enPause || etape == 0) {
			return refus();
		}
		enPause = false;
		programmerFinEtape(resteAuMomentDeLaPause);
		return accepte(commande, "manche-reprise");
	}

	@Override
	public ResultatDeCommande passerEtape(CommandePilotage commande) {
		if (etape == 0) { return new Refuse(409, "ETAPE_INACTIVE", "Aucune étape courante."); }
		if (terminee || enPause) {
			return refus();
		}
		minuteur.annuler();
		enPause = false;
		finirEtape();
		return accepte(commande, terminee ? "manche-terminee" : "question-ouverte");
	}

	@Override
	public ResultatDeCommande terminer(CommandePilotage commande) {
		if (terminee) {
			return refus();
		}
		minuteur.annuler();
		cloturer();
		return accepte(commande, "manche-terminee");
	}

	@Override
	public ResultatDeCommande commander(CommandeDeManche commande) { return refus(); }

	record Etat(int etape, boolean remportee, boolean pause, boolean terminee, Instant fin,
			Duration reste, List<AttributionDePoints> points) { }

	@Override
	public Object instantane() { return new Etat(etape, etapeRemportee, enPause, terminee, finEtapeLe, resteAuMomentDeLaPause, points()); }

	static DeroulementFactice restaurer(ContexteDeManche contexte, Etat etat, EtatManche cycle) {
		var d = new DeroulementFactice(contexte);
		d.etape = etat.etape(); d.etapeRemportee = etat.remportee(); d.enPause = cycle == EtatManche.EN_PAUSE;
		d.terminee = cycle == EtatManche.TERMINEE; d.finEtapeLe = etat.fin(); d.resteAuMomentDeLaPause = etat.reste();
		d.points.addAll(etat.points());
		if (!d.enPause && !d.terminee) {
			d.programmerFinEtape(Duration.between(contexte.horloge().maintenant(), etat.fin()).isNegative()
					? Duration.ZERO : Duration.between(contexte.horloge().maintenant(), etat.fin()));
		}
		return d;
	}

	private Refuse refus() { return new Refuse(409, "ETAT_INCOMPATIBLE", "Commande incompatible avec la Manche."); }
	private Accepte accepte(CommandePilotage commande, String type) {
		return new Accepte(commande.accuse(), Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES), type);
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
	}

}
