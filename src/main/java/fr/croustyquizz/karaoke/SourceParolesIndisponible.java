package fr.croustyquizz.karaoke;

/** Panne de la source externe, indépendante du protocole public de l'application. */
public class SourceParolesIndisponible extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public SourceParolesIndisponible(String message) { super(message); }
    public SourceParolesIndisponible(String message, Throwable cause) { super(message, cause); }
}
