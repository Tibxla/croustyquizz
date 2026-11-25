package fr.croustyquizz.blindtest;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * État immuable du Morceau courant. Chaque transition rend une nouvelle valeur.
 *
 * @param numero          rang du Morceau dans la Manche, à partir de 1
 * @param etapeId         identifiant attribué par le serveur, jamais l'identifiant Deezer
 * @param lienExtrait     lien signé, en phases EXTRAIT et PROPOSITION seulement
 * @param lecture         état de lecture, en phases EXTRAIT et PROPOSITION seulement
 * @param main            Joueur qui a la main, en phase PROPOSITION seulement
 * @param buzzAcceptes    nombre de Buzz qui ont donné la main sur ce Morceau
 * @param bloques         Joueurs bloqués jusqu'au Morceau suivant
 * @param ontPropose      Joueurs dont une Proposition de titre a déjà été jugée sur ce Morceau
 * @param partiesTrouvees parties trouvées, dans l'ordre
 * @param attente         délai avant le Morceau suivant en Pilotage Automatique, pendant la Révélation
 */
public record MorceauEnCours(int numero, UUID etapeId, PhaseBlindTest phase, URI lienExtrait, LectureExtrait lecture,
		MainBlindTest main, int buzzAcceptes, List<UUID> bloques, List<UUID> ontPropose,
		List<PartieTrouvee> partiesTrouvees, Delai attente) {

	public MorceauEnCours {
		Objects.requireNonNull(etapeId, "etapeId");
		Objects.requireNonNull(phase, "phase");
		bloques = List.copyOf(bloques);
		ontPropose = List.copyOf(ontPropose);
		partiesTrouvees = List.copyOf(partiesTrouvees);
	}

	static MorceauEnCours enPreparation(int numero, UUID etapeId) {
		return new MorceauEnCours(numero, etapeId, PhaseBlindTest.PREPARATION, null, null, null, 0,
				List.of(), List.of(), List.of(), null);
	}

	/** Parties que le prochain Joueur doit encore trouver. */
	public Set<PartieDuMorceau> partiesDemandees() {
		Set<PartieDuMorceau> demandees = EnumSet.allOf(PartieDuMorceau.class);
		partiesTrouvees.forEach(trouvee -> demandees.remove(trouvee.partie()));
		return demandees;
	}

	MorceauEnCours ouvert(URI lien, LectureExtrait premiereLecture) {
		return new MorceauEnCours(numero, etapeId, PhaseBlindTest.EXTRAIT, Objects.requireNonNull(lien),
				premiereLecture, null, buzzAcceptes, bloques, ontPropose, partiesTrouvees, null);
	}

	MorceauEnCours mainDonnee(MainBlindTest nouvelleMain, LectureExtrait lectureEnPause) {
		return new MorceauEnCours(numero, etapeId, PhaseBlindTest.PROPOSITION, lienExtrait, lectureEnPause,
				nouvelleMain, buzzAcceptes + 1, bloques, ontPropose, partiesTrouvees, null);
	}

	MorceauEnCours avecMain(MainBlindTest nouvelleMain) {
		return new MorceauEnCours(numero, etapeId, phase, lienExtrait, lecture, nouvelleMain, buzzAcceptes, bloques,
				ontPropose, partiesTrouvees, attente);
	}

	/** Le Joueur qui avait la main a été jugé ; l'appelant enchaîne aussitôt sur la reprise ou la Révélation. */
	MorceauEnCours juge(UUID joueurId, List<PartieTrouvee> trouvees, boolean bloque) {
		List<UUID> nouveauxBloques = new ArrayList<>(bloques);
		if (bloque) {
			nouveauxBloques.add(joueurId);
		}
		List<UUID> nouveauxProposants = new ArrayList<>(ontPropose);
		nouveauxProposants.add(joueurId);
		List<PartieTrouvee> toutesTrouvees = new ArrayList<>(partiesTrouvees);
		toutesTrouvees.addAll(trouvees);
		return new MorceauEnCours(numero, etapeId, phase, lienExtrait, lecture, null, buzzAcceptes, nouveauxBloques,
				nouveauxProposants, toutesTrouvees, attente);
	}

	MorceauEnCours repris(LectureExtrait reprise) {
		return new MorceauEnCours(numero, etapeId, PhaseBlindTest.EXTRAIT, lienExtrait, reprise, null, buzzAcceptes,
				bloques, ontPropose, partiesTrouvees, null);
	}

	MorceauEnCours revele(Delai avantSuivant) {
		return new MorceauEnCours(numero, etapeId, PhaseBlindTest.REVELATION, null, null, null, buzzAcceptes, bloques,
				ontPropose, partiesTrouvees, avantSuivant);
	}

	/** Pause de la Manche : l'extrait, la saisie et l'attente du Morceau suivant s'arrêtent. */
	MorceauEnCours geleA(Instant maintenant) {
		return new MorceauEnCours(numero, etapeId, phase, lienExtrait,
				lecture == null ? null : lecture.geleeA(maintenant),
				main == null ? null : main.avecSaisie(main.saisie().geleA(maintenant)),
				buzzAcceptes, bloques, ontPropose, partiesTrouvees,
				attente == null ? null : attente.geleA(maintenant));
	}

	/** Reprise de la Manche : pendant une saisie, l'extrait reste en pause jusqu'au jugement. */
	MorceauEnCours relanceA(Instant maintenant) {
		LectureExtrait relancee = phase == PhaseBlindTest.EXTRAIT ? lecture.lanceeA(maintenant) : lecture;
		return new MorceauEnCours(numero, etapeId, phase, lienExtrait, relancee,
				main == null ? null : main.avecSaisie(main.saisie().relanceA(maintenant)),
				buzzAcceptes, bloques, ontPropose, partiesTrouvees,
				attente == null ? null : attente.relanceA(maintenant));
	}

}
