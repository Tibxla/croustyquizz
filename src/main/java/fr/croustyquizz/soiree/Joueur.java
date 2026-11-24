package fr.croustyquizz.soiree;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Personne connectée à une Soirée depuis sa Manette, identifiée par un pseudo et
 * par son jeton de reconnexion, sans compte.
 */
@Entity
public class Joueur {

	static final int LONGUEUR_MAX_PSEUDO = 30;

	private static final Pattern MARQUES = Pattern.compile("\\p{M}+");
	/** Tous les blancs Unicode, espaces insécables compris, que {@code \s} seul et {@code strip()} ignorent. */
	private static final Pattern BLANCS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern BLANCS_AUTOUR = Pattern.compile("^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);

	@Id
	private UUID id;

	private UUID soireeId;

	private String pseudo;

	/** Clé d'unicité du pseudo dans la Soirée, voir {@link #cleDePseudo(String)}. */
	private String pseudoNormalise;

	private byte[] empreinteJeton;

	private Instant arriveLe;

	private boolean exclu;

	protected Joueur() {
		// Pour JPA.
	}

	/**
	 * Le Joueur qui arrive dans une Soirée. Le refus des pseudos déjà pris ou
	 * injurieux vient avec #10.
	 *
	 * @throws PseudoInvalide si le pseudo est vide ou trop long
	 */
	public Joueur(UUID soireeId, String pseudo, JetonDeReconnexion jeton, Instant arriveLe) {
		this.id = UUID.randomUUID();
		this.soireeId = Objects.requireNonNull(soireeId, "soireeId");
		this.pseudo = nettoyerPseudo(pseudo);
		this.pseudoNormalise = cleDePseudo(this.pseudo);
		this.empreinteJeton = Objects.requireNonNull(jeton, "jeton").empreinte();
		this.arriveLe = Objects.requireNonNull(arriveLe, "arriveLe");
	}

	/**
	 * Blancs de début et de fin retirés, insécables compris, de 1 à
	 * {@value #LONGUEUR_MAX_PSEUDO} caractères. On compte les caractères Unicode,
	 * pas les unités UTF-16 : un emoji compte pour un.
	 */
	static String nettoyerPseudo(String pseudo) {
		if (pseudo == null) {
			throw new PseudoInvalide();
		}
		String nettoye = BLANCS_AUTOUR.matcher(pseudo).replaceAll("");
		int longueur = nettoye.codePointCount(0, nettoye.length());
		if (longueur == 0 || longueur > LONGUEUR_MAX_PSEUDO) {
			throw new PseudoInvalide();
		}
		return nettoye;
	}

	/**
	 * Deux pseudos de même clé désignent le même nom à l'écran : « Léa »,
	 * « lea » et « LÉA » se confondent, tout comme « Jean  Luc » et « Jean Luc »,
	 * avec une espace ordinaire ou insécable.
	 */
	static String cleDePseudo(String pseudo) {
		String sansAccent = MARQUES.matcher(Normalizer.normalize(pseudo, Normalizer.Form.NFD)).replaceAll("");
		String recompose = Normalizer.normalize(sansAccent, Normalizer.Form.NFC);
		return BLANCS.matcher(recompose).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
	}

	public UUID getId() {
		return id;
	}

	public UUID getSoireeId() {
		return soireeId;
	}

	public String getPseudo() {
		return pseudo;
	}

	String getPseudoNormalise() {
		return pseudoNormalise;
	}

	public Instant getArriveLe() {
		return arriveLe;
	}

	public boolean isExclu() {
		return exclu;
	}

}
