package fr.croustyquizz.manche;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import fr.croustyquizz.TestcontainersConfiguration;
import tools.jackson.databind.json.JsonMapper;

/** PostgreSQL réel : Flyway, mapping JSON, reçu durable et restauration de la pause. Nécessite Docker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, SocleMancheJpaTest.Configuration.class})
class SocleMancheJpaTest {
	@Autowired private ServiceDeManche<?, ?> service;
	@Autowired private RegistreDesDeroulements registre;
	@Autowired private JsonMapper json;

	@Test
	void restaureLEtatDuModeEtLeRecuDepuisPostgres() {
		UUID soiree = UUID.randomUUID();
		Acteur acteur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
		UUID manche = service.creer(soiree, new ModeFacticeDuSocle.Reglages(3, 20));
		service.demarrer(manche, new EnvoiDeCommande(acteur, "/demarrer", UUID.randomUUID(), Map.of()));
		var pause = new InterventionDeManche(InterventionDeManche.Action.METTRE_EN_PAUSE, null);
		var envoi = new EnvoiDeCommande(acteur, "/commandes", UUID.randomUUID(), pause);
		var accepte = service.piloter(manche, envoi, pause);
		Object avant = service.instantane(soiree, manche);
		registre.retirer(manche);
		assertThat(service.instantane(soiree, manche)).isEqualTo(avant);
		assertThat(((DeroulementFactice.Etat) avant).pause()).isTrue();
		var repete = service.piloter(manche, envoi, pause);
		assertThat((tools.jackson.databind.JsonNode) json.valueToTree(ReponsesDeCommande.corps(repete, envoi.route())))
				.isEqualTo(json.valueToTree(ReponsesDeCommande.corps(accepte, envoi.route())));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Configuration {
		@Bean
		@org.springframework.context.annotation.Primary
		Horloge horlogeFacticeJpa() { return new HorlogeFactice(); }

		@Bean
		ServiceDeManche<?, ?> serviceFacticeJpa(SocleDeManche socle, EntityManager entites, JsonMapper json) {
			var fabrique = new ModeFacticeDuSocle();
			PersistanceDuMode<DeroulementFactice.Etat> persistance = new PersistanceDuMode<>() {
				public DeroulementFactice.Etat charger(UUID mancheId) {
					return json.readValue(entites.find(EtatFacticePersistant.class, mancheId).getDonnees(), DeroulementFactice.Etat.class);
				}
				public void enregistrer(UUID mancheId, Object instantane) {
					entites.merge(new EtatFacticePersistant(mancheId, json.writeValueAsString(instantane)));
				}
			};
			return new ServiceDeManche<>(fabrique, persistance, socle);
		}
	}
}
