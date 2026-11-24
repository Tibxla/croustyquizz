package fr.croustyquizz.freestyle;

import fr.croustyquizz.freestyle.GrilleDeRimes.Mot;
import fr.croustyquizz.freestyle.SchemaDeRimes.Famille;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Mots visibles de chaque famille sur l’ensemble de la Grille. */
public enum DifficulteFreestyle {
    TOUS, PREMIER, DERNIER, AUCUN;

    public GrilleDeRimes appliquer(GrilleDeRimes grille) {
        return switch (this) {
            case TOUS -> grille;
            case PREMIER -> new GrilleDeRimes(grille.nombreMesures(), premiersMots(grille.mots()));
            case DERNIER -> new GrilleDeRimes(grille.nombreMesures(), premiersMots(grille.mots().reversed()).reversed());
            case AUCUN -> new GrilleDeRimes(grille.nombreMesures(), grille.mots().stream().map(Mot::masque).toList());
        };
    }

    private static List<Mot> premiersMots(List<Mot> mots) {
        var famillesVues = EnumSet.noneOf(Famille.class);
        List<Mot> projection = new ArrayList<>(mots.size());
        for (Mot mot : mots) {
            projection.add(famillesVues.add(mot.famille()) ? mot : mot.masque());
        }
        return projection;
    }
}
