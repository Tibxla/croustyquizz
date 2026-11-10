package fr.croustyquizz.soiree;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Personne connectée à une Soirée depuis sa Manette, identifiée par un pseudo et
 * par son jeton de reconnexion, sans compte.
 */
@Entity
public class Joueur {

	static final int LONGUEUR_MAX_PSEUDO = 30;

	@Id
	private UUID id;

	private UUID soireeId;

	private String pseudo;

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
		this.empreinteJeton = Objects.requireNonNull(jeton, "jeton").empreinte();
		this.arriveLe = Objects.requireNonNull(arriveLe, "arriveLe");
	}

	/**
	 * Espaces de début et de fin retirés, de 1 à {@value #LONGUEUR_MAX_PSEUDO}
	 * caractères. On compte les caractères Unicode, pas les unités UTF-16 : un
	 * emoji compte pour un.
	 */
	static String nettoyerPseudo(String pseudo) {
		if (pseudo == null) {
			throw new PseudoInvalide();
		}
		String nettoye = pseudo.strip();
		int longueur = nettoye.codePointCount(0, nettoye.length());
		if (longueur == 0 || longueur > LONGUEUR_MAX_PSEUDO) {
			throw new PseudoInvalide();
		}
		return nettoye;
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

	public Instant getArriveLe() {
		return arriveLe;
	}

	public boolean isExclu() {
		return exclu;
	}

}
