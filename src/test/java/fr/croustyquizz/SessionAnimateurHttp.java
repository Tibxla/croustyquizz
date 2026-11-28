package fr.croustyquizz;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import tools.jackson.databind.json.JsonMapper;

/** Ouvre une vraie session de test avec son cookie et le CSRF renouvelé. */
public record SessionAnimateurHttp(String cookie, String csrf) {
	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String CSRF = "X-CSRF-TOKEN";

	public static SessionAnimateurHttp ouvrir(int port, JsonMapper json) throws Exception {
		return ouvrir("http://localhost:" + port, json);
	}

	/** Pour un serveur servi sous un chemin de contexte, {@code adresse} le contient. */
	public static SessionAnimateurHttp ouvrir(String adresse, JsonMapper json) throws Exception {
		var client = RestClient.create(adresse);
		var initial = client.get().uri("/api/csrf").retrieve().toEntity(String.class);
		ContratApi.verifier(CONTRAT, "obtenirCsrf", initial);
		var anonyme = new SessionAnimateurHttp(cookie(initial), json.readTree(initial.getBody()).path("token").asString());
		String email = UUID.randomUUID() + "@exemple.test";
		String motDePasse = "un mot de passe de test";
		var inscription = client.post().uri("/api/animateurs")
				.headers(entetes -> entetes.addAll(anonyme.entetes()))
				.body(Map.of("email", email, "motDePasse", motDePasse, "nom", "Camille",
						"lieu", Map.of("nom", "Le Balcon", "ville", "Nanterre")))
				.retrieve().toEntity(String.class);
		ContratApi.verifier(CONTRAT, "inscrireAnimateur", inscription);
		String cookie = cookie(inscription);
		var renouvellement = client.get().uri("/api/csrf").header(HttpHeaders.COOKIE, cookie)
				.retrieve().toEntity(String.class);
		ContratApi.verifier(CONTRAT, "obtenirCsrf", renouvellement);
		return new SessionAnimateurHttp(cookie, json.readTree(renouvellement.getBody()).path("token").asString());
	}

	public HttpHeaders entetes() {
		var entetes = new HttpHeaders();
		entetes.set(HttpHeaders.COOKIE, cookie);
		entetes.set(CSRF, csrf);
		return entetes;
	}

	/** Poignée de main WebSocket de la Console : le cookie de session. */
	public WebSocketHttpHeaders poigneeDeMain() {
		var entetes = new WebSocketHttpHeaders();
		entetes.set(HttpHeaders.COOKIE, cookie);
		return entetes;
	}

	/** CONNECT STOMP de la Console : le jeton CSRF de la session, sans Authorization. */
	public StompHeaders connexionStomp() {
		var entetes = new StompHeaders();
		entetes.set(CSRF, csrf);
		return entetes;
	}

	private static String cookie(ResponseEntity<?> reponse) {
		return reponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
	}

	@Override
	public String toString() {
		return "SessionAnimateurHttp[***]";
	}
}
