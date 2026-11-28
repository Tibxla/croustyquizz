package fr.croustyquizz.tempsreel;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

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
			changer(evenement.getUser(), true, joueur -> session.getAttribute(SESSION_FERMEE) == null
					&& connexions.ouvrir(joueur, session.getSessionId()));
		}
	}

	@EventListener
	void deconnecter(SessionDisconnectEvent evenement) {
		// La marque a la durée de vie du WebSocket, y compris si CONNECTED est encore en cours.
		var attributs = SimpMessageHeaderAccessor.getSessionAttributes(evenement.getMessage().getHeaders());
		if (attributs != null) {
			attributs.put(SESSION_FERMEE, true);
		}
		changer(evenement.getUser(), false, joueur -> connexions.fermer(joueur, evenement.getSessionId()));
	}

	private void changer(Principal utilisateur, boolean ouverture, Predicate<UUID> changement) {
		if (!(utilisateur instanceof IdentiteStomp identite) || identite.acteur().type() != Acteur.Type.JOUEUR) {
			return;
		}
		UUID joueurId = identite.acteur().identifiant();
		UUID soireeId = identite.acteur().soireeId();
		try {
			if (ouverture && !soirees.estEnregistree(soireeId)) {
				return; // Les Joueurs de démonstration restent en ligne.
			}
			verrous.executer(soireeId, () -> {
				if (changement.test(joueurId)) {
					vues.publier(soireeId, JOUEURS_MODIFIES);
				}
				return null;
			});
		} catch (RuntimeException erreur) {
			// Si l'assemblage échoue, le registre conserve le changement de présence.
			JOURNAL.error("Présence du Joueur {} non publiée pour la Soirée {}", joueurId, soireeId, erreur);
		}
	}
}
