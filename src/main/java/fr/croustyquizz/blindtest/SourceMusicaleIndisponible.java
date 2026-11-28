package fr.croustyquizz.blindtest;

/** Catalogue injoignable, en panne ou trop lent : 503 SOURCE_MUSICALE_INDISPONIBLE. */
public class SourceMusicaleIndisponible extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SourceMusicaleIndisponible(String message) {
		super(message);
	}

	public SourceMusicaleIndisponible(String message, Throwable cause) {
		super(message, cause);
	}

}
