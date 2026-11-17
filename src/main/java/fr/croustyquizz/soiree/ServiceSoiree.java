package fr.croustyquizz.soiree;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

	/** Index qui garantit qu'un code ne désigne qu'une Soirée qu'on peut rejoindre. */
	static final String INDEX_CODE_ACTIF = "soiree_code_acces_actif";

	/** Index qui garantit qu'un pseudo ne désigne qu'un Joueur dans une Soirée. */
	static final String INDEX_PSEUDO = "joueur_pseudo_par_soiree";

	private final DepotDeSoirees depotDeSoirees;
	private final DepotDeJoueurs depotDeJoueurs;
	private final Clock horloge;
	private final TirageDeCode tirage;
	private final FiltreDePseudos filtre;
	private final TransactionTemplate nouvelleTransaction;
	private final SecureRandom hasard = new SecureRandom();

	ServiceSoiree(DepotDeSoirees depotDeSoirees, DepotDeJoueurs depotDeJoueurs, Clock horloge, TirageDeCode tirage,
			FiltreDePseudos filtre, PlatformTransactionManager transactions) {
		this.depotDeSoirees = depotDeSoirees;
		this.depotDeJoueurs = depotDeJoueurs;
		this.horloge = horloge;
		this.tirage = tirage;
		this.filtre = filtre;
		this.nouvelleTransaction = new TransactionTemplate(transactions);
		this.nouvelleTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
	 * <p>
	 * L'index {@value #INDEX_CODE_ACTIF} est seul juge de l'unicité du code : un
	 * code déjà pris par une autre Soirée, même ouverte au même instant, fait
	 * échouer l'essai, et l'ouverture est retentée avec un nouveau tirage. Chaque
	 * essai a sa propre transaction, car PostgreSQL abandonne une transaction
	 * après une violation de contrainte : l'appeler hors de toute transaction.
	 *
	 * @throws SoireeIntrouvable si aucune Soirée ne porte cet identifiant
	 * @throws SoireeDejaLancee  si la Soirée est en cours ou terminée
	 */
	public Soiree ouvrir(UUID soireeId) {
		for (int essai = 1; essai <= ESSAIS_DE_CODE; essai++) {
			try {
				return nouvelleTransaction.execute(statut -> ouvrirAvecUnTirage(soireeId));
			} catch (DataIntegrityViolationException refus) {
				if (!violeIndex(refus, INDEX_CODE_ACTIF)) {
					throw refus;
				}
			}
		}
		throw new IllegalStateException("Aucun code d'accès libre après " + ESSAIS_DE_CODE + " essais");
	}

	private Soiree ouvrirAvecUnTirage(UUID soireeId) {
		// Le verrou sérialise les ouvertures d'une même Soirée : la seconde la trouve ouverte et garde son code.
		Soiree soiree = depotDeSoirees.verrouiller(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId));
		if (soiree.getEtat() == EtatSoiree.PREPARATION) {
			soiree.ouvrir(tirage.tirer(), horloge.instant());
			// Écrit maintenant, pour qu'une collision lève ici une exception traduite par Spring.
			depotDeSoirees.flush();
		} else {
			soiree.ouvrir(soiree.getCodeAcces(), horloge.instant());
		}
		return soiree;
	}

	private static boolean violeIndex(Throwable refus, String index) {
		for (Throwable cause = refus; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& index.equals(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
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
	 * <p>
	 * L'index {@value #INDEX_PSEUDO} garantit l'unicité du pseudo, même entre deux
	 * arrivées simultanées ; la vérification préalable évite seulement d'aller
	 * jusqu'à l'erreur de la base dans le cas courant.
	 *
	 * @param saisieDuCode code tel que tapé ou lu dans le QR code ; casse et espaces sont tolérés
	 * @throws CodeAccesInvalide si aucune Soirée à rejoindre ne porte ce code
	 * @throws PseudoInvalide    si le pseudo est absent, vide ou trop long
	 * @throws PseudoRefuse      si le filtre de pseudos l'écarte
	 * @throws PseudoDejaPris    si un Joueur de la Soirée le porte déjà, même parti ou exclu
	 */
	@Transactional
	public ArriveeDeJoueur rejoindre(String saisieDuCode, String pseudo) {
		String code = CodeAcces.normaliser(saisieDuCode).orElseThrow(CodeAccesInvalide::new);
		Soiree soiree = depotDeSoirees.findByCodeAccesAndEtatIn(code, EtatSoiree.AVEC_CODE_ACTIF)
				.filter(Soiree::accepteLesArrivees)
				.orElseThrow(CodeAccesInvalide::new);
		JetonDeReconnexion jeton = JetonDeReconnexion.generer(hasard);
		Joueur joueur = new Joueur(soiree.getId(), pseudo, jeton, horloge.instant());
		if (!filtre.estAcceptable(joueur.getPseudo())) {
			throw new PseudoRefuse();
		}
		if (depotDeJoueurs.existsBySoireeIdAndPseudoNormalise(soiree.getId(), joueur.getPseudoNormalise())) {
			throw new PseudoDejaPris();
		}
		try {
			depotDeJoueurs.saveAndFlush(joueur);
		}
		catch (DataIntegrityViolationException refus) {
			if (violeIndex(refus, INDEX_PSEUDO)) {
				throw new PseudoDejaPris(refus);
			}
			throw refus;
		}
		return new ArriveeDeJoueur(joueur, jeton, soiree);
	}

}
