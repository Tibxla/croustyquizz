package fr.croustyquizz.manche;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.VerrousSoiree;
import tools.jackson.databind.json.JsonMapper;

/** Dépendances communes partagées par les services des Modes de jeu. */
@Component
public record SocleDeManche(VerrousSoiree verrous, PlatformTransactionManager transactions,
		DepotDeManches depot, RegistreDesDeroulements registre, RegistreDesEnvois envois,
		PublicationDeVues vues, Horloge horloge, JsonMapper json) { }
