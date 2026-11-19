package fr.croustyquizz.soiree;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.passage.PorteeDePassage;
import fr.croustyquizz.tempsreel.AccesAuxVues;

/**
 * Portée des Passages d'une Soirée enregistrée (#111) : la Manche de Karaoké doit
 * appartenir à la Soirée, et un Joueur y être présent, non exclu. {@code passage}
 * ne dépend pas de {@code soiree} : c'est {@code soiree} qui implémente son port.
 * <p>
 * Une Soirée absente de la base est confiée à l'autre portée du profil, la
 * démonstration en dev et recette ({@code @Fallback}) ; sans elle, elle est introuvable.
 */
@Component
class PorteeDePassageSoiree implements PorteeDePassage {

	private final DepotDeSoirees soirees;
	private final DepotDeJoueurs joueurs;
	private final DepotDeManches manches;
	private final ObjectProvider<PorteeDePassage> portees;

	PorteeDePassageSoiree(DepotDeSoirees soirees, DepotDeJoueurs joueurs, DepotDeManches manches,
			ObjectProvider<PorteeDePassage> portees) {
		this.soirees = soirees;
		this.joueurs = joueurs;
		this.manches = manches;
		this.portees = portees;
	}

	@Override
	@Transactional(readOnly = true)
	public void verifier(Acteur acteur, UUID soireeId, UUID mancheId) {
		if (!soirees.existsById(soireeId)) {
			autre().verifier(acteur, soireeId, mancheId);
			return;
		}
		AccesAuxVues.verifier(acteur, soireeId, null);
		ManchePersistante manche = manches.charger(mancheId);
		boolean joueurAbsent = acteur.type() == Acteur.Type.JOUEUR
				&& !ParticipantsDePassageSoiree.present(joueurs, soireeId, acteur.identifiant());
		if (manche == null || !soireeId.equals(manche.getSoireeId()) || manche.getMode() != ModeDeJeu.KARAOKE || joueurAbsent) {
			throw introuvable();
		}
	}

	/**
	 * La Soirée est {@code EN_COURS} et la Manche en cours ou en pause, comme l'exige le contrat
	 * (« Recherche et gestion exigent une Soirée EN_COURS ») : une Soirée seulement ouverte ne
	 * fait tourner aucune File d'attente. Le lancement des Soirées arrive avec #12.
	 */
	@Override
	@Transactional(readOnly = true)
	public boolean estActive(UUID soireeId, UUID mancheId) {
		Soiree soiree = soirees.findById(soireeId).orElse(null);
		if (soiree == null) {
			PorteeDePassage autre = autreOuNull();
			return autre != null && autre.estActive(soireeId, mancheId);
		}
		return soiree.getEtat() == EtatSoiree.EN_COURS && manches.deLaSoiree(soireeId).stream()
				.anyMatch(manche -> manche.getMancheId().equals(mancheId)
						&& (manche.getEtat() == EtatManche.EN_COURS || manche.getEtat() == EtatManche.EN_PAUSE));
	}

	private PorteeDePassage autre() {
		PorteeDePassage autre = autreOuNull();
		if (autre == null) {
			throw introuvable();
		}
		return autre;
	}

	/** Le filtre {@code instanceof} suppose des proxys CGLIB, le réglage par défaut de Spring Boot. */
	private PorteeDePassage autreOuNull() {
		return portees.orderedStream().filter(portee -> !(portee instanceof PorteeDePassageSoiree)).findFirst().orElse(null);
	}

	private static RuntimeException introuvable() {
		return new Refuse(404, "RESSOURCE_INTROUVABLE", "Manche ou Joueur introuvable.").exception();
	}

}
