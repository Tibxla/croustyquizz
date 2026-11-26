package fr.croustyquizz.tempsreel;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpAttributesContextHolder;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatDeSoiree;

/**
 * Suit les connexions STOMP des Manettes et publie {@code joueurs-modifies} quand un Joueur d'une
 * Soirée enregistrée passe hors ligne ou revient (#8). Le changement et la publication à la salle et
 * à la Console se font sous le verrou de la Soirée.
 * <p>
 * À la fin de la Soirée, la présence de ses Joueurs est oubliée, et les connexions qui s'ouvrent ou se
 * ferment ensuite ne changent ni ne publient plus rien (#266).
 */
@Component
final class PresenceDesManettes {
	private static final Logger JOURNAL = LoggerFactory.getLogger(PresenceDesManettes.class);
	private static final String SESSION_FERMEE = "croustyquizz.presence.sessionFermee";
	private static final Accepte JOUEURS_MODIFIES = new Accepte(null,
			Set.of(Destination.SALLE, Destination.CONSOLE), "joueurs-modifies");
	private final ConnexionsDesManettes connexions;
	private final VerrousSoiree verrous;
	private final PublicationDeVues vues;
	private final EtatDeSoiree soirees;

	PresenceDesManettes(ConnexionsDesManettes connexions, VerrousSoiree verrous, PublicationDeVues vues,
			EtatDeSoiree soirees) {
		this.connexions = connexions;
		this.verrous = verrous;
		this.vues = vues;
		this.soirees = soirees;
	}

	@EventListener
	void connecter(SessionConnectedEvent evenement) {
		// Spring attache les attributs du WebSocket de cet identifiant au traitement de CONNECTED.
		var session = SimpAttributesContextHolder.currentAttributes();
		if (session.getAttribute(SESSION_FERMEE) == null) {
			changer(evenement.getUser(), joueur -> {
				UUID soireeId = joueur.soireeId();
				if (soirees.estEnregistree(soireeId) && !purgerSiTerminee(soireeId)
						&& session.getAttribute(SESSION_FERMEE) == null
						&& connexions.ouvrir(soireeId, joueur.identifiant(), session.getSessionId())) {
					vues.publier(soireeId, JOUEURS_MODIFIES);
				}
			});
		}
	}

	@EventListener
	void deconnecter(SessionDisconnectEvent evenement) {
		// La marque a la durée de vie du WebSocket, y compris si CONNECTED est encore en cours.
		var attributs = SimpMessageHeaderAccessor.getSessionAttributes(evenement.getMessage().getHeaders());
		if (attributs != null) {
			attributs.put(SESSION_FERMEE, true);
		}
		changer(evenement.getUser(), joueur -> {
			UUID soireeId = joueur.soireeId();
			// Le retrait de la connexion doit aboutir même si la base est indisponible.
			if (connexions.fermer(soireeId, joueur.identifiant(), evenement.getSessionId())
					&& !purgerSiTerminee(soireeId)) {
				vues.publier(soireeId, JOUEURS_MODIFIES);
			}
		});
	}

	/** Reçu sous le verrou, après le commit de {@code soiree-terminee}, comme pour les photos. */
	@EventListener
	void oublierALaFin(CycleDeScene cycle) {
		if (cycle.soireeTerminee()) {
			connexions.oublier(cycle.soireeId());
		}
	}

	/** Appelé sous le verrou de la Soirée, avant toute publication de présence. */
	private boolean purgerSiTerminee(UUID soireeId) {
		if (!soirees.estTerminee(soireeId)) {
			return false;
		}
		connexions.oublier(soireeId);
		return true;
	}

	private void changer(Principal utilisateur, Consumer<Acteur> changement) {
		if (!(utilisateur instanceof IdentiteStomp identite) || identite.acteur().type() != Acteur.Type.JOUEUR) {
			return;
		}
		Acteur joueur = identite.acteur();
		UUID soireeId = joueur.soireeId();
		try {
			verrous.executer(soireeId, () -> {
				changement.accept(joueur);
				return null;
			});
		} catch (RuntimeException erreur) {
			// Le registre conserve les changements appliqués avant l'échec.
			UUID joueurId = joueur.identifiant();
			JOURNAL.error("Présence du Joueur {} non publiée pour la Soirée {}", joueurId, soireeId, erreur);
		}
	}
}
