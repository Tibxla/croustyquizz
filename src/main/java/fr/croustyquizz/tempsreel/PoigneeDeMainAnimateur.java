package fr.croustyquizz.tempsreel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.HandshakeInterceptor;

import fr.croustyquizz.animateur.AnimateurConnecte;
import fr.croustyquizz.animateur.SessionAnimateur;

@Component
final class PoigneeDeMainAnimateur implements HandshakeInterceptor {
	private static final String SESSION = "sessionAnimateur";
	private static final String CSRF = "csrfAnimateur";
	private static final String CONNEXION = "connexionAnimateur";
	private static final String ENTETE_CSRF = "X-CSRF-TOKEN";

	private final CsrfTokenRepository jetons;

	PoigneeDeMainAnimateur(CsrfTokenRepository jetons) {
		this.jetons = jetons;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest requete, ServerHttpResponse reponse,
			WebSocketHandler gestionnaire, Map<String, Object> attributs) {
		if (requete instanceof ServletServerHttpRequest servlet) {
			var http = servlet.getServletRequest();
			var session = SessionAnimateur.de(http.getSession(false));
			if (session != null) {
				attributs.put(SESSION, session);
				CsrfToken csrf = jetons.loadToken(http);
				if (csrf != null) {
					attributs.put(CSRF, csrf);
				}
			}
		}
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest requete, ServerHttpResponse reponse,
			WebSocketHandler gestionnaire, Exception erreur) { }

	/**
	 * Rend l'Animateur de la session du handshake si le CONNECT porte son jeton CSRF. Sans cet en-tête, la
	 * connexion ne revendique pas la session : un Écran de salle ouvert dans un navigateur connecté reste
	 * anonyme (dev et recette) ou passe par le résolveur, qui le refuse en production.
	 */
	@SuppressWarnings("PMD.CloseResource") // La session HTTP conserve le WebSocket jusqu’à sa fermeture.
	static AnimateurConnecte connecter(SimpMessageHeaderAccessor entetes) {
		var attributs = entetes.getSessionAttributes();
		if (attributs == null || !(attributs.get(SESSION) instanceof SessionAnimateur session)) {
			return null;
		}
		CsrfToken jeton = attributs.get(CSRF) instanceof CsrfToken csrf ? csrf : null;
		String masque = entetes.getFirstNativeHeader(jeton == null ? ENTETE_CSRF : jeton.getHeaderName());
		if (masque == null) {
			return null;
		}
		if (jeton == null || !csrfValide(jeton, masque)) {
			throw new MessageDeliveryException("Le jeton CSRF est absent ou invalide.");
		}
		if (!(attributs.get(CONNEXION) instanceof WebSocketSession connexion) || !session.ajouter(connexion)) {
			throw new MessageDeliveryException("La session Animateur n’est plus active ou est déjà utilisée sur cette connexion.");
		}
		return session.identite();
	}

	@SuppressWarnings("PMD.CloseResource") // La fermeture avec statut signale le refus au client.
	static void refuser(SimpMessageHeaderAccessor entetes, String raison) {
		var attributs = entetes.getSessionAttributes();
		if (attributs != null && attributs.get(CONNEXION) instanceof WebSocketSession connexion) {
			try {
				connexion.close(CloseStatus.POLICY_VIOLATION.withReason(raison));
			} catch (IOException erreur) {
				throw new MessagingException("Impossible de fermer la connexion refusée.", erreur);
			}
		}
	}

	private static boolean csrfValide(CsrfToken jeton, String masque) {
		byte[] attendu = jeton.getToken().getBytes(StandardCharsets.UTF_8);
		byte[] donnees;
		try {
			donnees = Base64.getUrlDecoder().decode(masque);
		} catch (IllegalArgumentException invalide) {
			return false;
		}
		if (donnees.length != 2 * attendu.length) {
			return false;
		}
		// Le jeton HTTP de Spring contient le masque aléatoire suivi du jeton masqué par XOR.
		byte[] recu = new byte[attendu.length];
		for (int index = 0; index < recu.length; index++) {
			recu[index] = (byte) (donnees[index] ^ donnees[index + recu.length]);
		}
		return MessageDigest.isEqual(attendu, recu);
	}

	WebSocketHandler decorer(WebSocketHandler gestionnaire) {
		return new WebSocketHandlerDecorator(gestionnaire) {
			@Override
			public void afterConnectionEstablished(WebSocketSession connexion) throws Exception {
				if (connexion.getAttributes().containsKey(SESSION)) {
					connexion.getAttributes().put(CONNEXION, connexion);
				}
				super.afterConnectionEstablished(connexion);
			}

			@Override
			public void afterConnectionClosed(WebSocketSession connexion, CloseStatus statut) throws Exception {
				if (connexion.getAttributes().get(SESSION) instanceof SessionAnimateur session) {
					session.retirer(connexion);
				}
				super.afterConnectionClosed(connexion, statut);
			}
		};
	}
}
