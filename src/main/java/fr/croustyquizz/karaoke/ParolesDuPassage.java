package fr.croustyquizz.karaoke;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Version de paroles figée pour la lecture du Passage. */
public record ParolesDuPassage(UUID parolesId, long dureeMs, List<LigneParoles> lignes) {
    public ParolesDuPassage {
        Objects.requireNonNull(parolesId);
        lignes = lignes.stream().filter(ligne -> ligne.positionMs() >= 0).toList();
        if (dureeMs <= 0 || lignes.stream().noneMatch(ParolesDuPassage::contientDuTexte)) {
            throw new IllegalArgumentException("Les paroles exigent une durée positive et une ligne de texte.");
        }
    }

    long premierePosition() {
        return lignes.stream().filter(ParolesDuPassage::contientDuTexte).findFirst().orElseThrow().positionMs();
    }

    static boolean contientDuTexte(LigneParoles ligne) {
        return ligne.positionMs() >= 0 && ligne.texte() != null && !ligne.texte().isBlank();
    }
}
