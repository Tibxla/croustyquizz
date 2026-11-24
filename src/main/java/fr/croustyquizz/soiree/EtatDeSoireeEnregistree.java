package fr.croustyquizz.soiree;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.manche.EtatDeSoiree;

/**
 * État d'une Soirée enregistrée, lu en base (#111). Une Soirée absente de la base est
 * confiée à l'autre implémentation du profil (démonstration en dev et recette,
 * {@code @Fallback}) ; sans repli, la Soirée est inconnue.
 */
@Component
class EtatDeSoireeEnregistree implements EtatDeSoiree {

	private final DepotDeSoirees soirees;
	private final ObjectProvider<EtatDeSoiree> etats;

	EtatDeSoireeEnregistree(DepotDeSoirees soirees, ObjectProvider<EtatDeSoiree> etats) {
		this.soirees = soirees;
		this.etats = etats;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean existe(UUID soireeId) {
		if (soirees.existsById(soireeId)) {
			return true;
		}
		EtatDeSoiree autre = autre();
		return autre != null && autre.existe(soireeId);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean estEnCours(UUID soireeId) {
		Optional<Soiree> soiree = soirees.findById(soireeId);
		if (soiree.isEmpty()) {
			EtatDeSoiree autre = autre();
			return autre != null && autre.estEnCours(soireeId);
		}
		return soiree.get().getEtat() == EtatSoiree.EN_COURS;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean estTerminee(UUID soireeId) {
		Optional<Soiree> soiree = soirees.findById(soireeId);
		if (soiree.isEmpty()) {
			EtatDeSoiree autre = autre();
			return autre != null && autre.estTerminee(soireeId);
		}
		return soiree.get().getEtat() == EtatSoiree.TERMINEE;
	}

	/** Le filtre {@code instanceof} suppose des proxys CGLIB, le réglage par défaut de Spring Boot. */
	private EtatDeSoiree autre() {
		return etats.orderedStream().filter(etat -> !(etat instanceof EtatDeSoireeEnregistree)).findFirst().orElse(null);
	}

}
