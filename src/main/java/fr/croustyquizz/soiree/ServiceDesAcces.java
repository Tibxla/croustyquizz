package fr.croustyquizz.soiree;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.manche.Acteur;

/**
 * Secrets d'accès à une Soirée : création de la clé d'un Écran de salle, et
 * reconnaissance de l'Acteur qui présente un jeton de reconnexion ou une clé.
 */
@Service
public class ServiceDesAcces {

	private final DepotDeSoirees depotDeSoirees;
	private final DepotDeJoueurs depotDeJoueurs;
	private final DepotDAccesEcran depotDAccesEcran;
	private final Clock horloge;
	private final SecureRandom hasard = new SecureRandom();

	ServiceDesAcces(DepotDeSoirees depotDeSoirees, DepotDeJoueurs depotDeJoueurs, DepotDAccesEcran depotDAccesEcran,
			Clock horloge) {
		this.depotDeSoirees = depotDeSoirees;
		this.depotDeJoueurs = depotDeJoueurs;
		this.depotDAccesEcran = depotDAccesEcran;
		this.horloge = horloge;
	}

	/**
	 * Crée l'accès d'un Écran de salle à cette Soirée. Chaque appel donne une
	 * nouvelle clé ; les précédentes restent valides jusqu'à leur expiration.
	 *
	 * @throws SoireeIntrouvable si aucune Soirée ne porte cet identifiant
	 * @throws SoireeDejaLancee  si la Soirée est terminée
	 */
	@Transactional
	public OuvertureEcran ouvrirEcran(UUID soireeId) {
		Soiree soiree = depotDeSoirees.findById(soireeId).orElseThrow(() -> new SoireeIntrouvable(soireeId));
		if (soiree.getEtat() == EtatSoiree.TERMINEE) {
			throw new SoireeDejaLancee(soiree.getEtat());
		}
		String cle = Secrets.tirer(hasard);
		AccesEcran acces = depotDAccesEcran.save(new AccesEcran(soireeId, cle, horloge.instant()));
		return new OuvertureEcran(acces, cle);
	}

	/**
	 * Acteur prouvé par ce secret : un Joueur non exclu par son jeton de
	 * reconnexion, ou un Écran de salle par une clé non expirée. Vide sinon.
	 */
	@Transactional(readOnly = true)
	public Optional<Acteur> reconnaitre(String secret) {
		if (!Secrets.aLaForme(secret)) {
			return Optional.empty();
		}
		byte[] empreinte = Secrets.empreinte(secret);
		Optional<Acteur> joueur = depotDeJoueurs.findByEmpreinteJeton(empreinte)
				.filter(trouve -> !trouve.isExclu())
				.map(trouve -> new Acteur(Acteur.Type.JOUEUR, trouve.getId(), trouve.getSoireeId()));
		if (joueur.isPresent()) {
			return joueur;
		}
		return depotDAccesEcran.findByEmpreinteCle(empreinte)
				.filter(acces -> acces.estValideA(horloge.instant()))
				.map(acces -> new Acteur(Acteur.Type.ECRAN, acces.getId(), acces.getSoireeId()));
	}

}
