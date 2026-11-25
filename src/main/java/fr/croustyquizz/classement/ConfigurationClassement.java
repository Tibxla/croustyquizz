package fr.croustyquizz.classement;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fr.croustyquizz.soiree.ClassementDeSoiree;

@Configuration(proxyBeanMethods = false)
class ConfigurationClassement {

	@Bean
	ClassementDeSoiree classementDeSoiree() {
		return new ServiceClassement();
	}
}
