package fr.croustyquizz.tempsreel;

import java.util.UUID;

import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.EtatDeSoiree;

/**
 * État des Soirées de démonstration, absentes de la base : toujours en cours, comme
 * le montre {@link FournisseurCommunDemonstration}. {@code @Fallback} : l'état de la
 * Soirée enregistrée ({@code soiree}, #111) l'emporte et lui confie ces Soirées.
 */
@Component
@Profile({"dev", "recette"})
@Fallback
public final class EtatDeSoireeDemonstration implements EtatDeSoiree {

	@Override
	public boolean estEnregistree(UUID soireeId) {
		return false;
	}

	@Override
	public boolean estEnCours(UUID soireeId) {
		return true;
	}

	@Override
	public boolean estTerminee(UUID soireeId) {
		return false;
	}

}
