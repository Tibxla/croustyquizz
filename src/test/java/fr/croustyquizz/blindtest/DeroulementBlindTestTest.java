package fr.croustyquizz.blindtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.ActionJoueur;
import fr.croustyquizz.manche.AttributionDePoints;
import fr.croustyquizz.manche.CommandeDeManche;
import fr.croustyquizz.manche.CommandePilotage;
import fr.croustyquizz.manche.ContexteDeManche;
import fr.croustyquizz.manche.ContratDeroulementDeManche;
import fr.croustyquizz.manche.DeroulementDeManche;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.ReglagesDeManche;
import fr.croustyquizz.manche.ResultatDeCommande;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeroulementBlindTestTest extends ContratDeroulementDeManche {

	private static final URI LIEN = URI.create("https://cdnt-preview.dzcdn.net/extrait.mp3?hdnea=signe");
	private static final Morceau HALO = new Morceau(3135556, "Halo", "Beyoncé",
			URI.create("https://e-cdns-images.dzcdn.net/images/cover/halo.jpg"));
	private static final Morceau GET_LUCKY = new Morceau(67238735, "Get Lucky", "Daft Punk", null);
	private static final Morceau RESPECT = new Morceau(2114295, "Respect", "Aretha Franklin", null);
	private static final List<Morceau> SELECTION = List.of(HALO, GET_LUCKY, RESPECT);
	private static final ReglagesBlindTest REGLAGES = new ReglagesBlindTest(
			new SourceDeMorceaux(SourceDeMorceaux.Type.PLAYLIST, 908_622_995L), 3);

	private static final UUID LEA = JOUEUR;
	private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-00000000a002");
	private static final UUID NOE = UUID.fromString("00000000-0000-0000-0000-00000000a003");
	private static final UUID RETARDATAIRE = UUID.fromString("00000000-0000-0000-0000-00000000a009");

	private final Set<UUID> presents = new HashSet<>(Set.of(LEA, SAM, NOE));
	private int etapesTirees;
	private long numeroBrouillon;
	private DeroulementBlindTest blindTest;

	private FabriqueBlindTest fabrique() {
		return new FabriqueBlindTest(mancheId -> SELECTION, soireeId -> Set.copyOf(presents),
				() -> new UUID(0xb1d, ++etapesTirees));
	}

	@Override
	protected DeroulementDeManche creer(ContexteDeManche contexte) {
		blindTest = fabrique().creer(contexte, REGLAGES);
		return new LienFourniAuDemarrage(blindTest);
	}

	@Override
	protected ActionJoueur uneAction(UUID joueurId, Instant recueLe) {
		return new BuzzerMorceau(joueurId, "Léa", recueLe, UUID.randomUUID(), etape());
	}

	// --- Buzz et Proposition de titre ---

	@Test
	void lePremierBuzzMetLExtraitEnPauseEtDonneLaMain() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(5));

		ResultatDeCommande resultat = buzz(LEA);

		assertThat(resultat).isInstanceOfSatisfying(Accepte.class, accepte -> {
			assertThat(accepte.typeEvenement()).isEqualTo("buzz-recu");
			assertThat(accepte.aRepublier()).containsExactlyInAnyOrder(
					Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);
			assertThat(accepte.accuse()).isInstanceOfSatisfying(AccuseBuzz.class, accuse -> {
				assertThat(accuse.joueurId()).isEqualTo(LEA);
				assertThat(accuse.rang()).isEqualTo(1);
			});
		});
		MorceauEnCours courant = courant();
		assertThat(courant.phase()).isEqualTo(PhaseBlindTest.PROPOSITION);
		assertThat(courant.lecture()).isEqualTo(new LectureExtrait(5000, null));
		assertThat(courant.main().joueurId()).isEqualTo(LEA);
		assertThat(courant.main().saisie()).isEqualTo(new Delai(horloge.maintenant().plusSeconds(20), null));
		assertThat(courant.lienExtrait()).isEqualTo(LIEN);
		assertThat(blindTest.instantane().dernierBuzz()).isEqualTo(((Accepte) resultat).accuse());
		refusAttendu(buzz(SAM), "BUZZ_NON_AUTORISE");
	}

	@Test
	void lEcheanceJugeLeDernierBrouillonRecu() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(3));
		buzz(LEA);

		ResultatDeCommande depot = deposer(LEA, "hallo", "");
		deposer(LEA, "halo", "BEYONCE");

		assertThat(depot).isInstanceOfSatisfying(Accepte.class, accepte -> {
			assertThat(accepte.accuse()).isNull();
			assertThat(accepte.typeEvenement()).isEqualTo("brouillon-depose");
			assertThat(accepte.aRepublier()).containsExactly(Destination.joueur(LEA));
		});
		assertThat(courant().main().brouillon()).isEqualTo(new BrouillonProposition("halo", "BEYONCE"));
		horloge.avancer(Duration.ofMillis(19_999));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.PROPOSITION);
		horloge.avancer(Duration.ofMillis(1));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		assertThat(fabrique().typeEvenementMinuteur(blindTest)).isEqualTo("morceau-revele");
		assertThat(blindTest.points()).containsExactly(
				new AttributionDePoints(LEA, 100, "Titre et artiste trouvés", true, 3000));
	}

	@Test
	void unBrouillonRejoueEnRetardNEcrasePasUnBrouillonPlusRecent() {
		demarrer();
		buzz(LEA);
		deposer(LEA, 2, "halo", "beyonce");
		Object retenu = blindTest.instantane();

		ResultatDeCommande rejeu = deposer(LEA, 1, "hal", "");

		assertThat(rejeu).isInstanceOfSatisfying(Accepte.class, accepte -> {
			assertThat(accepte.accuse()).isNull();
			assertThat(accepte.aRepublier()).isEmpty();
		});
		assertThat(blindTest.instantane()).isEqualTo(retenu);
		assertThat(courant().main().brouillon()).isEqualTo(new BrouillonProposition("halo", "beyonce"));
		assertThat(courant().main().numeroBrouillon()).isEqualTo(2);
	}

	@Test
	void sansBrouillonLEcheanceBloqueLeJoueurEtRouvreLeBuzz() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(2));
		buzz(LEA);

		horloge.avancer(Duration.ofSeconds(20));

		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.EXTRAIT);
		assertThat(courant().bloques()).containsExactly(LEA);
		assertThat(courant().lecture()).isEqualTo(new LectureExtrait(2000, horloge.maintenant()));
		assertThat(fabrique().typeEvenementMinuteur(blindTest)).isEqualTo("proposition-jugee");
		assertThat(blindTest.points()).isEmpty();
	}

	@Test
	void unePartieTrouveeEstMontreeEtLeMorceauContinue() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(4));
		buzz(LEA);

		ResultatDeCommande resultat = valider(LEA, "Halo", "Rihanna");

		assertThat(resultat).isInstanceOfSatisfying(Accepte.class, accepte -> {
			assertThat(accepte.typeEvenement()).isEqualTo("proposition-jugee");
			assertThat(accepte.accuse()).isInstanceOfSatisfying(AccuseProposition.class, accuse -> {
				assertThat(accuse.titre()).isEqualTo(VerdictChamp.JUSTE);
				assertThat(accuse.artiste()).isEqualTo(VerdictChamp.FAUX);
				assertThat(accuse.pointsGagnes()).isEqualTo(70);
			});
		});
		MorceauEnCours courant = courant();
		assertThat(courant.phase()).isEqualTo(PhaseBlindTest.EXTRAIT);
		assertThat(courant.lecture()).isEqualTo(new LectureExtrait(4000, horloge.maintenant()));
		assertThat(courant.partiesTrouvees()).containsExactly(
				new PartieTrouvee(PartieDuMorceau.TITRE, "Halo", LEA, "Léa", 70));
		assertThat(courant.partiesDemandees()).containsExactly(PartieDuMorceau.ARTISTE);
		assertThat(courant.bloques()).containsExactly(LEA);
		refusAttendu(buzz(LEA), "BUZZ_NON_AUTORISE");

		horloge.avancer(Duration.ofSeconds(2));
		assertThat(buzz(SAM)).isInstanceOfSatisfying(Accepte.class,
				accepte -> assertThat(((AccuseBuzz) accepte.accuse()).rang()).isEqualTo(2));
		ResultatDeCommande artiste = valider(SAM, "", "beyonce");

		assertThat(artiste).isInstanceOfSatisfying(Accepte.class, accepte -> {
			assertThat(accepte.typeEvenement()).isEqualTo("morceau-revele");
			AccuseProposition accuse = (AccuseProposition) accepte.accuse();
			assertThat(accuse.titre()).isEqualTo(VerdictChamp.NON_DEMANDE);
			assertThat(accuse.artiste()).isEqualTo(VerdictChamp.JUSTE);
			assertThat(accuse.pointsGagnes()).isEqualTo(30);
		});
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		assertThat(courant().bloques()).containsExactly(LEA);
		assertThat(blindTest.points()).containsExactly(
				new AttributionDePoints(LEA, 70, "Titre trouvé", true, 4000),
				new AttributionDePoints(SAM, 30, "Artiste trouvé", true, 6000));
	}

	@Test
	void unChampDemandeVideCompteFauxEtBloque() {
		demarrer();
		buzz(LEA);

		ResultatDeCommande resultat = valider(LEA, "  ", "Beyoncé");

		AccuseProposition accuse = (AccuseProposition) ((Accepte) resultat).accuse();
		assertThat(accuse.titre()).isEqualTo(VerdictChamp.FAUX);
		assertThat(accuse.artiste()).isEqualTo(VerdictChamp.JUSTE);
		assertThat(accuse.pointsGagnes()).isEqualTo(30);
		assertThat(courant().bloques()).containsExactly(LEA);
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.EXTRAIT);
	}

	@Test
	void unTitreEtUnArtisteDUnMemeBuzzDonnentUneSeuleAttribution() {
		demarrer();
		horloge.avancer(Duration.ofMillis(7250));
		buzz(SAM);

		valider(SAM, "HALO", "beyonce");

		assertThat(blindTest.points()).containsExactly(
				new AttributionDePoints(SAM, 100, "Titre et artiste trouvés", true, 7250));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
	}

	@Test
	void unRetardataireBuzzeAussitot() {
		demarrer();

		assertThat(buzz(RETARDATAIRE)).isInstanceOf(Accepte.class);
		assertThat(courant().main().joueurId()).isEqualTo(RETARDATAIRE);
	}

	@Test
	void refuseLaSaisieDUnJoueurSansLaMainOuDejaJuge() {
		demarrer();
		buzz(LEA);

		refusAttendu(deposer(SAM, "Halo", ""), "ETAT_INCOMPATIBLE");
		refusAttendu(valider(SAM, "Halo", ""), "ETAT_INCOMPATIBLE");
		valider(LEA, "Hello", "Adele");
		Object avant = blindTest.instantane();

		refusAttendu(valider(LEA, "Halo", "Beyoncé"), "HORS_DELAI");
		refusAttendu(deposer(LEA, "Halo", "Beyoncé"), "HORS_DELAI");
		assertThat(blindTest.instantane()).isEqualTo(avant);
	}

	@Test
	void apresUnJugementALEcheanceQuiReveleLeMorceauLaSaisieEstHorsDelai() {
		demarrer();
		buzz(LEA);
		valider(LEA, "", "Beyoncé");
		buzz(SAM);
		deposer(SAM, "halo", "");
		horloge.avancer(Duration.ofSeconds(20));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		Object revele = blindTest.instantane();

		refusAttendu(valider(SAM, "halo", ""), "HORS_DELAI");
		refusAttendu(deposer(SAM, "halo", ""), "HORS_DELAI");
		refusAttendu(valider(LEA, "Halo", ""), "HORS_DELAI");
		refusAttendu(valider(NOE, "Halo", ""), "ETAT_INCOMPATIBLE");
		refusAttendu(buzz(NOE), "BUZZ_NON_AUTORISE");
		assertThat(blindTest.instantane()).isEqualTo(revele);
	}

	@Test
	void uneSaisieApresLEcheanceEstRefuseeAvantQueLeRappelNeLaJuge() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(4));
		buzz(LEA);
		deposer(LEA, "Halo", "Beyoncé");
		InstantaneBlindTest avant = blindTest.instantane();
		UUID etapeId = avant.courant().etapeId();
		HorlogeFactice rappelEnAttente = new HorlogeFactice(horloge.maintenant().plusSeconds(25));
		DeroulementBlindTest restaure = fabrique().restaurer(
				new ContexteDeManche(SOIREE, MANCHE, reglages(), rappelEnAttente, () -> finsSignalees++),
				REGLAGES, avant.etat(), avant.etatManche());

		refusAttendu(restaure.recevoir(new DeposerBrouillon(LEA, rappelEnAttente.maintenant(), etapeId, 99,
				new BrouillonProposition("Hello", ""))), "HORS_DELAI");
		refusAttendu(restaure.recevoir(new ValiderProposition(LEA, rappelEnAttente.maintenant(), UUID.randomUUID(),
				etapeId, new BrouillonProposition("Hello", ""))), "HORS_DELAI");
		assertThat(restaure.instantane()).isEqualTo(avant);

		rappelEnAttente.avancer(Duration.ZERO);
		assertThat(restaure.instantane().courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		assertThat(restaure.points()).containsExactly(
				new AttributionDePoints(LEA, 100, "Titre et artiste trouvés", true, 4000));
	}

	@Test
	void unBuzzRecuA30sDExtraitEstRefuseAvantQueLeRappelNeRevele() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(10));
		InstantaneBlindTest avant = blindTest.instantane();
		HorlogeFactice rappelEnAttente = new HorlogeFactice(horloge.maintenant().plusMillis(20_001));
		DeroulementBlindTest restaure = fabrique().restaurer(
				new ContexteDeManche(SOIREE, MANCHE, reglages(), rappelEnAttente, () -> finsSignalees++),
				REGLAGES, avant.etat(), avant.etatManche());

		refusAttendu(restaure.recevoir(new BuzzerMorceau(SAM, "Sam", rappelEnAttente.maintenant(), UUID.randomUUID(),
				avant.courant().etapeId())), "BUZZ_NON_AUTORISE");
		assertThat(restaure.instantane()).isEqualTo(avant);

		rappelEnAttente.avancer(Duration.ZERO);
		assertThat(restaure.instantane().courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
	}

	// --- Fin d'un Morceau ---

	@Test
	void leMorceauSeTermineAuBoutDe30sDExtraitHorsPauses() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(10));
		buzz(LEA);
		horloge.avancer(Duration.ofSeconds(2));
		valider(LEA, "Single Ladies", "Shakira");

		horloge.avancer(Duration.ofMillis(19_999));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.EXTRAIT);
		horloge.avancer(Duration.ofMillis(1));

		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		assertThat(courant().lecture()).isNull();
		assertThat(courant().lienExtrait()).isNull();
		assertThat(fabrique().typeEvenementMinuteur(blindTest)).isEqualTo("morceau-revele");
	}

	@Test
	void leMorceauSeTermineQuandTousLesJoueursSontBloques() {
		demarrer();
		buzz(LEA);
		valider(LEA, "Crazy in Love", "Beyoncé");
		buzz(SAM);
		valider(SAM, "", "");
		buzz(NOE);

		ResultatDeCommande dernier = valider(NOE, "Hello", "Rihanna");

		assertThat(((Accepte) dernier).typeEvenement()).isEqualTo("morceau-revele");
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		assertThat(courant().bloques()).containsExactly(LEA, SAM, NOE);
	}

	@Test
	void unMorceauReveleRefuseLeBuzzEtUnMorceauPasseLeRendPerime() {
		demarrer();
		UUID premier = etape();
		blindTest.passerEtape(pilotage());
		Object revele = blindTest.instantane();

		refusAttendu(blindTest.recevoir(new BuzzerMorceau(LEA, "Léa", horloge.maintenant(), UUID.randomUUID(),
				premier)), "BUZZ_NON_AUTORISE");
		assertThat(blindTest.instantane()).isEqualTo(revele);

		blindTest.passerEtape(pilotage());
		ouvrir();
		Object second = blindTest.instantane();
		refusAttendu(blindTest.recevoir(new BuzzerMorceau(LEA, "Léa", horloge.maintenant(), UUID.randomUUID(),
				premier)), "COMMANDE_PERIMEE");
		refusAttendu(blindTest.recevoir(new ValiderProposition(LEA, horloge.maintenant(), UUID.randomUUID(),
				premier, BrouillonProposition.VIDE)), "COMMANDE_PERIMEE");
		assertThat(blindTest.instantane()).isEqualTo(second);
	}

	// --- Enchaînement des Morceaux ---

	@Test
	void enPilotageAutomatiqueLeMorceauSuivantEstPrepare8sApresLaRevelation() {
		demarrerAutomatique();
		UUID premier = etape();

		horloge.avancer(Duration.ofSeconds(30));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		assertThat(courant().attente()).isEqualTo(new Delai(horloge.maintenant().plusSeconds(8), null));
		horloge.avancer(Duration.ofMillis(7999));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		horloge.avancer(Duration.ofMillis(1));

		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.PREPARATION);
		assertThat(courant().numero()).isEqualTo(2);
		assertThat(etape()).isNotEqualTo(premier);
		assertThat(fabrique().typeEvenementMinuteur(blindTest)).isEqualTo("morceau-en-preparation");
		assertThat(blindTest.extraitAPreparer()).contains(new ExtraitAPreparer(etape(), GET_LUCKY.idDeezer()));
		assertThat(horloge.minuteursEnAttente()).isZero();
	}

	@Test
	void enPilotageAutomatiqueLaMancheSeTermine8sApresLeDernierMorceau() {
		demarrerAutomatique();
		for (int numero = 1; numero < SELECTION.size(); numero++) {
			horloge.avancer(Duration.ofSeconds(38));
			ouvrir();
		}

		horloge.avancer(Duration.ofSeconds(38));

		assertThat(blindTest.estTerminee()).isTrue();
		assertThat(finsSignalees).isEqualTo(1);
		assertThat(fabrique().typeEvenementMinuteur(blindTest)).isEqualTo("manche-terminee");
	}

	@Test
	void enPilotageManuelUnPremierPasserEtapeReveleEtLeSecondPasseAuSuivant() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(30));
		horloge.avancer(Duration.ofMinutes(5));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		assertThat(courant().attente()).isNull();

		ResultatDeCommande suivant = blindTest.passerEtape(pilotage());

		assertThat(((Accepte) suivant).typeEvenement()).isEqualTo("morceau-en-preparation");
		assertThat(courant().numero()).isEqualTo(2);
		ouvrir();
		buzz(LEA);
		ResultatDeCommande revelation = blindTest.passerEtape(pilotage());
		assertThat(((Accepte) revelation).typeEvenement()).isEqualTo("morceau-revele");
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		assertThat(courant().main()).isNull();
		assertThat(horloge.minuteursEnAttente()).isZero();
	}

	@Test
	void lePilotageNommeToujoursLeMorceauAffiche() {
		assertThat(blindTest.viseEtape(null)).isTrue();
		demarrer();
		UUID premier = etape();

		assertThat(blindTest.viseEtape(null)).isFalse();
		assertThat(blindTest.viseEtape(premier)).isTrue();
		blindTest.passerEtape(pilotage());
		assertThat(blindTest.viseEtape(premier)).isTrue();
		blindTest.passerEtape(pilotage());
		assertThat(blindTest.viseEtape(premier)).isFalse();
		assertThat(blindTest.viseEtape(etape())).isTrue();
	}

	@Test
	void leDernierBuzzSurvitAuMorceauSuivantEtALaRestauration() {
		demarrer();
		AccuseBuzz buzzDeLea = (AccuseBuzz) ((Accepte) buzz(LEA)).accuse();
		blindTest.passerEtape(pilotage());
		blindTest.passerEtape(pilotage());
		InstantaneBlindTest avant = blindTest.instantane();

		DeroulementBlindTest restaure = fabrique().restaurer(
				new ContexteDeManche(SOIREE, MANCHE, reglages(), new HorlogeFactice(horloge.maintenant()), () -> { }),
				REGLAGES, avant.etat(), avant.etatManche());

		assertThat(avant.dernierBuzz()).isEqualTo(buzzDeLea);
		assertThat(restaure.instantane().dernierBuzz()).isEqualTo(buzzDeLea);
	}

	@Test
	void enPilotageManuelPasserLaDerniereRevelationTermineLaManche() {
		demarrer();
		for (int numero = 1; numero < SELECTION.size(); numero++) {
			blindTest.passerEtape(pilotage());
			blindTest.passerEtape(pilotage());
			ouvrir();
		}
		blindTest.passerEtape(pilotage());

		ResultatDeCommande fin = blindTest.passerEtape(pilotage());

		assertThat(((Accepte) fin).typeEvenement()).isEqualTo("manche-terminee");
		assertThat(blindTest.estTerminee()).isTrue();
	}

	@Test
	void unMorceauSansExtraitEstSauteSansPoints() {
		blindTest.demarrer();

		ResultatDeCommande saut = blindTest.sauterMorceau(etape());

		assertThat(((Accepte) saut).typeEvenement()).isEqualTo("morceau-saute");
		assertThat(courant().numero()).isEqualTo(2);
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.PREPARATION);
		assertThat(blindTest.points()).isEmpty();
	}

	@Test
	void unDernierMorceauSansExtraitTermineLaManche() {
		blindTest.demarrer();
		blindTest.sauterMorceau(etape());
		blindTest.sauterMorceau(etape());

		ResultatDeCommande fin = blindTest.sauterMorceau(etape());

		assertThat(((Accepte) fin).typeEvenement()).isEqualTo("manche-terminee");
		assertThat(blindTest.estTerminee()).isTrue();
		assertThat(finsSignalees).isEqualTo(1);
		assertThat(blindTest.extraitAPreparer()).isEmpty();
	}

	@Test
	void unLienPourUnMorceauQuiNEstPlusAttenduEstAbandonne() {
		blindTest.demarrer();
		UUID premier = etape();
		ouvrir();
		Object avant = blindTest.instantane();

		refusAttendu(blindTest.ouvrirMorceau(premier, LIEN), "COMMANDE_PERIMEE");
		refusAttendu(blindTest.sauterMorceau(premier), "COMMANDE_PERIMEE");
		refusAttendu(blindTest.ouvrirMorceau(UUID.randomUUID(), LIEN), "COMMANDE_PERIMEE");
		assertThat(blindTest.instantane()).isEqualTo(avant);
		assertThat(blindTest.extraitAPreparer()).isEmpty();
	}

	// --- Pause et restauration ---

	@Test
	void laPauseGeleLExtraitEtLeTempsDeSaisieRestant() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(1));
		buzz(LEA);
		horloge.avancer(Duration.ofSeconds(5));

		blindTest.mettreEnPause(pilotage());
		Object enPause = blindTest.instantane();
		horloge.avancer(Duration.ofHours(1));

		assertThat(blindTest.instantane()).isEqualTo(enPause);
		assertThat(courant().main().saisie()).isEqualTo(new Delai(null, 15_000L));
		refusAttendu(buzz(SAM), "BUZZ_NON_AUTORISE");
		refusAttendu(valider(LEA, "Halo", "Beyoncé"), "ETAT_INCOMPATIBLE");
		assertThat(deposer(LEA, "Halo", "Beyoncé")).isInstanceOf(Accepte.class);

		blindTest.reprendre(pilotage());
		assertThat(courant().main().saisie()).isEqualTo(new Delai(horloge.maintenant().plusSeconds(15), null));
		assertThat(courant().lecture().enLecture()).isFalse();
		horloge.avancer(Duration.ofSeconds(15));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
		assertThat(blindTest.points()).containsExactly(
				new AttributionDePoints(LEA, 100, "Titre et artiste trouvés", true, 1000));
	}

	@Test
	void laPauseGeleLExtraitEnLecture() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(12));
		blindTest.mettreEnPause(pilotage());
		assertThat(courant().lecture()).isEqualTo(new LectureExtrait(12_000, null));

		horloge.avancer(Duration.ofMinutes(3));
		blindTest.reprendre(pilotage());
		horloge.avancer(Duration.ofMillis(17_999));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.EXTRAIT);
		horloge.avancer(Duration.ofMillis(1));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
	}

	@Test
	void unMorceauOuvertPendantLaPauseAttendLaReprise() {
		blindTest.demarrer();
		blindTest.mettreEnPause(pilotage());

		ouvrir();

		assertThat(courant().lecture()).isEqualTo(new LectureExtrait(0, null));
		assertThat(horloge.minuteursEnAttente()).isZero();
		blindTest.reprendre(pilotage());
		assertThat(courant().lecture()).isEqualTo(new LectureExtrait(0, horloge.maintenant()));
		horloge.avancer(Duration.ofSeconds(30));
		assertThat(courant().phase()).isEqualTo(PhaseBlindTest.REVELATION);
	}

	@Test
	void laRestaurationEnPleineSaisieReprogrammeLeTempsRestant() {
		demarrer();
		horloge.avancer(Duration.ofSeconds(5));
		buzz(LEA);
		deposer(LEA, "Halo", "");
		horloge.avancer(Duration.ofSeconds(7));
		InstantaneBlindTest avant = blindTest.instantane();
		HorlogeFactice apresRedemarrage = new HorlogeFactice(horloge.maintenant());

		DeroulementBlindTest restaure = fabrique().restaurer(
				new ContexteDeManche(SOIREE, MANCHE, reglages(), apresRedemarrage, () -> finsSignalees++),
				REGLAGES, avant.etat(), avant.etatManche());

		assertThat(restaure.instantane()).isEqualTo(avant);
		assertThat(apresRedemarrage.minuteursEnAttente()).isEqualTo(1);
		apresRedemarrage.avancer(Duration.ofMillis(12_999));
		assertThat(restaure.instantane().courant().phase()).isEqualTo(PhaseBlindTest.PROPOSITION);
		apresRedemarrage.avancer(Duration.ofMillis(1));
		assertThat(restaure.instantane().courant().phase()).isEqualTo(PhaseBlindTest.EXTRAIT);
		assertThat(restaure.points()).containsExactly(new AttributionDePoints(LEA, 70, "Titre trouvé", true, 5000));
	}

	@Test
	void laRestaurationEnPauseNeProgrammeRien() {
		demarrer();
		buzz(LEA);
		blindTest.mettreEnPause(pilotage());
		InstantaneBlindTest avant = blindTest.instantane();
		HorlogeFactice apresRedemarrage = new HorlogeFactice(horloge.maintenant().plusSeconds(600));

		DeroulementBlindTest restaure = fabrique().restaurer(
				new ContexteDeManche(SOIREE, MANCHE, reglages(), apresRedemarrage, () -> finsSignalees++),
				REGLAGES, avant.etat(), EtatManche.EN_PAUSE);

		assertThat(restaure.instantane()).isEqualTo(avant);
		assertThat(apresRedemarrage.minuteursEnAttente()).isZero();
	}

	// --- Fabrique ---

	@Test
	void laFabriqueDeclareLeModeEtSesReglagesCommuns() {
		FabriqueBlindTest fabrique = fabrique();

		assertThat(fabrique.mode()).isEqualTo(ModeDeJeu.BLIND_TEST);
		assertThat(fabrique.typeReglages()).isEqualTo(ReglagesBlindTest.class);
		assertThat(fabrique.reglagesCommuns(REGLAGES)).isEqualTo(new ReglagesDeManche(3, Duration.ofSeconds(30)));
	}

	@Test
	void laFabriqueRefuseUneSelectionQuiNeCorrespondPasAuxReglages() {
		FabriqueBlindTest fabrique = new FabriqueBlindTest(mancheId -> List.of(HALO), soireeId -> Set.of());
		ContexteDeManche contexte = new ContexteDeManche(SOIREE, MANCHE, reglages(), horloge, () -> { });

		assertThatThrownBy(() -> fabrique.creer(contexte, REGLAGES)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> fabrique.restaurer(contexte, REGLAGES,
				new EtatBlindTestPersiste(SELECTION, null, List.of(), null), EtatManche.PRETE))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void lInstantaneGardeLaSelectionHorsDesReglages() {
		demarrer();

		assertThat(blindTest.instantane().etat().selection()).isEqualTo(SELECTION);
		assertThat(REGLAGES.toString()).doesNotContain("Halo", String.valueOf(HALO.idDeezer()));
	}

	// --- Outils ---

	private void demarrer() {
		blindTest.demarrer();
		ouvrir();
	}

	private void demarrerAutomatique() {
		blindTest = fabrique().creer(new ContexteDeManche(SOIREE, MANCHE, reglages(), horloge,
				() -> finsSignalees++, true), REGLAGES);
		demarrer();
	}

	private void ouvrir() {
		assertThat(blindTest.ouvrirMorceau(etape(), LIEN)).isInstanceOf(Accepte.class);
	}

	private MorceauEnCours courant() {
		return blindTest.instantane().courant();
	}

	private UUID etape() {
		return courant().etapeId();
	}

	private ResultatDeCommande buzz(UUID joueurId) {
		return blindTest.recevoir(new BuzzerMorceau(joueurId, pseudo(joueurId), horloge.maintenant(),
				UUID.randomUUID(), etape()));
	}

	private ResultatDeCommande deposer(UUID joueurId, String titre, String artiste) {
		return deposer(joueurId, ++numeroBrouillon, titre, artiste);
	}

	private ResultatDeCommande deposer(UUID joueurId, long numero, String titre, String artiste) {
		return blindTest.recevoir(new DeposerBrouillon(joueurId, horloge.maintenant(), etape(), numero,
				new BrouillonProposition(titre, artiste)));
	}

	private ResultatDeCommande valider(UUID joueurId, String titre, String artiste) {
		return blindTest.recevoir(new ValiderProposition(joueurId, horloge.maintenant(), UUID.randomUUID(), etape(),
				new BrouillonProposition(titre, artiste)));
	}

	private static String pseudo(UUID joueurId) {
		if (LEA.equals(joueurId)) {
			return "Léa";
		}
		return SAM.equals(joueurId) ? "Sam" : "Noé";
	}

	private static void refusAttendu(ResultatDeCommande resultat, String code) {
		assertThat(resultat).isInstanceOfSatisfying(Refuse.class, refus -> {
			assertThat(refus.statutHttp()).isEqualTo(409);
			assertThat(refus.code()).isEqualTo(code);
		});
	}

	/** Pour le contrat commun : le service fournit le lien du premier Morceau juste après le démarrage. */
	private static final class LienFourniAuDemarrage implements DeroulementDeManche {

		private final DeroulementBlindTest cible;

		LienFourniAuDemarrage(DeroulementBlindTest cible) {
			this.cible = cible;
		}

		@Override
		public void demarrer() {
			cible.demarrer();
			cible.ouvrirMorceau(cible.extraitAPreparer().orElseThrow().etapeId(), LIEN);
		}

		@Override
		public ResultatDeCommande recevoir(ActionJoueur action) {
			return cible.recevoir(action);
		}

		@Override
		public ResultatDeCommande commander(CommandeDeManche commande) {
			return cible.commander(commande);
		}

		@Override
		public ResultatDeCommande mettreEnPause(CommandePilotage commande) {
			return cible.mettreEnPause(commande);
		}

		@Override
		public ResultatDeCommande reprendre(CommandePilotage commande) {
			return cible.reprendre(commande);
		}

		@Override
		public ResultatDeCommande passerEtape(CommandePilotage commande) {
			return cible.passerEtape(commande);
		}

		@Override
		public ResultatDeCommande terminer(CommandePilotage commande) {
			return cible.terminer(commande);
		}

		@Override
		public boolean viseEtape(UUID etapeId) {
			return cible.viseEtape(etapeId);
		}

		@Override
		public Object instantane() {
			return cible.instantane();
		}

		@Override
		public boolean estTerminee() {
			return cible.estTerminee();
		}

		@Override
		public List<AttributionDePoints> points() {
			return cible.points();
		}

	}

}
