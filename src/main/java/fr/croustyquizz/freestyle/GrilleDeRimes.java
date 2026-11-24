package fr.croustyquizz.freestyle;

import fr.croustyquizz.freestyle.SchemaDeRimes.Famille;
import java.util.List;

public record GrilleDeRimes(int nombreMesures, List<Mot> mots) {
    static final int TEMPS_PAR_MESURE = 4;
    static final int TEMPS_DU_MOT = 3;

    public GrilleDeRimes {
        mots = List.copyOf(mots);
    }

    public record Mot(int mesure, int temps, long positionMs, Famille famille, String mot) {
        Mot masque() { return new Mot(mesure, temps, positionMs, famille, null); }
    }
}
