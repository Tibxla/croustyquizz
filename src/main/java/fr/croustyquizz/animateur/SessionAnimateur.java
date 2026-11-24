package fr.croustyquizz.animateur;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/** Identité et connexions STOMP attachées à la durée de vie de la session HTTP. */
public final class SessionAnimateur implements HttpSessionBindingListener {
	private static final String ATTRIBUT = SessionAnimateur.class.getName();

	private final AnimateurConnecte animateur;
	private final Set<WebSocketSession> connexions = new HashSet<>();
	private boolean fermee;

	SessionAnimateur(AnimateurConnecte identite) {
		this.animateur = identite;
	}

	public static SessionAnimateur de(HttpSession session) {
		if (session == null) {
			return null;
		}
		try {
			return (SessionAnimateur) session.getAttribute(ATTRIBUT);
		} catch (IllegalStateException invalidee) {
			return null;
		}
	}

	void attacher(HttpSession session) {
		session.setAttribute(ATTRIBUT, this);
	}

	public AnimateurConnecte identite() {
		return animateur;
	}

	public synchronized boolean ajouter(WebSocketSession connexion) {
		return !fermee && connexions.add(connexion);
	}

	public synchronized void retirer(WebSocketSession connexion) {
		connexions.remove(connexion);
	}

	@Override
	@SuppressWarnings("PMD.CloseResource") // La fermeture indique le statut WebSocket à chaque Console.
	public void valueUnbound(HttpSessionBindingEvent evenement) {
		List<WebSocketSession> aFermer;
		synchronized (this) {
			fermee = true;
			aFermer = List.copyOf(connexions);
			connexions.clear();
		}
		for (WebSocketSession connexion : aFermer) {
			try {
				connexion.close(CloseStatus.POLICY_VIOLATION);
			} catch (IOException erreur) {
				LogFactory.getLog(SessionAnimateur.class).warn("Impossible de fermer une connexion de la Console.", erreur);
			}
		}
	}
}
