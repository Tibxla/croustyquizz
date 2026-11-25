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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DeroulementQuiz implements DeroulementDeManche {

	private record ReponseInterne(UUID joueurId, UUID propositionId, Instant recueLe) {}

	private final ContexteDeManche contexte;
	private final List<Question> questions;
	private final List<AttributionDePoints> pointsAttribues = new ArrayList<>();
	private final Map<UUID, ReponseInterne> reponsesParJoueur = new HashMap<>();
	private final Map<UUID, AccuseReponse> envoisParEnvoiId = new HashMap<>();

	private int indexQuestion;
	private UUID etapeId;
	private PhaseQuiz phase = PhaseQuiz.ATTENTE;
	private Instant ouverteLe;
	private Instant echeanceLe;
	private Duration resteAuMomentDeLaPause;
	private Minuteur minuteur = () -> {};
	private boolean terminee;

	public DeroulementQuiz(ContexteDeManche contexte, List<Question> questions) {
		if (questions == null || questions.isEmpty()) {
			throw new IllegalArgumentException("Une Manche de Quiz nécessite au moins une Question.");
		}
		this.contexte = contexte;
		this.questions = List.copyOf(questions);
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
		if (etat.resteAuMomentDeLaPauseMs() != null) {
			this.resteAuMomentDeLaPause = Duration.ofMillis(etat.resteAuMomentDeLaPauseMs());
		}
		this.envoisParEnvoiId.putAll(etat.envoisParEnvoiId());
		for (Map.Entry<UUID, UUID> entree : etat.reponsesParJoueur().entrySet()) {
			this.reponsesParJoueur.put(entree.getKey(), new ReponseInterne(entree.getKey(), entree.getValue(), ouverteLe != null ? ouverteLe : Instant.EPOCH));
		}
		this.pointsAttribues.addAll(etat.points());
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
				pointsAttribues
		);
	}

	public UUID etapeCouranteId() {
		return etapeId;
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
			if (reponse.propositionId().equals(q.propositionJusteId())) {
				long elapsedMs = Math.max(0, Math.min(totalMs, Duration.between(ouverteLe, reponse.recueLe()).toMillis()));
				int valeur = 50;
				if (totalMs > 0) {
					valeur = (int) (50 + (50 * (totalMs - elapsedMs)) / totalMs);
				}
				pointsAttribues.add(new AttributionDePoints(reponse.joueurId(), valeur, "Bonne réponse à la question " + (indexQuestion + 1)));
			}
		}
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
				terminerManche(false);
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
		terminerManche(true);
		return new Accepte(null, Set.of(Destination.CONSOLE, Destination.SALLE, Destination.TOUTES_LES_MANETTES), "manche-terminee");
	}

	private void terminerManche(boolean annuleeSansSignalement) {
		terminee = true;
		phase = PhaseQuiz.TERMINEE;
		minuteur.annuler();
		if (!annuleeSansSignalement) {
			contexte.signalerFin().run();
		}
	}

	@Override
	public Object instantane() {
		if (phase == PhaseQuiz.ATTENTE || phase == PhaseQuiz.TERMINEE) {
			return new InstantaneQuiz(phase, null, null, null, null, null);
		}

		Question q = questions.get(indexQuestion);
		List<PropositionPublique> propositions = q.propositions().stream()
				.map(p -> new PropositionPublique(p.id(), p.texte()))
				.toList();
		QuestionPublique questionPublique = new QuestionPublique(
				etapeId,
				q.id(),
				indexQuestion + 1,
				questions.size(),
				q.enonce(),
				propositions
		);

		if (phase == PhaseQuiz.QUESTION_OUVERTE) {
			return new InstantaneQuiz(phase, questionPublique, ouverteLe, echeanceLe, null, null);
		}

		if (phase == PhaseQuiz.EN_PAUSE) {
			Long restant = resteAuMomentDeLaPause != null ? resteAuMomentDeLaPause.toMillis() : null;
			return new InstantaneQuiz(phase, questionPublique, ouverteLe, echeanceLe, restant, null);
		}

		List<ReponseDistribution> repartition = q.propositions().stream()
				.map(p -> {
					int count = (int) reponsesParJoueur.values().stream().filter(r -> r.propositionId().equals(p.id())).count();
					return new ReponseDistribution(p.id(), count);
				})
				.toList();
		CorrectionPublique correction = new CorrectionPublique(q.propositionJusteId(), repartition);

		return new InstantaneQuiz(phase, questionPublique, ouverteLe, echeanceLe, null, correction);
	}

	@Override
	public boolean estTerminee() {
		return terminee;
	}

	@Override
	public List<AttributionDePoints> points() {
		return List.copyOf(pointsAttribues);
	}

	private ResultatDeCommande refusGenerique() {
		return new Refuse(409, "ETAT_INCOMPATIBLE", "Action incompatible avec l'état courant de la Manche.");
	}
}
