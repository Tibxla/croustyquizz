package fr.croustyquizz.blindtest;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Port vers le catalogue musical, implémenté avec Deezer (#176). Chaque appel est externe : le service l'appelle hors
 * du verrou de la Soirée (ADR 0006). Une panne lève {@link SourceMusicaleIndisponible} ; une playlist ou un genre que
 * le catalogue ne connaît pas lève {@link SourceMusicaleInvalide}.
 */
public interface CatalogueMusical {

	/** Morceaux d'une playlist qui ont un extrait. */
	List<Morceau> morceauxDePlaylist(long playlistId);

	/** Morceaux du classement d'un genre qui ont un extrait. */
	List<Morceau> morceauxDuGenre(long genreId);

	/** Lien signé de l'extrait de 30 s, redemandé au moment de jouer ; vide si le Morceau n'a plus d'extrait ou n'existe plus. */
	Optional<URI> lienExtrait(long idDeezer);

}
