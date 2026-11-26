package fr.croustyquizz.karaoke;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * Identifiants opaques stables, sans table de correspondance ni droit associé. Deux familles de même forme :
 * LRCLIB et la Bibliothèque de base (ADR 0011), distinguées par leur préfixe et jamais confondues.
 */
public final class IdentifiantsChanson {
    private static final long PREFIXE_LRCLIB = 0x63726f7573747900L;
    private static final long PREFIXE_BIBLIOTHEQUE = 0x63726f7573747962L;
    private static final long MASQUE = 0x516b24e8a9d3705fL;

    private IdentifiantsChanson() { }

    public static String encoder(long idLrclib) { return encoder(PREFIXE_LRCLIB, idLrclib); }

    public static OptionalLong decoder(String chansonId) { return decoder(PREFIXE_LRCLIB, chansonId); }

    /** Le numéro d'une Chanson du catalogue, entier positif stable, en devient l'identifiant opaque. */
    public static String encoderBibliotheque(long numero) { return encoder(PREFIXE_BIBLIOTHEQUE, numero); }

    public static OptionalLong decoderBibliotheque(String chansonId) { return decoder(PREFIXE_BIBLIOTHEQUE, chansonId); }

    public static boolean estDeLaBibliotheque(String chansonId) { return decoderBibliotheque(chansonId).isPresent(); }

    private static String encoder(long prefixe, long id) {
        if (!estRepresentable(id)) {
            throw new IllegalArgumentException("Identité de Chanson hors bornes");
        }
        return new UUID(prefixe, id ^ MASQUE).toString();
    }

    private static OptionalLong decoder(long prefixe, String chansonId) {
        if (chansonId == null) { return OptionalLong.empty(); }
        try {
            UUID identifiant = UUID.fromString(chansonId);
            long id = identifiant.getLeastSignificantBits() ^ MASQUE;
            if (!identifiant.toString().equals(chansonId)
                    || identifiant.getMostSignificantBits() != prefixe || !estRepresentable(id)) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(id);
        } catch (IllegalArgumentException erreur) {
            return OptionalLong.empty();
        }
    }

    private static boolean estRepresentable(long id) { return id > 0 && id <= 9007199254740991L; }
}
