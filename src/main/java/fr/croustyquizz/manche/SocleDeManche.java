package fr.croustyquizz.manche;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.VerrousSoiree;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dépendances communes partagées par les services des Modes de jeu. Les observateurs apprennent chaque fin de
 * Manche ; une configuration sans observateur reçoit une liste vide.
 */
@Component
public record SocleDeManche(VerrousSoiree verrous, PlatformTransactionManager transactions,
		DepotDeManches depot, RegistreDesDeroulements registre, RegistreDesEnvois envois,
		PublicationDeVues vues, Horloge horloge, JsonMapper json, PilotageDeSoiree pilotage,
		List<ObservateurDeFinDeManche> observateurs) {
	public SocleDeManche {
		observateurs = List.copyOf(observateurs);
	}
}
