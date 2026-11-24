package fr.croustyquizz.blindtest;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Branchement du catalogue Deezer ; aucune consultation au démarrage. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProprietesCatalogueMusical.class)
public class ConfigurationCatalogueMusical {

	@Bean
	HttpClient clientCatalogueMusical(ProprietesCatalogueMusical proprietes) {
		return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofMillis(proprietes.delaiDeezerMs())).build();
	}

	@Bean
	CatalogueMusical catalogueMusical(ProprietesCatalogueMusical proprietes, HttpClient clientCatalogueMusical,
			JsonMapper json) {
		return new AdaptateurDeezer(proprietes, clientCatalogueMusical, json);
	}

}
