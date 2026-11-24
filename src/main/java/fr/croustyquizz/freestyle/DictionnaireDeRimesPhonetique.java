package fr.croustyquizz.freestyle;

import static java.util.stream.Collectors.groupingBy;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class DictionnaireDeRimesPhonetique implements DictionnaireDeRimes {
    private static final Pattern DIACRITIQUES = Pattern.compile("\\p{M}+");
    private static final Comparator<Candidat> ORDRE = Comparator.comparing(Candidat::riche).reversed()
            .thenComparing(Comparator.comparingDouble(Candidat::frequence).reversed())
            .thenComparing(Candidat::mot);

    private final Map<String, List<EntreePhonetique>> parMot;
    private final Map<List<String>, List<EntreePhonetique>> parFamille;

    DictionnaireDeRimesPhonetique(List<EntreePhonetique> entrees, List<String> motsFiltres) {
        Set<String> filtre = motsFiltres.stream().map(DictionnaireDeRimesPhonetique::cleDeFiltre)
                .collect(Collectors.toUnmodifiableSet());
        List<EntreePhonetique> entreesAdmises = entrees.stream()
                .filter(entree -> !filtre.contains(cleDeFiltre(entree.mot())))
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
        Map<String, Candidat> candidats = new HashMap<>();
        for (EntreePhonetique depart : parMot.getOrDefault(motCherche, List.of())) {
            for (EntreePhonetique entree : parFamille.get(depart.famille())) {
                String motCandidat = normaliserMot(entree.mot());
                if (!motCherche.equals(motCandidat) && (theme == null || theme == entree.theme())) {
                    Candidat candidat = new Candidat(entree.mot(), depart.rimeRicheAvec(entree), entree.frequence());
                    candidats.merge(motCandidat, candidat, DictionnaireDeRimesPhonetique::meilleurCandidat);
                }
            }
        }
        return candidats.values().stream().sorted(ORDRE).map(Candidat::mot).toList();
    }

    private static Candidat meilleurCandidat(Candidat premier, Candidat second) {
        return ORDRE.compare(premier, second) <= 0 ? premier : second;
    }

    private static String normaliserMot(String mot) {
        return Normalizer.normalize(mot.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }

    private static String cleDeFiltre(String mot) {
        String decompose = Normalizer.normalize(normaliserMot(mot), Normalizer.Form.NFD);
        return DIACRITIQUES.matcher(decompose).replaceAll("");
    }

    private record Candidat(String mot, boolean riche, double frequence) { }
}
