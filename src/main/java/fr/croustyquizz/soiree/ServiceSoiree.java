package fr.croustyquizz.soiree;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Création et ouverture des Soirées, arrivée des Joueurs. Les contrôleurs HTTP
 * et le serveur MCP passent par ce service et appliquent donc les mêmes règles.
 */
@Service
public class ServiceSoiree {

	/**
	 * Avec 32^6 codes possibles, une collision est déjà rare ; plusieurs de suite
	 * signaleraient un problème plutôt que de la malchance.
	 */
	static final int ESSAIS_DE_CODE = 20;

	private final DepotDeSoirees depotDeSoirees;
	private final DepotDeJoueurs depotDeJoueurs;
	private final Clock horloge;
	private final SecureRandom hasard = new SecureRandom();

	ServiceSoiree(DepotDeSoirees depotDeSoirees, DepotDeJoueurs depotDeJoueurs, Clock horloge) {
		this.depotDeSoirees = depotDeSoirees;
		this.depotDeJoueurs = depotDeJoueurs;
		this.horloge = horloge;
	}

	/**
	 * Crée une Soirée en Préparation, sans code d'accès.
	 *
	 * @throws TitreInvalide si le titre est absent, vide ou trop long
	 */
	@Transactional
	public Soiree creer(String titre, Format format, Pilotage pilotage) {
		return depotDeSoirees.save(new Soiree(titre, format, pilotage, horloge.instant()));
	}

	/** @throws SoireeIntrouvable si aucune Soirée ne porte cet identifiant */
	@Transactional(readOnly = true)
	public Soiree consulter(UUID soireeId) {
		return depotDeSoirees.findById(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId));
	}

	/**
	 * Ouvre la Soirée avec un code d'accès libre. Répéter l'ouverture d'une Soirée
	 * déjà ouverte la renvoie telle quelle, avec le même code.
	 *
	 * @throws SoireeIntrouvable si aucune Soirée ne porte cet identifiant
	 * @throws SoireeDejaLancee  si la Soirée est en cours ou terminée
	 */
	@Transactional
	public Soiree ouvrir(UUID soireeId) {
		// Le verrou empêche deux ouvertures simultanées de tirer deux codes différents.
		Soiree soiree = depotDeSoirees.verrouiller(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId));
		String code = soiree.getEtat() == EtatSoiree.PREPARATION ? codeLibre() : soiree.getCodeAcces();
		soiree.ouvrir(code, horloge.instant());
		return soiree;
	}

	/**
	 * Joueurs de la Soirée, du premier arrivé au dernier.
	 *
	 * @throws SoireeIntrouvable si aucune Soirée ne porte cet identifiant
	 */
	@Transactional(readOnly = true)
	public List<Joueur> joueurs(UUID soireeId) {
		if (!depotDeSoirees.existsById(soireeId)) {
			throw new SoireeIntrouvable(soireeId);
		}
		return depotDeJoueurs.findBySoireeIdOrderByArriveLeAsc(soireeId);
	}

	/**
	 * Fait arriver un Joueur dans la Soirée ouverte ou en cours qui porte ce code.
	 * Le refus des pseudos déjà pris ou injurieux vient avec #10.
	 *
	 * @param saisieDuCode code tel que tapé ou lu dans le QR code ; casse et espaces sont tolérés
	 * @throws CodeAccesInvalide si aucune Soirée à rejoindre ne porte ce code
	 * @throws PseudoInvalide    si le pseudo est absent, vide ou trop long
	 */
	@Transactional
	public ArriveeDeJoueur rejoindre(String saisieDuCode, String pseudo) {
		String code = CodeAcces.normaliser(saisieDuCode).orElseThrow(CodeAccesInvalide::new);
		Soiree soiree = depotDeSoirees.findByCodeAccesAndEtatIn(code, EtatSoiree.AVEC_CODE_ACTIF)
				.filter(Soiree::accepteLesArrivees)
				.orElseThrow(CodeAccesInvalide::new);
		JetonDeReconnexion jeton = JetonDeReconnexion.generer(hasard);
		Joueur joueur = depotDeJoueurs.save(new Joueur(soiree.getId(), pseudo, jeton, horloge.instant()));
		return new ArriveeDeJoueur(joueur, jeton, soiree);
	}

	private String codeLibre() {
		for (int essai = 0; essai < ESSAIS_DE_CODE; essai++) {
			String code = CodeAcces.generer(hasard);
			if (!depotDeSoirees.existsByCodeAccesAndEtatIn(code, EtatSoiree.AVEC_CODE_ACTIF)) {
				return code;
			}
		}
		throw new IllegalStateException("Aucun code d'accès libre après " + ESSAIS_DE_CODE + " essais");
	}

}
