package fr.croustyquizz.freestyle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Charge les listes et le filtre fournis avec le Freestyle. */
public final class ListesThematiquesDeRimes {
    private static final String DOSSIER = "/freestyle/";
    private static final int NOMBRE_COLONNES = 3;

    private ListesThematiquesDeRimes() {
    }

    public static DictionnaireDeRimes charger() {
        List<EntreePhonetique> entrees = new ArrayList<>();
        try {
            for (ThemeDeRimes theme : ThemeDeRimes.values()) {
                lireTheme(theme, entrees);
            }
            try (BufferedReader filtre = ouvrir("mots-filtres.txt")) {
                List<String> motsFiltres = filtre.lines().map(String::strip).filter(mot -> !mot.isEmpty()).toList();
                return new DictionnaireDeRimesPhonetique(entrees, motsFiltres);
            }
        } catch (IOException erreur) {
            throw new UncheckedIOException("Impossible de charger le dictionnaire de rimes", erreur);
        }
    }

    private static void lireTheme(ThemeDeRimes theme, List<EntreePhonetique> entrees) throws IOException {
        String fichier = theme.name().toLowerCase(Locale.ROOT) + ".tsv";
        try (BufferedReader lecteur = ouvrir(fichier)) {
            if (!"mot\tterminaison\tfréquence".equals(lecteur.readLine())) {
                throw new IOException("En-tête du dictionnaire invalide dans " + fichier);
            }
            int numero = 1;
            for (String ligne = lecteur.readLine(); ligne != null; ligne = lecteur.readLine()) {
                numero++;
                try {
                    String[] colonnes = ligne.split("\t", -1);
                    if (colonnes.length != NOMBRE_COLONNES) {
                        throw new IllegalArgumentException("Trois colonnes attendues");
                    }
                    entrees.add(new EntreePhonetique(colonnes[0], List.of(colonnes[1].split(" ")),
                            Double.parseDouble(colonnes[2]), theme));
                } catch (IllegalArgumentException erreur) {
                    throw new IOException("Entrée du dictionnaire invalide dans " + fichier + ":" + numero, erreur);
                }
            }
        }
    }

    private static BufferedReader ouvrir(String fichier) throws IOException {
        InputStream flux = ListesThematiquesDeRimes.class.getResourceAsStream(DOSSIER + fichier);
        if (flux == null) {
            throw new IOException("Ressource introuvable : " + DOSSIER + fichier);
        }
        return new BufferedReader(new InputStreamReader(flux, StandardCharsets.UTF_8));
    }
}
