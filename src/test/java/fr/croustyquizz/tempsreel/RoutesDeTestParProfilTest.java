package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Les routes /api/test servent au développement et à la recette, jamais à la production. */
class RoutesDeTestParProfilTest {

	@ParameterizedTest(name = "profils {0} : routes de test {1}")
	@CsvSource({
		"dev, true",
		"'prod,recette', true",
		"prod, false",
	})
	void lesRoutesDeTestNExistentQuEnDeveloppementEtEnRecette(String profils, boolean presentes) {
		try (AnnotationConfigApplicationContext contexte = new AnnotationConfigApplicationContext()) {
			contexte.getEnvironment().setActiveProfiles(profils.split(","));
			// L'enregistrement évalue @Profile sans démarrer les beans ni leurs dépendances.
			contexte.register(CanauxDeTestControleur.class, BuzzControleur.class);

			assertThat(contexte.containsBeanDefinition("canauxDeTestControleur")).isEqualTo(presentes);
			assertThat(contexte.containsBeanDefinition("buzzControleur")).isEqualTo(presentes);
		}
	}
}
