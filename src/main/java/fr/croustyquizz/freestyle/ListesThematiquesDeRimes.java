package fr.croustyquizz.freestyle;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lit les listes thématiques écrites pour le Freestyle, une par {@link ThemeDeRimes}. */
final class ListesThematiquesDeRimes {
    private static final int NOMBRE_COLONNES = 3;

    private ListesThematiquesDeRimes() {
    }

    static List<EntreePhonetique> lire() throws IOException {
        List<EntreePhonetique> entrees = new ArrayList<>();
        for (ThemeDeRimes theme : ThemeDeRimes.values()) {
            lireTheme(theme, entrees);
        }
        return entrees;
    }

    private static void lireTheme(ThemeDeRimes theme, List<EntreePhonetique> entrees) throws IOException {
        String fichier = theme.name().toLowerCase(Locale.ROOT) + ".tsv";
        try (BufferedReader lecteur = DictionnaireDeRimesFourni.ouvrir(fichier)) {
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
}
