package fr.croustyquizz.freestyle;

import static java.util.stream.Collectors.groupingBy;

import java.text.Normalizer;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dictionnaire qui rapproche les mots par leur Famille de rimes, quelle que soit leur source. Les listes thématiques
 * et le lexique général ne partagent pas la même échelle de fréquence : leurs suggestions ne se mélangent jamais, elles
 * se suivent. Avec un thème, ses suggestions viennent d'abord, et le lexique général ne les complète que si elles ne
 * remplissent pas une Grille de {@value #SUGGESTIONS_D_UNE_GRILLE} mesures. Sans thème, le lexique général vient
 * d'abord, puis les mots des listes qu'il ne contient pas. Une flexion du mot de départ qui se prononce comme lui
 * ({@code maisons} pour {@code maison}) n'est pas une rime : elle n'est jamais proposée.
 */
final class DictionnaireDeRimesPhonetique implements DictionnaireDeRimes {
    static final int SUGGESTIONS_D_UNE_GRILLE = 32;
    private static final Pattern DIACRITIQUES = Pattern.compile("\\p{M}+");
    private static final Comparator<Candidat> ORDRE = Comparator.comparing(Candidat::riche).reversed()
            .thenComparing(Comparator.comparingDouble(Candidat::frequence).reversed())
            .thenComparing(Candidat::mot);

    private final Map<String, List<EntreePhonetique>> parMot;
    private final Map<List<String>, List<EntreePhonetique>> parFamille;

    /**
     * Écarte avant l'indexation les mots filtrés, toutes variantes de casse et d'accentuation, ainsi que toute forme
     * que le lexique général rattache à un lemme filtré, quelle que soit la source qui la propose.
     */
    DictionnaireDeRimesPhonetique(List<EntreePhonetique> entrees, List<String> motsFiltres) {
        Set<String> filtre = motsFiltres.stream().map(DictionnaireDeRimesPhonetique::cleDeFiltre)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> exclus = new HashSet<>(filtre);
        for (EntreePhonetique entree : entrees) {
            if (entree.lemmes().stream().map(DictionnaireDeRimesPhonetique::cleDeFiltre).anyMatch(filtre::contains)) {
                exclus.add(cleDeFiltre(entree.mot()));
            }
        }
        List<EntreePhonetique> entreesAdmises = entrees.stream()
                .filter(entree -> !exclus.contains(cleDeFiltre(entree.mot())))
                .toList();
        parMot = entreesAdmises.stream().collect(groupingBy(entree -> normaliserMot(entree.mot())));
        parFamille = entreesAdmises.stream().collect(groupingBy(EntreePhonetique::famille));
    }

    @Override
    public List<String> rimesDe(String mot) {
        return rimesDe(mot, null);
    }

    @Override
    public List<String> rimesDe(String mot, ThemeDeRimes theme) {
        if (mot == null) {
            return List.of();
        }
        String motCherche = normaliserMot(mot);
        List<EntreePhonetique> departs = parMot.getOrDefault(motCherche, List.of());
        Map<String, String> rimes = new LinkedHashMap<>();
        if (theme == null) {
            ajouter(rimes, classer(motCherche, departs, EntreePhonetique::generale));
            ajouter(rimes, classer(motCherche, departs, entree -> !entree.generale()));
        } else {
            ajouter(rimes, classer(motCherche, departs, entree -> entree.theme() == theme));
            if (rimes.size() < SUGGESTIONS_D_UNE_GRILLE) {
                ajouter(rimes, classer(motCherche, departs, EntreePhonetique::generale));
            }
        }
        return List.copyOf(rimes.values());
    }

    private List<Candidat> classer(String motCherche, List<EntreePhonetique> departs,
            Predicate<EntreePhonetique> source) {
        Map<String, Candidat> candidats = new HashMap<>();
        for (EntreePhonetique depart : departs) {
            for (EntreePhonetique entree : parFamille.get(depart.famille())) {
                String motCandidat = normaliserMot(entree.mot());
                if (!motCherche.equals(motCandidat) && source.test(entree) && !flexionHomophone(departs, motCandidat)) {
                    Candidat candidat = new Candidat(entree.mot(), depart.rimeRicheAvec(entree), entree.frequence());
                    candidats.merge(motCandidat, candidat, DictionnaireDeRimesPhonetique::meilleurCandidat);
                }
            }
        }
        return candidats.values().stream().sorted(ORDRE).toList();
    }

    /**
     * Compare le départ à toutes les entrées du mot candidat, quelle que soit leur source : une liste thématique ne
     * note qu'une terminaison, sans lemme, et seul le lexique général permet de reconnaître la flexion.
     */
    private boolean flexionHomophone(List<EntreePhonetique> departs, String motCandidat) {
        List<EntreePhonetique> formes = parMot.get(motCandidat);
        return departs.stream().anyMatch(depart -> formes.stream().anyMatch(forme -> depart.sons().equals(forme.sons())
                && !Collections.disjoint(depart.lemmes(), forme.lemmes())));
    }

    private static void ajouter(Map<String, String> rimes, List<Candidat> candidats) {
        for (Candidat candidat : candidats) {
            rimes.putIfAbsent(normaliserMot(candidat.mot()), candidat.mot());
        }
    }

    private static Candidat meilleurCandidat(Candidat premier, Candidat second) {
        return ORDRE.compare(premier, second) <= 0 ? premier : second;
    }

    private static String normaliserMot(String mot) {
        return Normalizer.normalize(mot.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFC)
                .replace("œ", "oe").replace("æ", "ae");
    }

    private static String cleDeFiltre(String mot) {
        String decompose = Normalizer.normalize(normaliserMot(mot), Normalizer.Form.NFD);
        return DIACRITIQUES.matcher(decompose).replaceAll("");
    }

    private record Candidat(String mot, boolean riche, double frequence) { }
}
