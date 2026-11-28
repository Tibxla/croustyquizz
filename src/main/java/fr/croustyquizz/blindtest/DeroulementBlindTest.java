package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.ActionJoueur;
import fr.croustyquizz.manche.AttributionDePoints;
import fr.croustyquizz.manche.CommandeDeManche;
import fr.croustyquizz.manche.CommandePilotage;
import fr.croustyquizz.manche.ContexteDeManche;
import fr.croustyquizz.manche.DeroulementDeManche;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.Minuteur;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.ResultatDeCommande;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Domaine du Blind test (ADR 0009), sans catalogue ni publication. Le service demande le lien d'extrait du Morceau en
 * préparation hors du verrou ({@link #extraitAPreparer()}), puis appelle {@link #ouvrirMorceau} ou
 * {@link #sauterMorceau} sous le verrou, qui revérifient que ce Morceau est toujours attendu.
 */
// « BlindTest » nomme le Mode de jeu : PMD prend à tort la classe pour une classe de test.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class DeroulementBlindTest implements DeroulementDeManche {

	static final Duration DUREE_SAISIE = Duration.ofSeconds(20);
	static final Duration DELAI_SUIVANT = Duration.ofSeconds(8);
	static final String MORCEAU_EN_PREPARATION = "morceau-en-preparation";
	static final String MORCEAU_OUVERT = "morceau-ouvert";
	static final String PROPOSITION_JUGEE = "proposition-jugee";
	static final String MORCEAU_REVELE = "morceau-revele";
	static final String MORCEAU_SAUTE = "morceau-saute";
	static final String MANCHE_TERMINEE = "manche-terminee";
	static final String BROUILLON_DEPOSE = "brouillon-depose";
	static final String VERDICT_RENVERSE = "verdict-renverse";
	private static final Set<Destination> ECRANS = Set.of(
			Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);

	private final ContexteDeManche contexte;
	private final JoueursPresents joueursPresents;
	private final Supplier<UUID> identifiants;
	private final CorrecteurTolerant correcteur = new CorrecteurTolerant();
	private final List<Morceau> morceaux;
	/**
	 * Points des Morceaux déjà quittés. Ceux du Morceau courant se déduisent de ses Propositions, pour qu'un arbitrage
	 * les recalcule (ADR 0009, point 15) ; un état enregistré avant #294 les porte ici, sans Propositions.
	 */
	private final List<AttributionDePoints> pointsFiges = new ArrayList<>();
	private EtatManche etatManche;
	private MorceauEnCours courant;
	private AccuseBuzz dernierBuzz;
	private Minuteur minuteur = () -> { };
	private String derniereCause = MORCEAU_REVELE;

	public DeroulementBlindTest(ContexteDeManche contexte, List<Morceau> selection, JoueursPresents joueursPresents,
			Supplier<UUID> identifiants) {
		this(contexte, joueursPresents, identifiants, new EtatBlindTestPersiste(selection, null, List.of(), null),
				EtatManche.PRETE);
	}

	DeroulementBlindTest(ContexteDeManche contexte, JoueursPresents joueursPresents, Supplier<UUID> identifiants,
			EtatBlindTestPersiste persiste, EtatManche etatManche) {
		this.contexte = Objects.requireNonNull(contexte, "contexte");
		this.joueursPresents = Objects.requireNonNull(joueursPresents, "joueursPresents");
		this.identifiants = Objects.requireNonNull(identifiants, "identifiants");
		this.morceaux = persiste.selection();
		this.courant = persiste.courant();
		this.pointsFiges.addAll(persiste.points());
		this.dernierBuzz = persiste.dernierBuzz();
		this.etatManche = Objects.requireNonNull(etatManche, "etatManche");
		if (etatManche != EtatManche.PRETE && courant == null) {
			throw new IllegalStateException("Une Manche démarrée a un Morceau courant.");
		}
		if (etatManche == EtatManche.EN_COURS) {
			reprogrammer();
		}
	}

	@Override
	public void demarrer() {
		if (etatManche != EtatManche.PRETE) {
			throw new IllegalStateException("La Manche a déjà démarré.");
		}
		etatManche = EtatManche.EN_COURS;
		preparer(1);
	}

	/** Morceau dont le service doit demander le lien d'extrait, hors du verrou ; vide hors préparation. */
	public Optional<ExtraitAPreparer> extraitAPreparer() {
		if (etatManche == EtatManche.TERMINEE || courant == null || courant.phase() != PhaseBlindTest.PREPARATION) {
			return Optional.empty();
		}
		return Optional.of(new ExtraitAPreparer(courant.etapeId(), morceau().idDeezer()));
	}

	/** Met en lecture le Morceau en préparation ; les 30 s partent maintenant (ADR 0009, point 2). */
	public ResultatDeCommande ouvrirMorceau(UUID etapeId, URI lienExtrait) {
		Refuse refus = gardePreparation(etapeId);
		if (refus != null) {
			return refus;
		}
		Instant maintenant = maintenant();
		boolean enCours = etatManche == EtatManche.EN_COURS;
		courant = courant.ouvert(lienExtrait,
				enCours ? LectureExtrait.lancee(0, maintenant) : LectureExtrait.enPause(0));
		if (enCours) {
			programmerFinDExtrait();
		}
		return new Accepte(null, ECRANS, MORCEAU_OUVERT);
	}

	/** Saute sans points un Morceau dont le lien d'extrait n'a pu être obtenu (ADR 0009, point 14). */
	public ResultatDeCommande sauterMorceau(UUID etapeId) {
		Refuse refus = gardePreparation(etapeId);
		if (refus != null) {
			return refus;
		}
		if (passerAuSuivant()) {
			contexte.signalerFin().run();
			return new Accepte(null, ECRANS, MANCHE_TERMINEE);
		}
		return new Accepte(null, ECRANS, MORCEAU_SAUTE);
	}

	private Refuse gardePreparation(UUID etapeId) {
		Refuse refus = garde();
		if (refus != null) {
			return refus;
		}
		if (courant.phase() != PhaseBlindTest.PREPARATION || !courant.etapeId().equals(etapeId)) {
			return perimee();
		}
		return null;
	}

	@Override
	public ResultatDeCommande recevoir(ActionJoueur action) {
		Refuse refus = garde();
		if (refus != null) {
			return refus;
		}
		return switch (action) {
			case BuzzerMorceau buzz -> buzzer(buzz);
			case DeposerBrouillon depot -> deposer(depot);
			case ValiderProposition validation -> valider(validation);
			case null, default -> incompatible("Cette action n'existe pas au Blind test.");
		};
	}

	private ResultatDeCommande buzzer(BuzzerMorceau buzz) {
		if (perime(buzz.etapeId())) {
			return perimee();
		}
		Instant maintenant = maintenant();
		// L'extrait arrivé à 30 s ferme le Buzz, même si le rappel de fin d'extrait attend encore le verrou.
		if (courant.phase() != PhaseBlindTest.EXTRAIT || etatManche != EtatManche.EN_COURS
				|| neBuzzePlus(buzz.joueurId())
				|| courant.lecture().positionA(maintenant) >= LectureExtrait.DUREE_MS) {
			return new Refuse(409, "BUZZ_NON_AUTORISE", "Le Buzz n'est pas ouvert pour ce Joueur.");
		}
		minuteur.annuler();
		LectureExtrait enPause = courant.lecture().geleeA(maintenant);
		MainBlindTest main = MainBlindTest.donnee(buzz.joueurId(), buzz.pseudo(),
				Delai.depuis(maintenant, DUREE_SAISIE), enPause.positionMs(), courant.partiesDemandees());
		courant = courant.mainDonnee(main, enPause);
		dernierBuzz = new AccuseBuzz(buzz.envoiId(), buzz.joueurId(), buzz.pseudo(), buzz.recueLe(),
				courant.buzzAcceptes());
		programmer(DUREE_SAISIE, this::echeanceDeSaisie);
		return new Accepte(dernierBuzz, ECRANS, "buzz-recu");
	}

	private ResultatDeCommande deposer(DeposerBrouillon depot) {
		Refuse refus = gardeSaisie(depot.joueurId(), depot.etapeId());
		if (refus != null) {
			return refus;
		}
		MainBlindTest main = courant.main();
		if (!main.retiendrait(depot.numero())) {
			// Rejeu en retard d'un brouillon déjà dépassé : ignoré, sans publication.
			return new Accepte(null, Set.of(), BROUILLON_DEPOSE);
		}
		courant = courant.avecMain(main.avecBrouillon(depot.numero(), depot.brouillon()));
		// La vue n'est republiée qu'à la Manette de l'auteur, pour qu'un rechargement retrouve sa saisie.
		return new Accepte(null, Set.of(Destination.joueur(depot.joueurId())), BROUILLON_DEPOSE);
	}

	private ResultatDeCommande valider(ValiderProposition validation) {
		Refuse refus = gardeSaisie(validation.joueurId(), validation.etapeId());
		if (refus != null) {
			return refus;
		}
		if (etatManche != EtatManche.EN_COURS) {
			return incompatible("La Manche est en pause.");
		}
		Jugement jugement = juger(validation.proposition());
		return new Accepte(new AccuseProposition(validation.envoiId(), validation.etapeId(), validation.recueLe(),
				jugement.titre(), jugement.artiste(), jugement.points()), ECRANS, causeApresJugement());
	}

	/**
	 * Ordre des refus du contrat : Morceau qui n'est plus affiché, puis Joueur déjà jugé ou échéance atteinte, puis
	 * Joueur sans la main.
	 */
	private Refuse gardeSaisie(UUID joueurId, UUID etapeId) {
		if (perime(etapeId)) {
			return perimee();
		}
		MainBlindTest main = courant.main();
		boolean aLaMain = main != null && main.joueurId().equals(joueurId);
		if (courant.ontPropose().contains(joueurId) || aLaMain && main.echueA(maintenant())) {
			return new Refuse(409, "HORS_DELAI", "Le temps de saisie est écoulé.");
		}
		return aLaMain ? null : incompatible("Ce Joueur n'a pas la main.");
	}

	/** La cible n'est plus le Morceau affiché ; un Morceau révélé le reste jusqu'au suivant. */
	private boolean perime(UUID etapeId) {
		return !courant.etapeId().equals(etapeId);
	}

	private record Jugement(VerdictChamp titre, VerdictChamp artiste, int points) { }

	private Jugement juger(BrouillonProposition proposition) {
		minuteur.annuler();
		MainBlindTest main = courant.main();
		Morceau morceau = morceau();
		Set<PartieDuMorceau> demandees = main.demandeesParmi(courant.partiesDemandees());
		List<PartieTrouvee> trouvees = new ArrayList<>();
		ChampJuge[] champs = new ChampJuge[PartieDuMorceau.values().length];
		for (PartieDuMorceau partie : PartieDuMorceau.values()) {
			String saisie = proposition.champ(partie);
			VerdictChamp verdict = VerdictChamp.NON_DEMANDE;
			if (demandees.contains(partie)) {
				verdict = correcteur.estJuste(saisie, partie.attendue(morceau)) ? VerdictChamp.JUSTE : VerdictChamp.FAUX;
				if (verdict == VerdictChamp.JUSTE) {
					trouvees.add(trouvee(partie, morceau, main.joueurId(), main.pseudo()));
				}
			}
			champs[partie.ordinal()] = ChampJuge.juge(saisie == null ? "" : saisie, verdict);
		}
		PropositionJugee jugee = new PropositionJugee(propositionId(courant.etapeId(), main.joueurId()), main.joueurId(), main.pseudo(),
				main.tempsBuzzMs(), champs[PartieDuMorceau.TITRE.ordinal()], champs[PartieDuMorceau.ARTISTE.ordinal()]);
		int gagnes = jugee.points();
		courant = courant.juge(jugee, trouvees);
		if (courant.partiesDemandees().isEmpty() || tousBloques()) {
			reveler();
		} else {
			reprendreExtrait();
		}
		return new Jugement(verdict(PartieDuMorceau.TITRE, demandees, trouvees),
				verdict(PartieDuMorceau.ARTISTE, demandees, trouvees), gagnes);
	}

	private static VerdictChamp verdict(PartieDuMorceau partie, Set<PartieDuMorceau> demandees,
			List<PartieTrouvee> trouvees) {
		if (!demandees.contains(partie)) {
			return VerdictChamp.NON_DEMANDE;
		}
		return trouvees.stream().anyMatch(trouvee -> trouvee.partie() == partie) ? VerdictChamp.JUSTE
				: VerdictChamp.FAUX;
	}

	/** Un Joueur n'est jugé qu'une fois par Morceau : le couple Morceau et Joueur identifie sa Proposition. */
	static UUID propositionId(UUID etapeId, UUID joueurId) {
		return UUID.nameUUIDFromBytes((etapeId + "/" + joueurId).getBytes(StandardCharsets.UTF_8));
	}

	private static PartieTrouvee trouvee(PartieDuMorceau partie, Morceau morceau, UUID joueurId, String pseudo) {
		return new PartieTrouvee(partie, partie.attendue(morceau), joueurId, pseudo, partie.points());
	}

	/** Plus aucun Joueur présent ne peut buzzer : tous sont bloqués (ADR 0009, point 11). */
	private boolean tousBloques() {
		return joueursPresents.de(contexte.soireeId()).stream().allMatch(this::neBuzzePlus);
	}

	/**
	 * Un Joueur bloqué, ou déjà jugé sur ce Morceau, ne reprend plus la main : un arbitrage peut le débloquer sans lui
	 * rendre une seconde Proposition (relecture de #297).
	 */
	private boolean neBuzzePlus(UUID joueurId) {
		return courant.bloques().contains(joueurId) || courant.ontPropose().contains(joueurId);
	}

	private void reprendreExtrait() {
		LectureExtrait lecture = courant.lecture();
		if (lecture.estFinie()) {
			reveler();
			return;
		}
		boolean enCours = etatManche == EtatManche.EN_COURS;
		courant = courant.repris(enCours ? lecture.lanceeA(maintenant()) : lecture);
		if (enCours) {
			programmerFinDExtrait();
		}
	}

	private void reveler() {
		minuteur.annuler();
		if (!contexte.pilotageAutomatique()) {
			courant = courant.revele(null);
			return;
		}
		if (etatManche == EtatManche.EN_COURS) {
			courant = courant.revele(Delai.depuis(maintenant(), DELAI_SUIVANT));
			programmer(DELAI_SUIVANT, this::morceauSuivantAutomatique);
		} else {
			courant = courant.revele(Delai.gele(DELAI_SUIVANT));
		}
	}

	/** Prépare le Morceau suivant, ou termine la Manche après le dernier ; rend vrai si elle s'est terminée. */
	private boolean passerAuSuivant() {
		if (courant.numero() < morceaux.size()) {
			preparer(courant.numero() + 1);
			return false;
		}
		cloturer();
		return true;
	}

	private void preparer(int numero) {
		if (courant != null) {
			// Le Morceau quitté ne s'arbitre plus : ses points sont figés.
			pointsFiges.addAll(pointsDuMorceau(courant));
		}
		courant = MorceauEnCours.enPreparation(numero, identifiants.get());
	}

	private void cloturer() {
		minuteur.annuler();
		etatManche = EtatManche.TERMINEE;
	}

	private void finDExtrait() {
		reveler();
		derniereCause = MORCEAU_REVELE;
	}

	private void echeanceDeSaisie() {
		juger(courant.main().brouillon());
		derniereCause = causeApresJugement();
	}

	private void morceauSuivantAutomatique() {
		if (passerAuSuivant()) {
			contexte.signalerFin().run();
			derniereCause = MANCHE_TERMINEE;
		} else {
			derniereCause = MORCEAU_EN_PREPARATION;
		}
	}

	private String causeApresJugement() {
		return courant.phase() == PhaseBlindTest.REVELATION ? MORCEAU_REVELE : PROPOSITION_JUGEE;
	}

	/** Cause de la dernière mutation faite par un minuteur, pour l'événement publié par le service. */
	public String causeDernierMinuteur() {
		return derniereCause;
	}

	@Override
	public ResultatDeCommande commander(CommandeDeManche commande) {
		if (!(commande instanceof ArbitrerProposition arbitrage)) {
			return incompatible("Cette commande n'existe pas au Blind test.");
		}
		Refuse refus = garde();
		if (refus != null) {
			return refus;
		}
		return arbitrer(arbitrage);
	}

	/** Renverse le verdict d'un champ en Pilotage Manuel, Révélation et pause comprises (ADR 0009, point 15). */
	private ResultatDeCommande arbitrer(ArbitrerProposition arbitrage) {
		if (contexte.pilotageAutomatique()) {
			return incompatible("En Pilotage Automatique, le verdict de la correction est définitif.");
		}
		if (perime(arbitrage.etapeId())) {
			return perimee();
		}
		Optional<PropositionJugee> visee = courant.proposition(arbitrage.propositionId());
		if (visee.isEmpty()) {
			return new Refuse(404, "RESSOURCE_INTROUVABLE", "Cette Proposition n'existe pas sur ce Morceau.");
		}
		PropositionJugee proposition = visee.get();
		PartieDuMorceau partie = arbitrage.partie();
		ChampJuge champ = proposition.champ(partie);
		if (champ.verdict() == VerdictChamp.NON_DEMANDE) {
			return incompatible("Ce champ n'était pas demandé : une autre Proposition avait déjà trouvé cette partie.");
		}
		if (champ.verdict() == arbitrage.verdict()) {
			return new Accepte(arbitrage.accuse(false), Set.of(), VERDICT_RENVERSE);
		}
		PartieTrouvee trouvee = null;
		if (arbitrage.verdict() == VerdictChamp.JUSTE) {
			if (champ.estVide()) {
				return incompatible("Un champ vide ne peut pas être accordé.");
			}
			if (!courant.partiesDemandees().contains(partie)) {
				return incompatible("Un autre Joueur a trouvé cette partie depuis : ses points ne lui sont pas repris.");
			}
			trouvee = trouvee(partie, morceau(), proposition.joueurId(), proposition.pseudo());
		}
		courant = courant.arbitre(proposition.avec(partie, champ.renverse()), trouvee, partie);
		boolean revele = courant.phase() != PhaseBlindTest.REVELATION
				&& (courant.partiesDemandees().isEmpty()
						|| courant.phase() == PhaseBlindTest.EXTRAIT && tousBloques());
		if (revele) {
			reveler();
		}
		return new Accepte(arbitrage.accuse(true), ECRANS, revele ? MORCEAU_REVELE : VERDICT_RENVERSE);
	}

	@Override
	public ResultatDeCommande mettreEnPause(CommandePilotage commande) {
		Refuse refus = garde();
		if (refus != null) {
			return refus;
		}
		if (etatManche != EtatManche.EN_COURS) {
			return incompatible("La Manche est déjà en pause.");
		}
		minuteur.annuler();
		etatManche = EtatManche.EN_PAUSE;
		courant = courant.geleA(maintenant());
		return new Accepte(commande.accuse(), ECRANS, "manche-en-pause");
	}

	@Override
	public ResultatDeCommande reprendre(CommandePilotage commande) {
		Refuse refus = garde();
		if (refus != null) {
			return refus;
		}
		if (etatManche != EtatManche.EN_PAUSE) {
			return incompatible("La Manche n'est pas en pause.");
		}
		etatManche = EtatManche.EN_COURS;
		courant = courant.relanceA(maintenant());
		reprogrammer();
		return new Accepte(commande.accuse(), ECRANS, "manche-reprise");
	}

	/** Un Morceau en cours se termine et se révèle ; seul un passage pendant la Révélation mène au suivant. */
	@Override
	public ResultatDeCommande passerEtape(CommandePilotage commande) {
		Refuse refus = garde();
		if (refus != null) {
			return refus;
		}
		if (etatManche != EtatManche.EN_COURS) {
			return incompatible("La Manche est en pause.");
		}
		if (courant.phase() != PhaseBlindTest.REVELATION) {
			reveler();
			return new Accepte(commande.accuse(), ECRANS, MORCEAU_REVELE);
		}
		minuteur.annuler();
		return new Accepte(commande.accuse(), ECRANS, passerAuSuivant() ? MANCHE_TERMINEE : MORCEAU_EN_PREPARATION);
	}

	@Override
	public ResultatDeCommande terminer(CommandePilotage commande) {
		Refuse refus = garde();
		if (refus != null) {
			return refus;
		}
		cloturer();
		return new Accepte(commande.accuse(), ECRANS, MANCHE_TERMINEE);
	}

	@Override
	public boolean viseEtape(UUID etapeId) {
		return courant == null ? etapeId == null : courant.etapeId().equals(etapeId);
	}

	@Override
	public InstantaneBlindTest instantane() {
		return new InstantaneBlindTest(etatManche, new EtatBlindTestPersiste(morceaux, courant, pointsFiges,
				dernierBuzz));
	}

	@Override
	public boolean estTerminee() {
		return etatManche == EtatManche.TERMINEE;
	}

	@Override
	public List<AttributionDePoints> points() {
		List<AttributionDePoints> points = new ArrayList<>(pointsFiges);
		if (courant != null) {
			points.addAll(pointsDuMorceau(courant));
		}
		return List.copyOf(points);
	}

	private static List<AttributionDePoints> pointsDuMorceau(MorceauEnCours morceau) {
		return morceau.propositions().stream().flatMap(proposition -> proposition.attribution().stream()).toList();
	}

	/** Reprogramme le minuteur de la phase courante, à la reprise ou à la restauration d'une Manche en cours. */
	private void reprogrammer() {
		Instant maintenant = maintenant();
		switch (courant.phase()) {
			case EXTRAIT -> programmer(courant.lecture().restantA(maintenant), this::finDExtrait);
			case PROPOSITION -> programmer(courant.main().saisie().restantA(maintenant), this::echeanceDeSaisie);
			case REVELATION -> {
				if (courant.attente() != null) {
					programmer(courant.attente().restantA(maintenant), this::morceauSuivantAutomatique);
				}
			}
			case PREPARATION -> {
				// Le service redemande le lien d'extrait : aucun minuteur.
			}
		}
	}

	private void programmerFinDExtrait() {
		programmer(courant.lecture().restantA(maintenant()), this::finDExtrait);
	}

	private void programmer(Duration delai, Runnable action) {
		minuteur = contexte.horloge().programmer(delai, action);
	}

	private Morceau morceau() {
		return morceaux.get(courant.numero() - 1);
	}

	private Instant maintenant() {
		return contexte.horloge().maintenant();
	}

	private Refuse garde() {
		if (etatManche == EtatManche.TERMINEE) {
			return incompatible("La Manche est terminée.");
		}
		if (etatManche == EtatManche.PRETE) {
			return new Refuse(409, "ETAPE_INACTIVE", "La Manche n'a pas commencé.");
		}
		return null;
	}

	private static Refuse incompatible(String detail) {
		return new Refuse(409, "ETAT_INCOMPATIBLE", detail);
	}

	private static Refuse perimee() {
		return new Refuse(409, "COMMANDE_PERIMEE", "Ce Morceau n'est plus en cours.");
	}

}
