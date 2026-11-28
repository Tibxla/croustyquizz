package fr.croustyquizz.soiree;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.EnchainementDeSoiree;
import fr.croustyquizz.manche.Refuse;

/**
 * Suite d'une Soirée enregistrée à l'échéance d'une transition du Pilotage Automatique (#123) : le même chemin
 * que {@code piloterSoiree} {@code MANCHE_SUIVANTE}, au nom de l'Animateur du Pilotage Automatique, avec le reçu
 * tiré de la Manche terminée. Un refus rendu par l'orchestrateur devient une exception, pour que le socle
 * republie les vues.
 * <p>
 * Une Soirée absente de la base est confiée à l'autre enchaînement du profil, la démonstration en dev et recette
 * ({@code @Fallback}) ; sans lui, elle est introuvable.
 */
@Component
class EnchainementDeSoireeEnregistree implements EnchainementDeSoiree {

	private final DepotDeSoirees soirees;
	private final OrchestrateurDeSoiree orchestrateur;
	private final ObjectProvider<EnchainementDeSoiree> enchainements;

	EnchainementDeSoireeEnregistree(DepotDeSoirees soirees, OrchestrateurDeSoiree orchestrateur,
			ObjectProvider<EnchainementDeSoiree> enchainements) {
		this.soirees = soirees;
		this.orchestrateur = orchestrateur;
		this.enchainements = enchainements;
	}

	@Override
	public void mancheSuivante(UUID soireeId, UUID mancheTermineeId) {
		if (!soirees.existsById(soireeId)) {
			autre(soireeId).mancheSuivante(soireeId, mancheTermineeId);
			return;
		}
		if (orchestrateur.mancheSuivante(EnchainementDeSoiree.acteur(soireeId), mancheTermineeId) instanceof Refuse refus) {
			throw refus.exception();
		}
	}

	/** Le filtre {@code instanceof} suppose des proxys CGLIB, le réglage par défaut de Spring Boot. */
	private EnchainementDeSoiree autre(UUID soireeId) {
		return enchainements.orderedStream().filter(enchainement -> !(enchainement instanceof EnchainementDeSoireeEnregistree))
				.findFirst().orElseThrow(() -> new SoireeIntrouvable(soireeId));
	}

}
