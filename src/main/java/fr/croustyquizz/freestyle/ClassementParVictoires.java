package fr.croustyquizz.freestyle;

import fr.croustyquizz.passage.ResultatVote;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Palmarès du Freestyle des Passages à plusieurs (#74). Chaque Passage clos donne une victoire à chacun des
 * Interprètes qui ont reçu le plus de voix, égalité comprise ; un Passage sans voix n’en donne aucune. Ordre :
 * victoires, puis nombre de Passages, décroissants, puis pseudo. Rang nul sans victoire.
 */
final class ClassementParVictoires {
    private ClassementParVictoires() { }

    record Ligne(Long rang, VuesFreestyle.Interprete interprete, long nombreVictoires, long nombrePassages) { }

    static List<Ligne> classer(List<ResultatVote> resultats, Map<UUID, String> pseudos) {
        Map<UUID, long[]> comptes = new LinkedHashMap<>();
        for (var resultat : resultats.stream().filter(r -> r.interpretes().size() > 1).toList()) {
            long meilleur = Collections.max(resultat.interpretes().stream()
                    .map(id -> resultat.voix().getOrDefault(id, 0L)).toList());
            for (UUID id : resultat.interpretes()) {
                long[] compte = comptes.computeIfAbsent(id, cle -> new long[2]);
                compte[1]++;
                if (meilleur > 0 && resultat.voix().getOrDefault(id, 0L) == meilleur) { compte[0]++; }
            }
        }
        var lignes = comptes.entrySet().stream()
                .map(e -> new Ligne(null, VuesFreestyle.interprete(e.getKey(), pseudos), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(Ligne::nombreVictoires).reversed()
                        .thenComparing(Comparator.comparingLong(Ligne::nombrePassages).reversed())
                        .thenComparing(l -> l.interprete().pseudo())
                        .thenComparing(l -> l.interprete().joueurId()))
                .toList();
        List<Ligne> classement = new ArrayList<>(lignes.size());
        for (var ligne : lignes) {
            classement.add(ligne.nombreVictoires() == 0 ? ligne
                    : new Ligne(classement.size() + 1L, ligne.interprete(), ligne.nombreVictoires(), ligne.nombrePassages()));
        }
        return List.copyOf(classement);
    }
}
