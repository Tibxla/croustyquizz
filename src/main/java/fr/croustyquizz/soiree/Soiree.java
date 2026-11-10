package fr.croustyquizz.soiree;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/**
 * Événement organisé dans un Lieu, que les Joueurs rejoignent grâce à son code
 * d'accès. Le temps est toujours passé en paramètre : la Soirée ne lit jamais
 * l'heure elle-même.
 */
@Entity
public class Soiree {

	static final int LONGUEUR_MAX_TITRE = 150;

	@Id
	private UUID id;

	private String titre;

	@Enumerated(EnumType.STRING)
	private Format format;

	@Enumerated(EnumType.STRING)
	private Pilotage pilotage;

	@Enumerated(EnumType.STRING)
	private EtatSoiree etat;

	@Column(name = "code_acces")
	private String codeAcces;

	private Instant creeeLe;

	private Instant ouverteLe;

	private Instant debuteeLe;

	private Instant termineeLe;

	protected Soiree() {
		// Pour JPA.
	}

	/** Une Soirée naît en Préparation, sans code d'accès. */
	public Soiree(String titre, Format format, Pilotage pilotage, Instant creeeLe) {
		String titreNettoye = Objects.requireNonNull(titre, "titre").strip();
		if (titreNettoye.isEmpty() || titreNettoye.codePointCount(0, titreNettoye.length()) > LONGUEUR_MAX_TITRE) {
			throw new IllegalArgumentException("Le titre doit faire de 1 à " + LONGUEUR_MAX_TITRE + " caractères");
		}
		this.id = UUID.randomUUID();
		this.titre = titreNettoye;
		this.format = Objects.requireNonNull(format, "format");
		this.pilotage = Objects.requireNonNull(pilotage, "pilotage");
		this.etat = EtatSoiree.PREPARATION;
		this.creeeLe = Objects.requireNonNull(creeeLe, "creeeLe");
	}

	/**
	 * Ouvre la Soirée aux Joueurs avec ce code d'accès, dont l'appelant a vérifié
	 * l'unicité. Sans effet si elle est déjà ouverte : elle garde son code.
	 *
	 * @return vrai si la Soirée vient d'être ouverte, faux si elle l'était déjà
	 * @throws SoireeDejaLancee si la Soirée est en cours ou terminée
	 */
	public boolean ouvrir(String code, Instant maintenant) {
		switch (etat) {
			case OUVERTE -> {
				return false;
			}
			case EN_COURS, TERMINEE -> throw new SoireeDejaLancee(etat);
			case PREPARATION -> {
				if (!CodeAcces.estValide(code)) {
					throw new IllegalArgumentException("Code d'accès invalide : " + code);
				}
				this.codeAcces = code;
				this.ouverteLe = Objects.requireNonNull(maintenant, "maintenant");
				this.etat = EtatSoiree.OUVERTE;
				return true;
			}
		}
		throw new IllegalStateException("État inconnu : " + etat);
	}

	/** Un Joueur peut arriver seulement dans une Soirée ouverte (arrivée en retard : #10). */
	public boolean accepteLesArrivees() {
		return etat == EtatSoiree.OUVERTE;
	}

	public UUID getId() {
		return id;
	}

	public String getTitre() {
		return titre;
	}

	public Format getFormat() {
		return format;
	}

	public Pilotage getPilotage() {
		return pilotage;
	}

	public EtatSoiree getEtat() {
		return etat;
	}

	/** Absent tant que la Soirée est en Préparation. */
	public String getCodeAcces() {
		return codeAcces;
	}

	public Instant getCreeeLe() {
		return creeeLe;
	}

	public Instant getOuverteLe() {
		return ouverteLe;
	}

	public Instant getDebuteeLe() {
		return debuteeLe;
	}

	public Instant getTermineeLe() {
		return termineeLe;
	}

}
