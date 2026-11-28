package fr.croustyquizz.tempsreel;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fr.croustyquizz.manche.Horloge;
import tools.jackson.databind.json.JsonMapper;

/** Branche la limite de requêtes (#4) devant les contrôleurs, avec les seuils de {@code croustyquizz.limites}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReglagesDesLimites.class)
public class ConfigurationLimiteDeRequetes {

	@Bean
	LimiteDeRequetes limiteDeRequetes(ReglagesDesLimites reglages, Horloge horloge, JsonMapper json) {
		return new LimiteDeRequetes(reglages, horloge, json);
	}

}
