package fr.croustyquizz.soiree;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * Résultats personnels d'une Soirée sans Mode de jeu qui les fournisse.
 * {@code @Fallback} : l'implémentation du Quiz (#14) la
 * remplace sans autre configuration.
 */
@Component
@Fallback
class ResultatsVides implements ResultatsDeSoiree {

	@Override
	public Optional<AccuseReponse> derniereReponse(UUID soireeId, UUID joueurId) {
		return Optional.empty();
	}

	@Override
	public Optional<ResultatPersonnel> resultatPersonnel(UUID soireeId, UUID joueurId) {
		return Optional.empty();
	}

}
