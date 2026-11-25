package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.SocleDeManche;
import fr.croustyquizz.passage.ParticipantsDePassage;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assemblage du Blind test ; le catalogue Deezer vient de {@link ConfigurationCatalogueMusical}. */
@Configuration(proxyBeanMethods = false)
// « BlindTest » nomme le Mode de jeu : PMD prend à tort la classe pour une classe de test.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class ConfigurationBlindTest {

	@Bean
	TirageDeMorceaux tirageDeMorceaux(CatalogueMusical catalogue) {
		return new TirageDeMorceaux(catalogue, new SecureRandom());
	}

	/** Les Joueurs présents sont ceux de la Soirée, sans les exclus : un retardataire compte aussitôt (ADR 0009). */
	@Bean
	FabriqueBlindTest fabriqueBlindTest(PersistanceBlindTestJpa persistance, ParticipantsDePassage participants) {
		return new FabriqueBlindTest(persistance, soireeId -> participants.pseudos(soireeId).keySet());
	}

	/** Demandes de lien d'extrait, hors du verrou de la Soirée ; aucune ne retient un fil du serveur HTTP. */
	@Bean(destroyMethod = "shutdown")
	ExecutorService preparationsBlindTest() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	@Bean
	ServiceBlindTest serviceBlindTest(FabriqueBlindTest fabrique, PersistanceBlindTestJpa persistance,
			SocleDeManche socle, CatalogueMusical catalogue, TirageDeMorceaux tirage, ParticipantsDePassage participants,
			@Qualifier("preparationsBlindTest") ExecutorService preparations) {
		return new ServiceBlindTest(fabrique, persistance, socle, catalogue, tirage, participants, preparations);
	}

	@Bean
	FournisseurDeBlocBlindTest fournisseurDeBlocBlindTest(DepotDeManches depot,
			ObjectProvider<ServiceBlindTest> service) {
		return new FournisseurDeBlocBlindTest(depot, service);
	}

}
