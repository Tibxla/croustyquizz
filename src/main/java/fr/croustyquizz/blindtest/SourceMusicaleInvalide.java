package fr.croustyquizz.blindtest;

/**
 * Source que Deezer ne connaît pas, ou qui n'a pas assez de Morceaux jouables : 422 SOURCE_MUSICALE_INVALIDE. Le
 * message, montré tel quel à l'Animateur, dit quoi corriger ; réessayer sans changer les réglages ne servira à rien.
 */
public class SourceMusicaleInvalide extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SourceMusicaleInvalide(String message) {
		super(message);
	}

	public SourceMusicaleInvalide(String message, Throwable cause) {
		super(message, cause);
	}

}
