package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.AjoutDeManche;
import fr.croustyquizz.manche.EnvoiDeCommande;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.PreparationDeCommande;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.ResultatDeCommande;
import fr.croustyquizz.manche.ServiceDeManche;
import fr.croustyquizz.manche.SocleDeManche;
import fr.croustyquizz.passage.ParticipantsDePassage;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service du Blind test (ADR 0009). Tout appel à Deezer se fait hors du verrou de la Soirée (ADR 0006) : le tirage à
 * l'ajout de la Manche, puis le lien d'extrait de chaque Morceau en préparation, demandé par une tâche à part que
 * lance chaque mutation enregistrée, minuteur de 8 s compris. La tâche reprend ensuite le verrou et le domaine
 * revérifie que ce Morceau est toujours attendu, sinon le lien est abandonné.
 */
// « BlindTest » nomme le Mode de jeu : PMD prend à tort la classe pour une classe de test.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class ServiceBlindTest extends ServiceDeManche<ReglagesBlindTest, EtatBlindTestPersiste> {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceBlindTest.class);
	/** Après ces échecs imprévus, le Morceau est sauté sans redemander son lien (ADR 0009, point 14). */
	static final int ECHECS_AVANT_SAUT = 3;
	/** Au-delà, même le saut échoue : la relance par la lecture suivante reste le dernier recours. */
	static final int ECHECS_MAX = 10;
	static final Duration DELAI_NOUVEL_ESSAI = Duration.ofSeconds(2);

	private final SocleDeManche socle;
	private final PersistanceBlindTestJpa persistance;
	private final CatalogueMusical catalogue;
	private final TirageDeMorceaux tirage;
	private final ParticipantsDePassage participants;
	private final Executor preparations;
	/** Morceaux dont le lien est en cours de demande : une seule demande à la fois par Morceau. */
	private final Set<UUID> enPreparation = ConcurrentHashMap.newKeySet();
	/** Échecs imprévus consécutifs par Morceau : retirés dès que la préparation aboutit ou est abandonnée. */
	private final Map<UUID, Integer> echecs = new ConcurrentHashMap<>();
	/** Morceaux dont un nouvel essai attend l'Horloge : une commande enregistrée entre-temps ne le devance pas. */
	private final Set<UUID> essaiProgramme = ConcurrentHashMap.newKeySet();

	public ServiceBlindTest(FabriqueBlindTest fabrique, PersistanceBlindTestJpa persistance, SocleDeManche socle,
			CatalogueMusical catalogue, TirageDeMorceaux tirage, ParticipantsDePassage participants,
			Executor preparations) {
		super(fabrique, persistance, socle);
		this.socle = socle;
		this.persistance = persistance;
		this.catalogue = catalogue;
		this.tirage = tirage;
		this.participants = participants;
		this.preparations = preparations;
	}

	// --- Ajout de la Manche ---

	/** Tire les Morceaux hors du verrou ; la sélection s'enregistre avec la Manche, jamais dans ses réglages. */
	@Override
	public AjoutDeManche preparerAjout(UUID soireeId, Object reglages) {
		ReglagesBlindTest valeurs = resoudreReglages(reglages);
		List<Morceau> selection;
		try {
			selection = tirage.tirer(valeurs);
		} catch (SourceMusicaleInvalide erreur) {
			throw new Refuse(422, "SOURCE_MUSICALE_INVALIDE", erreur.getMessage()).exception(erreur);
		} catch (SourceMusicaleIndisponible erreur) {
			throw new Refuse(503, "SOURCE_MUSICALE_INDISPONIBLE",
					"Le catalogue musical ne répond pas. Réessaie dans un instant.").exception(erreur);
		}
		ManchePersistante manche = preparer(soireeId, reglages);
		return new AjoutDeManche(manche, ajoutee -> persistance.enregistrerSelection(ajoutee.getMancheId(), selection));
	}

	// --- Préparation de chaque Morceau ---

	@Override
	protected void apresEnregistrement(ManchePersistante manche, Object instantane) {
		lancerPreparation(manche.getSoireeId(), manche.getMancheId(), (InstantaneBlindTest) instantane);
	}

	/**
	 * Toute lecture d'un Morceau encore attendu relance aussi sa demande de lien si aucune n'est en cours : après un
	 * redémarrage ou une demande interrompue, la Manche ne reste pas bloquée en préparation.
	 */
	@Override
	public Object instantane(UUID soireeId, UUID mancheId) {
		InstantaneBlindTest instantane = (InstantaneBlindTest) super.instantane(soireeId, mancheId);
		lancerPreparation(soireeId, mancheId, instantane);
		return instantane;
	}

	/** Sans bloquer ni appeler Deezer : la demande part sur sa propre tâche, une seule à la fois par Morceau. */
	private void lancerPreparation(UUID soireeId, UUID mancheId, InstantaneBlindTest etat) {
		MorceauEnCours courant = etat.courant();
		if (etat.etatManche() == EtatManche.TERMINEE || courant == null
				|| courant.phase() != PhaseBlindTest.PREPARATION) {
			return;
		}
		if (essaiProgramme.contains(courant.etapeId())) {
			return;
		}
		long idDeezer = etat.etat().selection().get(courant.numero() - 1).idDeezer();
		lancerPreparation(soireeId, mancheId, courant.etapeId(), idDeezer);
	}

	private void lancerPreparation(UUID soireeId, UUID mancheId, UUID etapeId, long idDeezer) {
		if (!enPreparation.add(etapeId)) {
			return;
		}
		try {
			// La tâche attend le verrou, donc la fin de la transaction en cours, avant de toucher au déroulement.
			preparations.execute(() -> preparerMorceau(soireeId, mancheId, etapeId, idDeezer));
		} catch (RejectedExecutionException arret) {
			// Application en cours d'arrêt : la prochaine lecture après le redémarrage relancera la demande.
			enPreparation.remove(etapeId);
			JOURNAL.warn("Préparation du Morceau {} de la Manche {} reportée", etapeId, mancheId, arret);
		}
	}

	/**
	 * Une panne imprévue (base, réseau hors du contrat du catalogue) ne fige pas la Manche : l'Horloge reprogramme un
	 * essai, puis le Morceau est sauté après {@value #ECHECS_AVANT_SAUT} échecs. Une capture ne relance rien, car elle
	 * rend la dernière vue publiée (ADR 0004).
	 */
	private void preparerMorceau(UUID soireeId, UUID mancheId, UUID etapeId, long idDeezer) {
		try {
			boolean sauter = echecs.getOrDefault(etapeId, 0) >= ECHECS_AVANT_SAUT;
			Optional<URI> lien = sauter ? Optional.empty() : lienExtrait(idDeezer);
			appliquer(soireeId, mancheId, deroulement -> lien
					.map(extrait -> ((DeroulementBlindTest) deroulement).ouvrirMorceau(etapeId, extrait))
					.orElseGet(() -> ((DeroulementBlindTest) deroulement).sauterMorceau(etapeId)));
			// Ouvert, sauté, ou refusé parce que ce Morceau n'est plus attendu : rien à réessayer.
			echecs.remove(etapeId);
			enPreparation.remove(etapeId);
		} catch (RuntimeException erreur) {
			int nombre = echecs.merge(etapeId, 1, Integer::sum);
			JOURNAL.error("Échec {} de la préparation du Morceau {} de la Manche {}", nombre, etapeId, mancheId, erreur);
			if (nombre < ECHECS_MAX) {
				// Marqué avant de libérer le Morceau : une commande enregistrée entre-temps ne relance pas avant l'Horloge.
				essaiProgramme.add(etapeId);
				enPreparation.remove(etapeId);
				socle.horloge().programmer(DELAI_NOUVEL_ESSAI, () -> {
					essaiProgramme.remove(etapeId);
					lancerPreparation(soireeId, mancheId, etapeId, idDeezer);
				});
			} else {
				echecs.remove(etapeId);
				enPreparation.remove(etapeId);
			}
		}
	}

	/** Hors du verrou. Un Deezer en panne équivaut à un Morceau sans extrait : il sera sauté (ADR 0009, point 14). */
	private Optional<URI> lienExtrait(long idDeezer) {
		try {
			return catalogue.lienExtrait(idDeezer);
		} catch (SourceMusicaleIndisponible erreur) {
			JOURNAL.warn("Lien d'extrait indisponible pour le Morceau Deezer {}", idDeezer, erreur);
			return Optional.empty();
		}
	}

	// --- Refus après la fin de la Manche ---

	/** Le contrôle d'une Manche terminée passe à {@link #verifierAvantPreparation}, qui connaît l'auteur. */
	@Override
	protected Refuse verifierEtat(ManchePersistante manche, boolean demarrage) {
		if (!demarrage && manche.getEtat() == EtatManche.TERMINEE) {
			return null;
		}
		return super.verifierEtat(manche, demarrage);
	}

	/**
	 * Pour un Joueur, le Morceau d'une Manche terminée n'est plus le Morceau affiché : {@code COMMANDE_PERIMEE} passe
	 * avant l'état (contrat, ordre des refus), comme pour le Buzz. L'Animateur garde le refus d'état.
	 */
	@Override
	protected Refuse verifierAvantPreparation(ManchePersistante manche, EnvoiDeCommande envoi, boolean demarrage) {
		if (demarrage || manche.getEtat() != EtatManche.TERMINEE) {
			return null;
		}
		return envoi.acteur().type() == Acteur.Type.JOUEUR ? perimee()
				: new Refuse(409, "ETAT_INCOMPATIBLE", "Commande incompatible avec la Manche.");
	}

	/** Brouillon déposé, ou lien préparé, après la fin de la Manche : ce Morceau n'est plus affiché. */
	@Override
	protected Refuse refusHorsDeroulement(ManchePersistante manche) {
		return manche.getEtat() == EtatManche.TERMINEE ? perimee() : super.refusHorsDeroulement(manche);
	}

	private static Refuse perimee() {
		return new Refuse(409, "COMMANDE_PERIMEE", "Ce Morceau n'est plus en cours.");
	}

	@Override
	protected EtatManche etatApresAcceptation(EtatManche etatParDefaut, Object instantane) {
		return ((InstantaneBlindTest) instantane).etatManche();
	}

	// --- Actions des Joueurs ---

	/**
	 * Buzz transmis par la route commune {@code envoyerBuzz} (accord de #8). {@code retenir} reçoit le Buzz qui prend
	 * la main, dans la transaction de la Manche et avant la publication, pour que {@code dernierBuzz} le montre.
	 */
	public ResultatDeCommande buzzer(UUID mancheId, EnvoiDeCommande envoi, UUID etapeId, String pseudo,
			Consumer<AccuseBuzz> retenir) {
		Acteur acteur = envoi.acteur();
		verifierPresence(acteur);
		return executer(mancheId, envoi, () -> PreparationDeCommande.prete((deroulement, recueLe) -> {
			ResultatDeCommande resultat = deroulement.recevoir(
					new BuzzerMorceau(acteur.identifiant(), pseudo, recueLe, envoi.envoiId(), etapeId));
			if (resultat instanceof Accepte accepte && accepte.accuse() instanceof AccuseBuzz buzz) {
				retenir.accept(buzz);
			}
			return resultat;
		}));
	}

	/** Brouillon de Proposition de titre : sans reçu, la vue n'est republiée qu'à la Manette de son auteur. */
	public ResultatDeCommande deposerBrouillon(UUID mancheId, UUID etapeId, Acteur acteur, long numero,
			BrouillonProposition brouillon) {
		verifierPresence(acteur);
		return appliquer(acteur.soireeId(), mancheId, deroulement -> deroulement.recevoir(new DeposerBrouillon(
				acteur.identifiant(), socle.horloge().maintenant(), etapeId, numero, brouillon)));
	}

	/** Validation de la Proposition de titre, dédupliquée par l'{@code envoiId} du corps. */
	public ResultatDeCommande validerProposition(UUID mancheId, UUID etapeId, EnvoiDeCommande envoi,
			BrouillonProposition proposition) {
		Acteur acteur = envoi.acteur();
		verifierPresence(acteur);
		return executer(mancheId, envoi, () -> PreparationDeCommande.prete((deroulement, recueLe) ->
				deroulement.recevoir(new ValiderProposition(acteur.identifiant(), recueLe, envoi.envoiId(), etapeId,
						proposition))));
	}

	/** Arbitrage de l'Animateur, dédupliqué par l'{@code Idempotency-Key} comme le pilotage (ADR 0009, point 15). */
	public ResultatDeCommande arbitrer(UUID mancheId, UUID etapeId, EnvoiDeCommande envoi, UUID propositionId,
			PartieDuMorceau partie, VerdictChamp verdict) {
		return executer(mancheId, envoi, () -> PreparationDeCommande.prete((deroulement, recueLe) ->
				deroulement.commander(new ArbitrerProposition(envoi.acteur(), recueLe, envoi.envoiId(), etapeId,
						propositionId, partie, verdict))));
	}

	private void verifierPresence(Acteur acteur) {
		if (!participants.estPresent(acteur.soireeId(), acteur.identifiant())) {
			throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Manche ou Joueur introuvable.").exception();
		}
	}

}
