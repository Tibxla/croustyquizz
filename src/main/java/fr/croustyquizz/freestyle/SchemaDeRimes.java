package fr.croustyquizz.freestyle;

import java.util.List;

import static fr.croustyquizz.freestyle.SchemaDeRimes.Famille.A;
import static fr.croustyquizz.freestyle.SchemaDeRimes.Famille.B;

public enum SchemaDeRimes {
    AABB(List.of(A, A, B, B)),
    ABAB(List.of(A, B, A, B)),
    AAAA(List.of(A, A, A, A));

    public enum Famille { A, B }

    private final List<Famille> motif;

    SchemaDeRimes(List<Famille> motif) {
        this.motif = motif;
    }

    Famille famille(int mesure) {
        return motif.get((mesure - 1) % motif.size());
    }

    int motsDeFamille(Famille famille, int nombreMesures) {
        return (int) motif.stream().filter(famille::equals).count() * (nombreMesures / motif.size());
    }
}
