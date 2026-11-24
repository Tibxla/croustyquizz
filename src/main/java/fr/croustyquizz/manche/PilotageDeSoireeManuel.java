package fr.croustyquizz.manche;

import java.util.UUID;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Pilotage d'une Soirée absente de la base hors des profils de démonstration. */
@Component
@Profile("!dev & !recette")
@Fallback
final class PilotageDeSoireeManuel implements PilotageDeSoiree {
	@Override
	public boolean estAutomatique(UUID soireeId) {
		return false;
	}
}
