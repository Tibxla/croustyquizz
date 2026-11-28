package fr.croustyquizz.karaoke;

/** Catalogue de la Bibliothèque de base inutilisable : l'application refuse de démarrer. */
public class CatalogueDeBaseInvalide extends IllegalStateException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public CatalogueDeBaseInvalide(String message) { super(message); }
    public CatalogueDeBaseInvalide(String message, Throwable cause) { super(message, cause); }
}
