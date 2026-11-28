package fr.croustyquizz.freestyle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Dictionnaire de rimes livré avec le Freestyle : les listes thématiques, l'extrait de Lexique 3.83 et le filtre de
 * vulgarité, tous dans {@code src/main/resources/freestyle/}. Le premier appel à {@link #partage()} le charge, une
 * seule fois ; l'instance, immuable, se partage ensuite entre toutes les Manches.
 */
public final class DictionnaireDeRimesFourni {
    private static final String DOSSIER = "/freestyle/";
    private static final String EXTRAIT_DE_LEXIQUE = "lexique383-extrait.tsv";

    private DictionnaireDeRimesFourni() {
    }

    public static DictionnaireDeRimes partage() {
        return Partage.DICTIONNAIRE;
    }

    static DictionnaireDeRimes charger() {
        try {
            List<EntreePhonetique> entrees = new ArrayList<>(ListesThematiquesDeRimes.lire());
            try (BufferedReader lexique = ouvrir(EXTRAIT_DE_LEXIQUE)) {
                entrees.addAll(LexiqueDeRimes.lire(lexique, EXTRAIT_DE_LEXIQUE));
            }
            try (BufferedReader filtre = ouvrir("mots-filtres.txt")) {
                List<String> motsFiltres = filtre.lines().map(String::strip).filter(mot -> !mot.isEmpty()).toList();
                return new DictionnaireDeRimesPhonetique(entrees, motsFiltres);
            }
        } catch (IOException erreur) {
            throw new UncheckedIOException("Impossible de charger le dictionnaire de rimes", erreur);
        }
    }

    static BufferedReader ouvrir(String fichier) throws IOException {
        InputStream flux = DictionnaireDeRimesFourni.class.getResourceAsStream(DOSSIER + fichier);
        if (flux == null) {
            throw new IOException("Ressource introuvable : " + DOSSIER + fichier);
        }
        return new BufferedReader(new InputStreamReader(flux, StandardCharsets.UTF_8));
    }

    private static final class Partage {
        private static final DictionnaireDeRimes DICTIONNAIRE = charger();
    }
}
