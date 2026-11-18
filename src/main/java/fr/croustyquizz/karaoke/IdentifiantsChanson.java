package fr.croustyquizz.karaoke;

import java.util.OptionalLong;
import java.util.UUID;

/** Identifiants opaques stables, sans table de correspondance ni droit associé. */
public final class IdentifiantsChanson {
    private IdentifiantsChanson() { }

    public static String encoder(long idLrclib) {
        if (!estRepresentable(idLrclib)) {
            throw new IllegalArgumentException("Identité de Chanson hors bornes");
        }
        return new UUID(0x63726f7573747900L, idLrclib ^ 0x516b24e8a9d3705fL).toString();
    }

    public static OptionalLong decoder(String chansonId) {
        if (chansonId == null) { return OptionalLong.empty(); }
        try {
            UUID identifiant = UUID.fromString(chansonId);
            long id = identifiant.getLeastSignificantBits() ^ 0x516b24e8a9d3705fL;
            if (!identifiant.toString().equals(chansonId)
                    || identifiant.getMostSignificantBits() != 0x63726f7573747900L || !estRepresentable(id)) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(id);
        } catch (IllegalArgumentException erreur) {
            return OptionalLong.empty();
        }
    }

    private static boolean estRepresentable(long id) { return id > 0 && id <= 9007199254740991L; }
}
