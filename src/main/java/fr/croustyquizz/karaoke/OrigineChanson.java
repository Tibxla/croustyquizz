package fr.croustyquizz.karaoke;

/** Provenance d'une Chanson et de ses paroles synchronisées (ADR 0011). */
public enum OrigineChanson {
    LRCLIB, BIBLIOTHEQUE;

    public static OrigineChanson de(String chansonId) {
        return IdentifiantsChanson.estDeLaBibliotheque(chansonId) ? BIBLIOTHEQUE : LRCLIB;
    }
}
