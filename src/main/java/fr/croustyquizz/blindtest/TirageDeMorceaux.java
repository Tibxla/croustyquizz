package fr.croustyquizz.blindtest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Tire au hasard les Morceaux d'une Manche dans sa source, sans doublon (ADR 0009, point 1). Deux versions d'un même
 * titre du même artiste comptent comme un doublon. Appelle le catalogue : hors du verrou de la Soirée.
 */
public final class TirageDeMorceaux {

	private static final String CORRECTION = "demandes-en moins ou choisis une autre source.";

	private final CatalogueMusical catalogue;
	private final RandomGenerator hasard;

	public TirageDeMorceaux(CatalogueMusical catalogue, RandomGenerator hasard) {
		this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
		this.hasard = Objects.requireNonNull(hasard, "hasard");
	}

	/** Rend {@code nombreMorceaux} Morceaux distincts, ou lève {@link SourceMusicaleInvalide} s'il en manque. */
	public List<Morceau> tirer(ReglagesBlindTest reglages) {
		SourceDeMorceaux source = reglages.source();
		List<Morceau> candidats = switch (source.type()) {
			case PLAYLIST -> catalogue.morceauxDePlaylist(source.idDeezer());
			case GENRE -> catalogue.morceauxDuGenre(source.idDeezer());
		};
		List<Morceau> distincts = new ArrayList<>();
		Set<String> formesRetenues = new HashSet<>();
		Set<Long> identifiantsVus = new HashSet<>();
		for (Morceau morceau : candidats) {
			Set<String> formes = formes(morceau);
			if (identifiantsVus.add(morceau.idDeezer()) && Collections.disjoint(formes, formesRetenues)) {
				distincts.add(morceau);
				formesRetenues.addAll(formes);
			}
		}
		if (distincts.size() < reglages.nombreMorceaux()) {
			throw new SourceMusicaleInvalide(manque(source.type(), distincts.size(), reglages.nombreMorceaux()));
		}
		List<Morceau> melange = new ArrayList<>(distincts);
		Collections.shuffle(melange, hasard);
		return List.copyOf(melange.subList(0, reglages.nombreMorceaux()));
	}

	/** Ce qui manque, et quoi corriger : le message est montré tel quel à l'Animateur. */
	private static String manque(SourceDeMorceaux.Type type, int jouables, int demandes) {
		String source = type == SourceDeMorceaux.Type.GENRE ? "Ce genre" : "Cette playlist";
		return switch (jouables) {
			case 0 -> source + " n’a aucun Morceau jouable : choisis une autre source.";
			case 1 -> source + " n’a qu’un Morceau jouable pour " + demandes + " demandés : " + CORRECTION;
			default -> source + " n’a que " + jouables + " Morceaux jouables pour " + demandes + " demandés : "
					+ CORRECTION;
		};
	}

	/**
	 * Couples titre et artiste que la correction accepte sans faute de frappe : deux Morceaux qui en partagent un ne
	 * se distinguent pas, comme « The Cure » et « Cure » du même artiste, et forment un doublon.
	 */
	private static Set<String> formes(Morceau morceau) {
		Set<String> formes = new HashSet<>();
		for (String titre : CorrecteurTolerant.formesExactes(morceau.titre())) {
			for (String artiste : CorrecteurTolerant.formesExactes(morceau.artiste())) {
				formes.add(titre + "\n" + artiste);
			}
		}
		return formes;
	}

}
