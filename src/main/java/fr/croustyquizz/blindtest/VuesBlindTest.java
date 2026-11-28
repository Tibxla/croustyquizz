package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Projections du bloc {@code blindtest} par destination (contrat : BlocBlindTestSalle, BlocBlindTestConsole,
 * BlocBlindTestManette). La salle ne reçoit que le lien d'extrait signé : ni identifiant Deezer, ni titre, ni artiste
 * avant leur Révélation, hors partie déjà trouvée.
 */
// « BlindTest » nomme le Mode de jeu : PMD prend à tort la classe pour une classe de test.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
final class VuesBlindTest {

	private static final String JOUEUR_ID = "joueurId";
	private static final String PSEUDO = "pseudo";

	private VuesBlindTest() {
	}

	/** Bloc d'une destination, ou {@code null} avant le premier Morceau et après la fin. */
	static Object projeter(UUID mancheId, InstantaneBlindTest instantane, Destination destination) {
		MorceauEnCours courant = instantane.courant();
		if (courant == null || instantane.etatManche() == EtatManche.TERMINEE) {
			return null;
		}
		List<Morceau> selection = instantane.etat().selection();
		Morceau morceau = selection.get(courant.numero() - 1);
		Map<String, Object> bloc = commun(mancheId, instantane.etatManche(), courant, selection.size(), morceau);
		switch (destination.type()) {
			case SALLE -> bloc.put("lienExtrait", lienPublie(courant));
			case CONSOLE -> {
				bloc.put("reponse", revelation(morceau));
				bloc.put("joueursBloques", courant.bloques());
				// Le texte saisi ne sort que vers la Console, pour l'arbitrage (ADR 0009, point 15).
				bloc.put("propositions", courant.propositions().stream().map(VuesBlindTest::proposition).toList());
			}
			case JOUEUR -> completerManette(bloc, instantane.etatManche(), courant, destination.joueurId());
			case TOUTES_LES_MANETTES -> throw new IllegalArgumentException("Chaque Manette reçoit sa propre vue.");
		}
		return bloc;
	}

	private static Map<String, Object> commun(UUID mancheId, EtatManche etat, MorceauEnCours courant, int total,
			Morceau morceau) {
		Map<String, Object> bloc = new LinkedHashMap<>();
		bloc.put("mancheId", mancheId);
		bloc.put("etat", etat.name());
		bloc.put("etapeId", courant.etapeId());
		bloc.put("numero", courant.numero());
		bloc.put("total", total);
		bloc.put("phase", courant.phase().name());
		bloc.put("lecture", lecture(courant));
		bloc.put("main", main(courant.main()));
		bloc.put("partiesTrouvees", courant.partiesTrouvees().stream().map(VuesBlindTest::partie).toList());
		bloc.put("revelation", courant.phase() == PhaseBlindTest.REVELATION ? revelation(morceau) : null);
		bloc.put("arbitrage", arbitrage(courant.arbitrage()));
		return bloc;
	}

	private static Object arbitrage(ArbitrageMorceau arbitrage) {
		if (arbitrage == null) {
			return null;
		}
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put("partie", arbitrage.partie().name());
		vue.put(JOUEUR_ID, arbitrage.joueurId());
		vue.put(PSEUDO, arbitrage.pseudo());
		vue.put("verdict", arbitrage.verdict().name());
		return vue;
	}

	private static Map<String, Object> proposition(PropositionJugee proposition) {
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put("propositionId", proposition.propositionId());
		vue.put(JOUEUR_ID, proposition.joueurId());
		vue.put(PSEUDO, proposition.pseudo());
		vue.put("titre", champ(proposition.titre()));
		vue.put("artiste", champ(proposition.artiste()));
		vue.put("points", proposition.points());
		return vue;
	}

	private static Map<String, Object> champ(ChampJuge champ) {
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put("saisie", champ.saisie());
		vue.put("verdict", champ.verdict().name());
		vue.put("arbitre", champ.arbitre());
		return vue;
	}

	private static void completerManette(Map<String, Object> bloc, EtatManche etat, MorceauEnCours courant,
			UUID joueurId) {
		boolean bloque = courant.bloques().contains(joueurId);
		MainBlindTest main = courant.main();
		boolean aLaMain = main != null && main.joueurId().equals(joueurId);
		boolean dejaJuge = courant.ontPropose().contains(joueurId);
		bloc.put("peutBuzzer", courant.phase() == PhaseBlindTest.EXTRAIT && etat == EtatManche.EN_COURS && !bloque
				&& !dejaJuge);
		bloc.put("bloque", bloque);
		bloc.put("aLaMain", aLaMain);
		// Le Joueur qui a la main voit les parties de son formulaire, celles qui seront jugées.
		var demandees = aLaMain ? main.demandeesParmi(courant.partiesDemandees()) : courant.partiesDemandees();
		bloc.put("champsDemandes", demandees.stream().sorted().map(Enum::name).toList());
		bloc.put("monBrouillon", aLaMain && main.numeroBrouillon() > 0 ? brouillon(main) : null);
	}

	/** Le lien n'est publié qu'en phases EXTRAIT et PROPOSITION, quand la salle peut jouer l'extrait. */
	private static String lienPublie(MorceauEnCours courant) {
		boolean joue = courant.phase() == PhaseBlindTest.EXTRAIT || courant.phase() == PhaseBlindTest.PROPOSITION;
		return joue && courant.lienExtrait() != null ? courant.lienExtrait().toString() : null;
	}

	private static Object lecture(MorceauEnCours courant) {
		LectureExtrait lecture = courant.lecture();
		if (lecture == null || courant.phase() == PhaseBlindTest.PREPARATION
				|| courant.phase() == PhaseBlindTest.REVELATION) {
			return null;
		}
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put("etat", lecture.enLecture() ? "EN_LECTURE" : "EN_PAUSE");
		vue.put("positionMs", lecture.positionMs());
		vue.put("depuisLe", instant(lecture.depuisLe()));
		return vue;
	}

	private static Object main(MainBlindTest main) {
		if (main == null) {
			return null;
		}
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put(JOUEUR_ID, main.joueurId());
		vue.put(PSEUDO, main.pseudo());
		vue.put("echeanceLe", instant(main.saisie().echeanceLe()));
		vue.put("tempsRestantMs", main.saisie().restantMs());
		return vue;
	}

	private static Map<String, Object> partie(PartieTrouvee partie) {
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put("partie", partie.partie().name());
		vue.put("valeur", partie.valeur());
		vue.put(JOUEUR_ID, partie.joueurId());
		vue.put(PSEUDO, partie.pseudo());
		vue.put("points", partie.points());
		return vue;
	}

	private static Map<String, Object> revelation(Morceau morceau) {
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put("titre", morceau.titre());
		vue.put("artiste", morceau.artiste());
		vue.put("pochette", morceau.pochette() == null ? null : morceau.pochette().toString());
		return vue;
	}

	private static Map<String, Object> brouillon(MainBlindTest main) {
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put("numero", main.numeroBrouillon());
		vue.put("titre", main.brouillon().titre());
		vue.put("artiste", main.brouillon().artiste());
		return vue;
	}

	/** Instant au format du contrat (« Z » final) ; {@code null} reste nul. */
	private static String instant(Instant instant) {
		return instant == null ? null : instant.toString();
	}

}
