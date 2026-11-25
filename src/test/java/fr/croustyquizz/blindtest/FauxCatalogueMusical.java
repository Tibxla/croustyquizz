package fr.croustyquizz.blindtest;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Catalogue en mémoire pour les tests : playlists, genres et Morceaux sans extrait se déclarent à la main. */
final class FauxCatalogueMusical implements CatalogueMusical {

	private final Map<Long, List<Morceau>> playlists = new HashMap<>();
	private final Map<Long, List<Morceau>> genres = new HashMap<>();
	private final Set<Long> sansExtrait = new HashSet<>();
	private boolean enPanne;

	FauxCatalogueMusical avecPlaylist(long id, Morceau... morceaux) {
		playlists.put(id, List.of(morceaux));
		return this;
	}

	FauxCatalogueMusical avecGenre(long id, Morceau... morceaux) {
		genres.put(id, List.of(morceaux));
		return this;
	}

	FauxCatalogueMusical sansExtrait(long idDeezer) {
		sansExtrait.add(idDeezer);
		return this;
	}

	FauxCatalogueMusical enPanne() {
		enPanne = true;
		return this;
	}

	@Override
	public List<Morceau> morceauxDePlaylist(long playlistId) {
		verifier();
		return playlists.getOrDefault(playlistId, List.of());
	}

	@Override
	public List<Morceau> morceauxDuGenre(long genreId) {
		verifier();
		return genres.getOrDefault(genreId, List.of());
	}

	@Override
	public Optional<URI> lienExtrait(long idDeezer) {
		verifier();
		return sansExtrait.contains(idDeezer) ? Optional.empty()
				: Optional.of(URI.create("https://cdnt-preview.dzcdn.net/extrait-" + idDeezer + ".mp3?hdnea=signe"));
	}

	private void verifier() {
		if (enPanne) {
			throw new SourceMusicaleIndisponible("Deezer ne répond pas.");
		}
	}

}
