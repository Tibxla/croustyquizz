package fr.croustyquizz.animateur;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.SessionAnimateurHttp;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.HorlogeFactice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class InvitationAnimateurHttpTest {

	private static final String INVITATIONS = "/api/animateurs/invitations";
	private static final String CONSULTATION = INVITATIONS + "/consultation";
	private static final String ACCEPTATION = INVITATIONS + "/acceptation";
	private static final String SESSION = "/api/animateurs/session";
	private static final String MOT_DE_PASSE = "Un mot de passe réservé au test";
	private static final String INVITATION_INVALIDE = "INVITATION_INVALIDE";
	private static final String EMAIL_DEJA_UTILISE = "EMAIL_DEJA_UTILISE";

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private HorlogeFactice horloge;

	@Autowired
	private JdbcClient jdbc;

	@TestConfiguration(proxyBeanMethods = false)
	static class TempsDesInvitations {
		@Bean
		@Primary
		HorlogeFactice horlogeDesInvitations() {
			return new HorlogeFactice();
		}
	}

	@Test
	void rejointLeLieuDeLAnimateurEtOuvreUneSession(CapturedOutput journal) throws Exception {
		var invitant = SessionAnimateurHttp.ouvrir(port, json);
		var identiteInvitant = consulterSession(invitant.cookie());
		String email = "invite@example.com";
		String cle = creerInvitation(invitant.entetes(), email).path("cle").asString();
		var consultation = verifier("consulterInvitationAnimateur", 200, consulterInvitation(cle));
		assertThat(consultation.path("nomLieu").asString()).isEqualTo("Le Balcon");
		assertThat(consultation.path("ville").asString()).isEqualTo("Nanterre");
		assertThat(consultation.path("email").asString()).isEqualTo(email);

		var anonyme = sessionAnonyme();
		var reponse = accepter(anonyme.entetes(), cle);
		var invite = verifier("accepterInvitationAnimateur", 201, reponse);
		assertThat(reponse.getHeaders().getLocation()).hasToString(SESSION);
		assertThat(cookie(reponse)).isNotEqualTo(anonyme.cookie());
		assertThat(invite.path("id").asString()).isNotEqualTo(identiteInvitant.path("id").asString());
		assertThat(invite.path("nom").asString()).isEqualTo("Émilie");
		assertThat(invite.path("lieuId")).isEqualTo(identiteInvitant.path("lieuId"));
		assertThat(consulterSession(cookie(reponse))).isEqualTo(invite);
		var soiree = verifier("creerSoiree", 201, poster("/api/soirees", invitant.entetes(),
				Map.of("titre", "La Soirée du Balcon", "format", "SOLO", "pilotage", "MANUEL")));
		var soireeDepuisInvitation = client().get().uri("/api/soirees/{id}", soiree.path("id").asString())
				.header(HttpHeaders.COOKIE, cookie(reponse)).retrieve().toEntity(String.class);
		assertThat(verifier("consulterSoiree", 200, soireeDepuisInvitation)).isEqualTo(soiree);

		var reconnexion = poster(SESSION, sessionAnonyme().entetes(),
				Map.of("email", "INVITE@EXAMPLE.com", "motDePasse", MOT_DE_PASSE));
		assertThat(verifier("connecterAnimateur", 200, reconnexion)).isEqualTo(invite);
		verifierRefus("consulterInvitationAnimateur", 404, INVITATION_INVALIDE, consulterInvitation(cle));
		verifierRefus("accepterInvitationAnimateur", 404, INVITATION_INVALIDE, accepter(sessionAnonyme().entetes(), cle));
		assertThat(journal.getAll()).doesNotContain(cle, MOT_DE_PASSE);
	}

	@Test
	void exigeUneSessionPourCreerEtProtegeLesMutationsParCsrf() throws Exception {
		var invitant = SessionAnimateurHttp.ouvrir(port, json);
		var anonyme = sessionAnonyme();
		var demande = Map.of("email", "protection@example.com");
		verifierRefus("creerInvitationAnimateur", 401, "AUTHENTIFICATION_REQUISE",
				poster(INVITATIONS, anonyme.entetes(), demande));
		var cookieInvitant = new HttpHeaders();
		cookieInvitant.set(HttpHeaders.COOKIE, invitant.cookie());
		verifierRefus("creerInvitationAnimateur", 403, "CSRF_INVALIDE", poster(INVITATIONS, cookieInvitant, demande));
		var bearer = invitant.entetes();
		bearer.setBearerAuth("demo:ANIMATEUR:factice");
		verifierRefus("creerInvitationAnimateur", 403, "ACCES_REFUSE", poster(INVITATIONS, bearer, demande));
		verifierRefus("creerInvitationAnimateur", 422, "DONNEES_INVALIDES",
				poster(INVITATIONS, invitant.entetes(), Map.of("email", "adresse-invalide")));

		String cle = creerInvitation(invitant.entetes(), demande.get("email")).path("cle").asString();
		verifierRefus("accepterInvitationAnimateur", 403, "CSRF_INVALIDE", accepter(HttpHeaders.EMPTY, cle));
		verifierRefus("accepterInvitationAnimateur", 422, "DONNEES_INVALIDES", poster(ACCEPTATION, anonyme.entetes(),
				Map.of("cle", cle, "nom", "Émilie", "motDePasse", "court")));
		verifierRefus("accepterInvitationAnimateur", 400, "REQUETE_INVALIDE", poster(ACCEPTATION, anonyme.entetes(),
				Map.of("cle", cle, "nom", "Émilie", "motDePasse", MOT_DE_PASSE, "email", "autre@example.com")));
		verifier("accepterInvitationAnimateur", 201, accepter(anonyme.entetes(), cle));
	}

	@Test
	void refuseLaCleDesQueSeptJoursSontEcoules() throws Exception {
		var invitant = SessionAnimateurHttp.ouvrir(port, json);
		Instant maintenant = horloge.maintenant();
		var invitation = creerInvitation(invitant.entetes(), "echeance@example.com");
		String cle = invitation.path("cle").asString();
		assertThat(Instant.parse(invitation.path("expireLe").asString())).isEqualTo(maintenant.plus(Duration.ofDays(7)));

		horloge.avancer(Duration.ofDays(7).minusSeconds(1));
		verifier("consulterInvitationAnimateur", 200, consulterInvitation(cle));
		horloge.avancer(Duration.ofSeconds(1));
		verifierRefus("consulterInvitationAnimateur", 404, INVITATION_INVALIDE, consulterInvitation(cle));
		verifierRefus("accepterInvitationAnimateur", 404, INVITATION_INVALIDE, accepter(sessionAnonyme().entetes(), cle));
	}

	@Test
	void effaceLesInvitationsExpireesEtPlafonneCellesEnCoursDuLieu() throws Exception {
		var invitant = SessionAnimateurHttp.ouvrir(port, json);
		String lieuId = consulterSession(invitant.cookie()).path("lieuId").asString();
		creerInvitation(invitant.entetes(), "perimee@example.com");
		horloge.avancer(Duration.ofDays(7));
		creerInvitation(invitant.entetes(), "recente@example.com");
		assertThat(jdbc.sql("select email from invitation_animateur where lieu_id = cast(:lieuId as uuid)")
				.param("lieuId", lieuId).query(String.class).list()).containsExactly("recente@example.com");

		for (int numero = 2; numero <= 20; numero++) {
			creerInvitation(invitant.entetes(), "invite-" + numero + "@example.com");
		}
		verifierRefus("creerInvitationAnimateur", 409, "LIMITE_ATTEINTE",
				poster(INVITATIONS, invitant.entetes(), Map.of("email", "invite-21@example.com")));
		var autreLieu = SessionAnimateurHttp.ouvrir(port, json);
		creerInvitation(autreLieu.entetes(), "invite-21@example.com");

		horloge.avancer(Duration.ofDays(7));
		creerInvitation(invitant.entetes(), "invite-21@example.com");
		assertThat(jdbc.sql("select count(*) from invitation_animateur where lieu_id = cast(:lieuId as uuid)")
				.param("lieuId", lieuId).query(Integer.class).single()).isOne();
	}

	@Test
	void refuseUnEmailPrisEntreLaCreationEtLAcceptationMemeSiLaCasseDiffere() throws Exception {
		var invitant = SessionAnimateurHttp.ouvrir(port, json);
		String cle = creerInvitation(invitant.entetes(), "doublon@example.com").path("cle").asString();
		var inscription = poster("/api/animateurs", sessionAnonyme().entetes(),
				Map.of("email", "DOUBLON@EXAMPLE.com", "nom", "Lou", "motDePasse", MOT_DE_PASSE,
						"lieu", Map.of("nom", "La Verrière", "ville", "Nanterre")));
		verifier("inscrireAnimateur", 201, inscription);
		verifierRefus("creerInvitationAnimateur", 409, EMAIL_DEJA_UTILISE,
				poster(INVITATIONS, invitant.entetes(), Map.of("email", "doublon@example.com")));
		verifierRefus("accepterInvitationAnimateur", 409, EMAIL_DEJA_UTILISE, accepter(sessionAnonyme().entetes(), cle));
		verifierRefus("accepterInvitationAnimateur", 409, EMAIL_DEJA_UTILISE, accepter(sessionAnonyme().entetes(), cle));
	}

	@Test
	void normaliseLEmailInviteEtRefuseUnSurrogateIsoleCommeLInscription() throws Exception {
		var invitant = SessionAnimateurHttp.ouvrir(port, json);
		// Corps écrits à la main : la sérialisation Java remplacerait le surrogate isolé par « ? ».
		String isole = "\\ud800";
		verifierRefus("creerInvitationAnimateur", 422, "DONNEES_INVALIDES", posterBrut(INVITATIONS, invitant.entetes(),
				"{\"email\":\"" + isole + "ines@example.com\"}"));

		String cle = creerInvitation(invitant.entetes(), "Inès.Ñúñez@EXAMPLE.com").path("cle").asString();
		assertThat(verifier("consulterInvitationAnimateur", 200, consulterInvitation(cle)).path("email").asString())
				.isEqualTo("inès.ñúñez@example.com");
		var anonyme = sessionAnonyme();
		String modele = "{\"cle\":\"%s\",\"nom\":\"%s\",\"motDePasse\":\"%s\"}";
		verifierRefus("accepterInvitationAnimateur", 422, "DONNEES_INVALIDES", posterBrut(ACCEPTATION, anonyme.entetes(),
				modele.formatted(cle, "Inès" + isole, MOT_DE_PASSE)));
		verifierRefus("accepterInvitationAnimateur", 422, "DONNEES_INVALIDES", posterBrut(ACCEPTATION, anonyme.entetes(),
				modele.formatted(cle, "Inès", MOT_DE_PASSE + isole)));

		var invite = verifier("accepterInvitationAnimateur", 201, accepter(anonyme.entetes(), cle));
		assertThat(jdbc.sql("select email from animateur where id = cast(:id as uuid)")
				.param("id", invite.path("id").asString()).query(String.class).single())
				.isEqualTo("inès.ñúñez@example.com");
		verifierRefus("creerInvitationAnimateur", 409, EMAIL_DEJA_UTILISE,
				poster(INVITATIONS, invitant.entetes(), Map.of("email", "INÈS.ÑÚÑEZ@example.COM")));
	}

	@Test
	void accepteUneSeuleFoisDeuxRequetesConcurrentes() throws Exception {
		var invitant = SessionAnimateurHttp.ouvrir(port, json);
		String cle = creerInvitation(invitant.entetes(), "simultane@example.com").path("cle").asString();
		var premier = sessionAnonyme();
		var second = sessionAnonyme();
		var depart = new CountDownLatch(1);
		try (var requetes = Executors.newVirtualThreadPerTaskExecutor()) {
			var a = requetes.submit(() -> {
				depart.await();
				return accepter(premier.entetes(), cle);
			});
			var b = requetes.submit(() -> {
				depart.await();
				return accepter(second.entetes(), cle);
			});
			depart.countDown();
			var premiere = a.get(10, TimeUnit.SECONDS);
			var seconde = b.get(10, TimeUnit.SECONDS);
			boolean premiereAcceptee = premiere.getStatusCode().value() == 201;
			verifier("accepterInvitationAnimateur", 201, premiereAcceptee ? premiere : seconde);
			verifierRefus("accepterInvitationAnimateur", 404, INVITATION_INVALIDE, premiereAcceptee ? seconde : premiere);
		}
	}

	private JsonNode creerInvitation(HttpHeaders entetes, String email) throws Exception {
		return verifier("creerInvitationAnimateur", 201, poster(INVITATIONS, entetes, Map.of("email", email)));
	}

	private ResponseEntity<String> accepter(HttpHeaders entetes, String cle) {
		return poster(ACCEPTATION, entetes,
				Map.of("cle", cle, "nom", "Émilie", "motDePasse", MOT_DE_PASSE));
	}

	private ResponseEntity<String> consulterInvitation(String cle) {
		return poster(CONSULTATION, HttpHeaders.EMPTY, Map.of("cle", cle));
	}

	private JsonNode consulterSession(String cookie) throws Exception {
		return verifier("consulterAnimateur", 200, client().get().uri(SESSION).header(HttpHeaders.COOKIE, cookie)
				.retrieve().toEntity(String.class));
	}

	private SessionAnimateurHttp sessionAnonyme() throws Exception {
		var reponse = client().get().uri("/api/csrf").retrieve().toEntity(String.class);
		var csrf = verifier("obtenirCsrf", 200, reponse);
		return new SessionAnimateurHttp(cookie(reponse), csrf.path("token").asString());
	}

	private ResponseEntity<String> poster(String route, HttpHeaders entetes, Map<String, ?> corps) {
		return client().post().uri(route).headers(copie -> copie.addAll(entetes)).body(corps)
				.retrieve().onStatus(HttpStatusCode::isError, (requete, reponse) -> { }).toEntity(String.class);
	}

	private ResponseEntity<String> posterBrut(String route, HttpHeaders entetes, String corps) {
		return client().post().uri(route).headers(copie -> copie.addAll(entetes)).contentType(MediaType.APPLICATION_JSON)
				.body(corps).retrieve().onStatus(HttpStatusCode::isError, (requete, reponse) -> { }).toEntity(String.class);
	}

	private RestClient client() {
		return RestClient.create("http://localhost:" + port);
	}

	private JsonNode verifier(String operation, int statut, ResponseEntity<String> reponse) throws Exception {
		assertThat(reponse.getStatusCode().value()).as(operation).isEqualTo(statut);
		ContratApi.verifier("docs/api/openapi.yaml", operation, reponse);
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
		return json.readTree(reponse.getBody());
	}

	private void verifierRefus(String operation, int statut, String code, ResponseEntity<String> reponse) throws Exception {
		assertThat(verifier(operation, statut, reponse).path("code").asString()).isEqualTo(code);
	}

	private static String cookie(ResponseEntity<?> reponse) {
		return reponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
	}
}
