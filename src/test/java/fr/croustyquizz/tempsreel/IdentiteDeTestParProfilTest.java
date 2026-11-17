package fr.croustyquizz.tempsreel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.context.annotation.Configuration;

/** L'identité de démonstration est une route de test, sans ressource statique publique. */
class IdentiteDeTestParProfilTest {
	@Configuration(proxyBeanMethods = false)
	@EnableWebMvc
	static class ConfigurationWeb { }

	@ParameterizedTest(name = "profils {0} : identité accessible {1}")
	@CsvSource({ "dev, true", "recette, true", "'prod,recette', true", "prod, false", "default, false" })
	void laPageNExisteQuEnDeveloppementEtEnRecette(String profils, boolean presente) throws Exception {
		try (var contexte = new AnnotationConfigWebApplicationContext()) {
			contexte.setServletContext(new MockServletContext());
			contexte.getEnvironment().setActiveProfiles(profils.split(","));
			contexte.register(ConfigurationWeb.class, IdentiteDeTestControleur.class);
			contexte.refresh();
			var resultat = MockMvcBuilders.webAppContextSetup(contexte).build().perform(get("/test/identite.html"));
			if (presente) {
				resultat.andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"));
			} else {
				resultat.andExpect(status().isNotFound());
			}
			assertThat(getClass().getResource("/static/test/identite.html")).isNull();
		}
	}
}
