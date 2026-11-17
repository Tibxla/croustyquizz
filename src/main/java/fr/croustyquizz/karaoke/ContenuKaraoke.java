package fr.croustyquizz.karaoke;

import java.util.List;

/** Contenu figé pour un Passage, indépendant de la source externe. */
public record ContenuKaraoke(Chanson chanson, List<LigneParoles> lignes) {
    public ContenuKaraoke { lignes = List.copyOf(lignes); }
}
