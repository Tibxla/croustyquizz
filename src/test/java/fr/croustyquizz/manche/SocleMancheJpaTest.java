package fr.croustyquizz.manche;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.soiree.Format;
import fr.croustyquizz.soiree.Pilotage;
import fr.croustyquizz.soiree.ServiceDesAcces;
import fr.croustyquizz.soiree.ServiceSoiree;
import fr.croustyquizz.tempsreel.FournisseurDeBloc;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** PostgreSQL réel : Flyway, mapping JSON, reçu durable et restauration de la pause. Nécessite Docker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, SocleMancheJpaTest.Configuration.class})
class SocleMancheJpaTest {
	private static final String CONTRAT = "docs/api/openapi.yaml";

	@LocalServerPort private int port;
	@Autowired @Qualifier("serviceFacticeJpa") private ServiceDeManche<?, ?> service;
	@Autowired private RegistreDesDeroulements registre;
	@Autowired private HorlogeFactice horloge;
	@Autowired private ServiceSoiree soirees;
	@Autowired private ServiceDesAcces acces;
	@Autowired private PublicationDeVues vues;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private JsonMapper json;

	@Test
	void leClassementSeCumuleEntreLesManchesEtResteAccessibleApresLaSoiree() throws Exception {
		UUID soiree = soirees.creer("Quiz du jeudi", Format.SOLO, Pilotage.MANUEL).getId();
		String code = soirees.ouvrir(soiree).getCodeAcces();
		var lea = soirees.rejoindre(code, "Léa");
		String cleEcran = acces.ouvrirEcran(soiree).cle();
		var animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
		var joueuse = new Acteur(Acteur.Type.JOUEUR, lea.joueur().getId(), soiree);
		var reglages = new ModeFacticeDuSocle.Reglages(1, 20);
		UUID premiere = service.creer(soiree, reglages);
		UUID seconde = service.creer(soiree, reglages);

		jouer(premiere, animateur, joueuse, Duration.ofSeconds(2));
		JsonNode classement = lire(soiree, "/etat/manette", lea.jeton().valeur(), "retrouverEtatManette")
				.at("/vue/classement");
		assertThat(classement.at("/0/points").asLong()).isOne();
		assertThat(lire(soiree, "/etat/salle", cleEcran, "retrouverEtatSalle").at("/vue/classement"))
				.isEqualTo(classement);
		horloge.avancer(Duration.ofSeconds(20));
		jouer(seconde, animateur, joueuse, Duration.ofSeconds(3));
		horloge.avancer(Duration.ofSeconds(20));
		registre.retirer(premiere);
		registre.retirer(seconde);

		// La commande de fin de Soirée est livrée par le pilotage de Soirée.
		jdbc.update("update soiree set etat = 'TERMINEE', terminee_le = ? where id = ?",
				Timestamp.from(horloge.maintenant()), soiree);
		vues.publier(soiree, new Accepte(null, Set.of(Destination.CONSOLE), "soiree-terminee"));
		String jeton = "demo:ANIMATEUR:" + soiree + ":" + animateur.identifiant();
		JsonNode console = lire(soiree, "/etat/console", jeton, "retrouverEtatConsole").path("vue");
		assertThat(console.at("/soiree/etat").asString()).isEqualTo("TERMINEE");
		assertThat(console.at("/classement/0/concurrentId").asString()).isEqualTo(joueuse.identifiant().toString());
		assertThat(console.at("/classement/0/points").asLong()).isEqualTo(2);
		assertThat(console.at("/classement/0/bonnesReponses").asInt()).isEqualTo(2);
		assertThat(console.at("/classement/0/tempsCumuleMs").asLong()).isEqualTo(5_000);
		assertThat(lire(soiree, "/classement", jeton, "consulterClassement").path("classement"))
				.isEqualTo(console.path("classement"));
	}

	private void jouer(UUID manche, Acteur animateur, Acteur joueuse, Duration tempsDeReponse) {
		assertThat(service.demarrer(manche, new EnvoiDeCommande(animateur, "/demarrer", UUID.randomUUID(), Map.of())))
				.isInstanceOf(Accepte.class);
		horloge.avancer(tempsDeReponse);
		var envoi = new EnvoiDeCommande(joueuse, "/action", UUID.randomUUID(), Map.of());
		assertThat(service.executer(manche, envoi, () -> PreparationDeCommande.prete((d, recueLe) ->
				d.recevoir(new DeroulementFactice.ActionFactice(joueuse.identifiant(), recueLe))))).isInstanceOf(Accepte.class);
		assertThat(service.executer(manche, envoi, () -> { throw new AssertionError("L'envoi a déjà son reçu."); }))
				.isInstanceOf(Accepte.class);
	}

	private JsonNode lire(UUID soiree, String chemin, String jeton, String operation) throws Exception {
		var reponse = RestClient.create("http://localhost:" + port).get()
				.uri("/api/soirees/" + soiree + chemin)
				.header("Authorization", "Bearer " + jeton)
				.retrieve().onStatus(statut -> statut.isError(), (requete, refus) -> { }).toEntity(String.class);
		ContratApi.verifier(CONTRAT, operation, reponse);
		assertThat(reponse.getStatusCode().value()).isEqualTo(200);
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
		return json.readTree(reponse.getBody());
	}

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
		HorlogeFactice horlogeFacticeJpa() { return new HorlogeFactice(); }

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

		@Bean
		FournisseurDeBloc blocFacticeJpa(
				@Qualifier("serviceFacticeJpa") ObjectProvider<ServiceDeManche<?, ?>> service,
				DepotDeManches depot) {
			return new BlocFacticeDuSocle(service::getObject, depot);
		}
	}
}
