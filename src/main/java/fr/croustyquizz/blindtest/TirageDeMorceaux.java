package fr.croustyquizz.blindtest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Tire au hasard les Morceaux d'une Manche dans sa source, sans doublon (ADR 0009, point 1). Deux versions d'un même
 * titre du même artiste comptent comme un doublon. Appelle le catalogue : hors du verrou de la Soirée.
 */
public final class TirageDeMorceaux {

	private final CatalogueMusical catalogue;
	private final RandomGenerator hasard;

	public TirageDeMorceaux(CatalogueMusical catalogue, RandomGenerator hasard) {
		this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
		this.hasard = Objects.requireNonNull(hasard, "hasard");
	}

	/** Rend {@code nombreMorceaux} Morceaux distincts, ou lève {@link SourceMusicaleIndisponible} s'il en manque. */
	public List<Morceau> tirer(ReglagesBlindTest reglages) {
		SourceDeMorceaux source = reglages.source();
		List<Morceau> candidats = switch (source.type()) {
			case PLAYLIST -> catalogue.morceauxDePlaylist(source.idDeezer());
			case GENRE -> catalogue.morceauxDuGenre(source.idDeezer());
		};
		Map<String, Morceau> distincts = new LinkedHashMap<>();
		Set<Long> identifiantsVus = new HashSet<>();
		for (Morceau morceau : candidats) {
			if (identifiantsVus.add(morceau.idDeezer())) {
				distincts.putIfAbsent(cle(morceau), morceau);
			}
		}
		if (distincts.size() < reglages.nombreMorceaux()) {
			throw new SourceMusicaleIndisponible("Cette source n'a pas assez de Morceaux jouables.");
		}
		List<Morceau> melange = new ArrayList<>(distincts.values());
		Collections.shuffle(melange, hasard);
		return List.copyOf(melange.subList(0, reglages.nombreMorceaux()));
	}

	private static String cle(Morceau morceau) {
		return CorrecteurExact.normaliser(morceau.titre()) + "\n" + CorrecteurExact.normaliser(morceau.artiste());
	}

}
