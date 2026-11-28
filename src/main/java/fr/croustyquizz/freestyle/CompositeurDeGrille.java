package fr.croustyquizz.freestyle;

import fr.croustyquizz.freestyle.SchemaDeRimes.Famille;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Choisit des familles distinctes et place un mot par mesure sur l’Instrumentale. */
public final class CompositeurDeGrille {
    private final DictionnaireDeRimes dictionnaire;

    public CompositeurDeGrille(DictionnaireDeRimes dictionnaire) {
        this.dictionnaire = dictionnaire;
    }

    public Optional<GrilleDeRimes> composer(ReglagesFreestyle reglages, Instrumentale instrumentale,
            List<String> motsDuTheme) {
        int nombreMesures = reglages.nombreMesures();
        if (instrumentale.positionSurLeFichier(nombreMesures * GrilleDeRimes.TEMPS_PAR_MESURE)
                > Instrumentale.POSITION_MAX_MS) {
            return Optional.empty();
        }
        return choisirFamilles(reglages, motsDuTheme).map(familles -> placer(reglages, instrumentale, familles));
    }

    private Optional<Familles> choisirFamilles(ReglagesFreestyle reglages, List<String> motsDuTheme) {
        int minimum = reglages.schemaDeRimes().motsDeFamille(Famille.A, reglages.nombreMesures());
        boolean deuxFamilles = reglages.schemaDeRimes() != SchemaDeRimes.AAAA;
        List<Map<String, String>> dejaVues = new ArrayList<>();
        for (String depart : motsDuTheme) {
            Map<String, String> candidats = motsDistincts(depart, reglages.theme());
            if (candidats.size() < minimum) { continue; }
            if (!deuxFamilles) { return Optional.of(new Familles(List.copyOf(candidats.values()), List.of())); }
            for (Map<String, String> precedents : dejaVues) {
                if (Collections.disjoint(precedents.keySet(), candidats.keySet())) {
                    return Optional.of(new Familles(List.copyOf(precedents.values()), List.copyOf(candidats.values())));
                }
            }
            dejaVues.add(candidats);
        }
        return Optional.empty();
    }

    private Map<String, String> motsDistincts(String depart, ThemeDeRimes theme) {
        Map<String, String> mots = new LinkedHashMap<>();
        ajouter(mots, depart);
        dictionnaire.rimesDe(depart, theme).forEach(mot -> ajouter(mots, mot));
        dictionnaire.rimesDe(depart).forEach(mot -> ajouter(mots, mot));
        return mots;
    }

    private static void ajouter(Map<String, String> mots, String mot) {
        if (!mot.isBlank()) {
            String graphie = Normalizer.normalize(mot.strip(), Normalizer.Form.NFC);
            mots.putIfAbsent(graphie.toLowerCase(Locale.ROOT), graphie);
        }
    }

    private static GrilleDeRimes placer(ReglagesFreestyle reglages, Instrumentale instrumentale, Familles familles) {
        var mots = new ArrayList<GrilleDeRimes.Mot>(reglages.nombreMesures());
        var familleA = familles.a().iterator();
        var familleB = familles.b().iterator();
        for (int mesure = 1; mesure <= reglages.nombreMesures(); mesure++) {
            int temps = GrilleDeRimes.TEMPS_DU_MOT;
            int tempsEcoules = (mesure - 1) * GrilleDeRimes.TEMPS_PAR_MESURE + temps - 1;
            Famille famille = reglages.schemaDeRimes().famille(mesure);
            String mot = famille == Famille.A ? familleA.next() : familleB.next();
            mots.add(new GrilleDeRimes.Mot(mesure, temps, instrumentale.positionSurLeFichier(tempsEcoules), famille, mot));
        }
        return new GrilleDeRimes(reglages.nombreMesures(), mots);
    }

    private record Familles(List<String> a, List<String> b) { }
}
