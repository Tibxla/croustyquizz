package fr.croustyquizz.karaoke;

import java.util.List;
import java.util.Optional;

/** Catalogue et contenus de Chansons ; une panne se distingue d'un résultat vide. */
public interface SourceDeParoles {
    List<Chanson> rechercher(String recherche);
    Optional<ContenuKaraoke> charger(long idLrclib);
}
