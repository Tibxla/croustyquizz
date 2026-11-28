package fr.croustyquizz.campagne;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;

@Configuration(proxyBeanMethods = false)
class ConfigurationCampagnes {

	@Bean
	@Fallback
	EnvoiDeSms envoiDeSmsFactice() {
		return new EnvoiDeSmsFactice();
	}
}
