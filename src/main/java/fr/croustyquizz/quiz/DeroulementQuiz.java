package fr.croustyquizz.quiz;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.ActionJoueur;
import fr.croustyquizz.manche.AttributionDePoints;
import fr.croustyquizz.manche.CommandeDeManche;
import fr.croustyquizz.manche.CommandePilotage;
import fr.croustyquizz.manche.ContexteDeManche;
import fr.croustyquizz.manche.DeroulementDeManche;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Minuteur;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.ResultatDeCommande;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DeroulementQuiz implements DeroulementDeManche {

	private static final int POINTS_FIXES_PAR_BONNE_REPONSE = 100;
	private static final int POINTS_MIN_RAPIDITE = 50;
	private static final int POINTS_BONUS_RAPIDITE = 50;

	private record ReponseInterne(UUID joueurId, UUID propositionId, Instant recueLe) {}

	private final ContexteDeManche contexte;
	private final List<Question> questions;
	private final VarianteQuiz varianteChoisie;
	private final List<AttributionDePoints> pointsAttribues = new ArrayList<>();
	private final Map<UUID, ReponseInterne> reponsesParJoueur = new HashMap<>();
	private final Map<UUID, AccuseReponse> envoisParEnvoiId = new HashMap<>();
	private final Set<UUID> idsJoueursElimines = new HashSet<>();
	private final Set<UUID> joueursAyantParticipe = new HashSet<>();

	private int indexQuestion;
	private UUID etapeId;
	private PhaseQuiz phase = PhaseQuiz.ATTENTE;
	private Instant ouverteLe;
	private Instant echeanceLe;
	private Duration resteAuMomentDeLaPause;
	private Minuteur minuteur = () -> {};
	private boolean terminee;

	public DeroulementQuiz(ContexteDeManche contexte, List<Question> questions) {
		this(contexte, questions, VarianteQuiz.RAPIDITE);
	}

	public DeroulementQuiz(ContexteDeManche contexte, List<Question> questions, VarianteQuiz variante) {
		if (questions == null || questions.isEmpty()) {
			throw new IllegalArgumentException("Une Manche de Quiz nécessite au moins une Question.");
		}
		this.contexte = contexte;
		this.questions = List.copyOf(questions);
		this.varianteChoisie = variante != null ? variante : VarianteQuiz.RAPIDITE;
		this.etapeId = UUID.randomUUID();
	}

	public DeroulementQuiz(ContexteDeManche contexte, EtatQuizPersiste etat) {
		this.contexte = contexte;
		this.questions = etat.questions();
		this.indexQuestion = etat.indexQuestion();
		this.etapeId = etat.etapeCouranteId();
		this.phase = etat.phase();
		this.ouverteLe = etat.ouverteLe();
		this.echeanceLe = etat.echeanceLe();
		this.varianteChoisie = etat.variante() != null ? etat.variante() : VarianteQuiz.RAPIDITE;
		if (etat.resteAuMomentDeLaPauseMs() != null) {
			this.resteAuMomentDeLaPause = Duration.ofMillis(etat.resteAuMomentDeLaPauseMs());
		}
		this.envoisParEnvoiId.putAll(etat.envoisParEnvoiId());
		for (Map.Entry<UUID, UUID> entree : etat.reponsesParJoueur().entrySet()) {
			this.reponsesParJoueur.put(entree.getKey(), new ReponseInterne(entree.getKey(), entree.getValue(), ouverteLe != null ? ouverteLe : Instant.EPOCH));
		}
		this.pointsAttribues.addAll(etat.points());
		this.idsJoueursElimines.addAll(etat.joueursElimines());
		this.terminee = (phase == PhaseQuiz.TERMINEE);
	}

	public EtatQuizPersiste etatPersiste() {
		Map<UUID, UUID> rep = new HashMap<>();
		for (Map.Entry<UUID, ReponseInterne> e : reponsesParJoueur.entrySet()) {
			rep.put(e.getKey(), e.getValue().propositionId());
		}
		Long pauseMs = resteAuMomentDeLaPause != null ? resteAuMomentDeLaPause.toMillis() : null;
		return new EtatQuizPersiste(
				questions,
				indexQuestion,
				etapeId,
				phase,
				ouverteLe,
				echeanceLe,
				pauseMs,
				rep,
				envoisParEnvoiId,
				pointsAttribues,
				varianteChoisie,
				idsJoueursElimines
		);
	}

	public UUID etapeCouranteId() {
		return etapeId;
	}

	public VarianteQuiz variante() {
		return varianteChoisie;
	}

	public Set<UUID> joueursElimines() {
		return Set.copyOf(idsJoueursElimines);
	}

	@Override
	public boolean viseEtape(UUID etapeViseeId) {
		return etapeViseeId == null || etapeViseeId.equals(etapeId);
	}

	@Override
	public void demarrer() {
		if (terminee || phase != PhaseQuiz.ATTENTE) {
			return;
		}
		ouvrirQuestion(0);
	}

	private void ouvrirQuestion(int index) {
		indexQuestion = index;
		etapeId = UUID.randomUUID();
		phase = PhaseQuiz.QUESTION_OUVERTE;
		ouverteLe = contexte.horloge().maintenant();
		Duration duree = contexte.reglages().tempsParEtape();
		echeanceLe = ouverteLe.plus(duree);
		reponsesParJoueur.clear();
		minuteur = contexte.horloge().programmer(duree, this::cloturerQuestion);
	}

	private void cloturerQuestion() {
		if (terminee || phase != PhaseQuiz.QUESTION_OUVERTE) {
			return;
		}
		minuteur.annuler();
		phase = PhaseQuiz.CORRECTION;

		Question q = questions.get(indexQuestion);
		long totalMs = Duration.between(ouverteLe, echeanceLe).toMillis();

		for (ReponseInterne reponse : reponsesParJoueur.values()) {
			boolean estJuste = reponse.propositionId().equals(q.propositionJusteId());
			if (estJuste) {
				int valeur = calculerPoints(totalMs, reponse.recueLe());
				pointsAttribues.add(new AttributionDePoints(reponse.joueurId(), valeur, "Bonne réponse à la question " + (indexQuestion + 1)));
			} else if (varianteChoisie == VarianteQuiz.ELIMINATION) {
				idsJoueursElimines.add(reponse.joueurId());
			}
		}

		if (varianteChoisie == VarianteQuiz.ELIMINATION) {
			for (UUID joueur : joueursAyantParticipe) {
				if (!reponsesParJoueur.containsKey(joueur)) {
					idsJoueursElimines.add(joueur);
				}
			}
		}
	}

	private int calculerPoints(long totalMs, Instant recueLe) {
		if (varianteChoisie == VarianteQuiz.POINTS_FIXES || varianteChoisie == VarianteQuiz.ELIMINATION) {
			return POINTS_FIXES_PAR_BONNE_REPONSE;
		}
		long elapsedMs = Math.max(0, Math.min(totalMs, Duration.between(ouverteLe, recueLe).toMillis()));
		int valeur = POINTS_MIN_RAPIDITE;
		if (totalMs > 0) {
			valeur = (int) (POINTS_MIN_RAPIDITE + (POINTS_BONUS_RAPIDITE * (totalMs - elapsedMs)) / totalMs);
		}
		return valeur;
	}

	@Override
	public ResultatDeCommande recevoir(ActionJoueur action) {
		if (terminee || !(action instanceof EnvoyerReponse envoi)) {
			return refusGenerique();
		}

		AccuseReponse existant = envoisParEnvoiId.get(envoi.envoiId());
		if (existant != null) {
			if (existant.propositionId().equals(envoi.propositionId())) {
				return new Accepte(existant, Set.of(Destination.TOUTES_LES_MANETTES), "reponse-enregistree");
			}
			return new Refuse(409, "ENVOI_REUTILISE", "Identifiant d'envoi réutilisé pour un autre contenu.");
		}

		if (varianteChoisie == VarianteQuiz.ELIMINATION && idsJoueursElimines.contains(envoi.joueurId())) {
			return new Refuse(409, "ETAT_INCOMPATIBLE", "Le joueur a été éliminé de cette Manche de Quiz.");
		}

		if (phase != PhaseQuiz.QUESTION_OUVERTE) {
			if (contexte.horloge().maintenant().isAfter(echeanceLe != null ? echeanceLe : ouverteLe)) {
				return new Refuse(409, "HORS_DELAI", "Réponse reçue hors délai.");
			}
			return refusGenerique();
		}

		if (!envoi.etapeId().equals(etapeId)) {
			return new Refuse(409, "ETAPE_INACTIVE", "L'étape visée n'est pas l'étape courante.");
		}

		if (reponsesParJoueur.containsKey(envoi.joueurId())) {
			return new Refuse(409, "REPONSE_DEJA_ENREGISTREE", "Une réponse a déjà été enregistrée pour ce joueur.");
		}

		if (!envoi.recueLe().isBefore(echeanceLe)) {
			return new Refuse(409, "HORS_DELAI", "Réponse reçue hors délai.");
		}

		Question q = questions.get(indexQuestion);
		boolean propositionValide = q.propositions().stream().anyMatch(p -> p.id().equals(envoi.propositionId()));
		if (!propositionValide) {
			return new Refuse(422, "DONNEES_INVALIDES", "Proposition inconnue pour cette question.");
		}

		UUID reponseId = UUID.randomUUID();
		AccuseReponse accuse = new AccuseReponse(envoi.envoiId(), reponseId, etapeId, envoi.propositionId(), envoi.recueLe());
		envoisParEnvoiId.put(envoi.envoiId(), accuse);
		reponsesParJoueur.put(envoi.joueurId(), new ReponseInterne(envoi.joueurId(), envoi.propositionId(), envoi.recueLe()));
		joueursAyantParticipe.add(envoi.joueurId());

		return new Accepte(accuse, Set.of(Destination.TOUTES_LES_MANETTES), "reponse-enregistree");
	}

	@Override
	public ResultatDeCommande commander(CommandeDeManche commande) {
		return refusGenerique();
	}

	@Override
	public ResultatDeCommande mettreEnPause(CommandePilotage commande) {
		if (terminee || phase != PhaseQuiz.QUESTION_OUVERTE) {
			return refusGenerique();
		}
		minuteur.annuler();
		phase = PhaseQuiz.EN_PAUSE;
		resteAuMomentDeLaPause = Duration.between(contexte.horloge().maintenant(), echeanceLe);
		return new Accepte(null, Set.of(Destination.CONSOLE, Destination.SALLE, Destination.TOUTES_LES_MANETTES), "manche-en-pause");
	}

	@Override
	public ResultatDeCommande reprendre(CommandePilotage commande) {
		if (terminee || phase != PhaseQuiz.EN_PAUSE) {
			return refusGenerique();
		}
		phase = PhaseQuiz.QUESTION_OUVERTE;
		echeanceLe = contexte.horloge().maintenant().plus(resteAuMomentDeLaPause);
		minuteur = contexte.horloge().programmer(resteAuMomentDeLaPause, this::cloturerQuestion);
		return new Accepte(null, Set.of(Destination.CONSOLE, Destination.SALLE, Destination.TOUTES_LES_MANETTES), "manche-reprise");
	}

	@Override
	public ResultatDeCommande passerEtape(CommandePilotage commande) {
		if (terminee || phase == PhaseQuiz.EN_PAUSE || phase == PhaseQuiz.ATTENTE) {
			return refusGenerique();
		}

		if (phase == PhaseQuiz.QUESTION_OUVERTE) {
			cloturerQuestion();
			return new Accepte(null, Set.of(Destination.CONSOLE, Destination.SALLE, Destination.TOUTES_LES_MANETTES), "question-corrigee");
		}

		if (phase == PhaseQuiz.CORRECTION) {
			if (indexQuestion + 1 < questions.size()) {
				ouvrirQuestion(indexQuestion + 1);
				return new Accepte(null, Set.of(Destination.CONSOLE, Destination.SALLE, Destination.TOUTES_LES_MANETTES), "question-ouverte");
			} else {
				terminerMancheNaturellement();
				return new Accepte(null, Set.of(Destination.CONSOLE, Destination.SALLE, Destination.TOUTES_LES_MANETTES), "manche-terminee");
			}
		}

		return refusGenerique();
	}

	@Override
	public ResultatDeCommande terminer(CommandePilotage commande) {
		if (terminee) {
			return refusGenerique();
		}
		minuteur.annuler();
		phase = PhaseQuiz.TERMINEE;
		terminee = true;
		return new Accepte(null, Set.of(Destination.CONSOLE, Destination.SALLE, Destination.TOUTES_LES_MANETTES), "manche-terminee");
	}

	private void terminerMancheNaturellement() {
		minuteur.annuler();
		phase = PhaseQuiz.TERMINEE;
		terminee = true;
		if (contexte.signalerFin() != null) {
			contexte.signalerFin().run();
		}
	}

	@Override
	public Object instantane() {
		if (phase == PhaseQuiz.ATTENTE || phase == PhaseQuiz.TERMINEE) {
			return new InstantaneQuiz(phase, null, null, null, null, null);
		}
		Question q = questions.get(indexQuestion);
		QuestionPublique qPub = new QuestionPublique(etapeId, q.id(), indexQuestion + 1, questions.size(), q.enonce(),
				q.propositions().stream().map(p -> new PropositionPublique(p.id(), p.texte())).toList());

		Long tempsRestantMs = null;
		if (phase == PhaseQuiz.QUESTION_OUVERTE && echeanceLe != null) {
			tempsRestantMs = Math.max(0, Duration.between(contexte.horloge().maintenant(), echeanceLe).toMillis());
		} else if (phase == PhaseQuiz.EN_PAUSE && resteAuMomentDeLaPause != null) {
			tempsRestantMs = resteAuMomentDeLaPause.toMillis();
		}

		CorrectionPublique correction = null;
		if (phase == PhaseQuiz.CORRECTION) {
			Map<UUID, Integer> repartition = new HashMap<>();
			for (Proposition p : q.propositions()) {
				repartition.put(p.id(), 0);
			}
			for (ReponseInterne r : reponsesParJoueur.values()) {
				repartition.computeIfPresent(r.propositionId(), (k, v) -> v + 1);
			}
			List<ReponseDistribution> dist = repartition.entrySet().stream()
					.map(e -> new ReponseDistribution(e.getKey(), e.getValue()))
					.toList();
			correction = new CorrectionPublique(q.propositionJusteId(), dist);
		}

		return new InstantaneQuiz(phase, qPub, ouverteLe, echeanceLe, tempsRestantMs, correction);
	}

	@Override
	public List<AttributionDePoints> points() {
		return List.copyOf(pointsAttribues);
	}

	@Override
	public boolean estTerminee() {
		return terminee;
	}

	private ResultatDeCommande refusGenerique() {
		return new Refuse(409, "ETAT_INCOMPATIBLE", "Action non autorisée dans l'état actuel de la Manche de Quiz.");
	}
}
