package fr.croustyquizz.campagne;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class ConfigurationEnvoiDeSmsTest {
	private static final String PRESTATAIRE = "CROUSTYQUIZZ_CAMPAGNES_PRESTATAIRE";
	private static final String COMPTE = "TWILIO_ACCOUNT_SID";
	private static final String JETON = "TWILIO_AUTH_TOKEN";
	private static final String EXPEDITEUR = "TWILIO_NUMERO_EXPEDITEUR";
	private static final String MODELE = "TWILIO_MODELE_ESSAI";
	private static final Map<String, Object> VARIABLES = Map.of(
			PRESTATAIRE, "twilio",
			COMPTE, "AC00000000000000000000000000000000",
			JETON, "jeton-fictif-pour-le-test",
			EXPEDITEUR, "+12025550123");

	@Test
	void simuleSansVariables() {
		contexte(Map.of()).run(contexte -> {
			assertThat(contexte).hasSingleBean(EnvoiDeSms.class);
			assertThat(contexte.getBean(EnvoiDeSms.class)).isInstanceOf(EnvoiDeSmsFactice.class);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {PRESTATAIRE, COMPTE, JETON, EXPEDITEUR})
	void simuleSiUneVariableEstVide(String variable) {
		var variables = new HashMap<>(VARIABLES);
		variables.put(variable, " ");
		contexte(variables).run(contexte -> {
			assertThat(contexte).hasSingleBean(EnvoiDeSms.class);
			assertThat(contexte.getBean(EnvoiDeSms.class)).isInstanceOf(EnvoiDeSmsFactice.class);
		});
	}

	@Test
	void choisitTwilioEnEssaiParDefautQuandLesIdentifiantsSontRenseignes() {
		contexte(VARIABLES).run(contexte -> {
			assertThat(contexte).hasSingleBean(EnvoiDeSms.class);
			assertThat(contexte.getBean(EnvoiDeSms.class).mode()).isEqualTo(EnvoiDeSms.Mode.TWILIO_ESSAI);
			assertThat(contexte.getEnvironment().getProperty("croustyquizz.campagnes.twilio.modele-essai"))
					.isEqualTo("sms_marketing_promotions");
		});
	}

	@Test
	void choisitLEnvoiDuTexteQuandLeModeleEstExplicitementVide() {
		var variables = new HashMap<>(VARIABLES);
		variables.put(MODELE, "");
		contexte(variables).run(contexte -> {
			assertThat(contexte).hasSingleBean(EnvoiDeSms.class);
			assertThat(contexte.getBean(EnvoiDeSms.class).mode()).isEqualTo(EnvoiDeSms.Mode.TWILIO);
		});
	}

	private static ApplicationContextRunner contexte(Map<String, Object> variables) {
		return new ApplicationContextRunner()
				.withInitializer(contexte -> contexte.getEnvironment().getPropertySources().replace(
						StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
						new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
								variables)))
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withUserConfiguration(ConfigurationCampagnes.class);
	}
}
