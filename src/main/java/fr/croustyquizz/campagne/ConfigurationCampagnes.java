package fr.croustyquizz.campagne;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
class ConfigurationCampagnes {

	@Bean
	@Fallback
	ContactsDuLieu contactsVides() {
		return lieuId -> List.of();
	}

	@Bean
	@Fallback
	EnvoiDeSms envoiDeSms(@Value("${croustyquizz.campagnes.prestataire:}") String prestataire,
			@Value("${croustyquizz.campagnes.twilio.account-sid:}") String compte,
			@Value("${croustyquizz.campagnes.twilio.auth-token:}") String jeton,
			@Value("${croustyquizz.campagnes.twilio.numero-expediteur:}") String expediteur,
			@Value("${croustyquizz.campagnes.twilio.modele-essai:}") String modeleEssai) {
		if ("twilio".equals(prestataire) && StringUtils.hasText(compte) && StringUtils.hasText(jeton)
				&& StringUtils.hasText(expediteur)) {
			return new EnvoiDeSmsTwilio(URI.create("https://api.twilio.com"),
					compte.strip(), jeton.strip(), expediteur.strip(), modeleEssai, Duration.ofSeconds(5));
		}
		return new EnvoiDeSmsFactice();
	}

	@Bean
	@Profile("dev & !recette & !prod")
	@ConditionalOnProperty(name = "croustyquizz.campagnes.contacts-demonstration", havingValue = "true")
	ContactsDuLieu contactsDeDemonstration() {
		return lieuId -> IntStream.rangeClosed(1, 3).mapToObj(index -> contactFictif(lieuId, index)).toList();
	}

	private static ContactsDuLieu.Contact contactFictif(UUID lieuId, int index) {
		UUID id = UUID.nameUUIDFromBytes((lieuId + "/contact-fictif/" + index).getBytes(StandardCharsets.UTF_8));
		return new ContactsDuLieu.Contact(id, "+3363998000" + index,
				"https://croustyquizz.invalid/stop/contact-" + index);
	}
}
