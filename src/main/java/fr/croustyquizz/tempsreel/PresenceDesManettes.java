package fr.croustyquizz.tempsreel;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
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
 * Soirée enregistrée passe hors ligne ou revient (#8). Le changement et sa publication se font sous le
 * verrou de la Soirée, comme une arrivée : une capture ne voit jamais l'un sans l'autre.
 */
@Component
final class PresenceDesManettes {
	private static final Logger JOURNAL = LoggerFactory.getLogger(PresenceDesManettes.class);
	private static final Accepte JOUEURS_MODIFIES = new Accepte(null,
			Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES), "joueurs-modifies");
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
		String connexion = SimpMessageHeaderAccessor.getSessionId(evenement.getMessage().getHeaders());
		changer(evenement.getUser(), joueur -> connexions.ouvrir(joueur, connexion));
	}

	@EventListener
	void deconnecter(SessionDisconnectEvent evenement) {
		changer(evenement.getUser(), joueur -> connexions.fermer(joueur, evenement.getSessionId()));
	}

	private void changer(Principal utilisateur, Predicate<UUID> changement) {
		if (!(utilisateur instanceof IdentiteStomp identite) || identite.acteur().type() != Acteur.Type.JOUEUR) {
			return;
		}
		Acteur joueur = identite.acteur();
		UUID soireeId = joueur.soireeId();
		try {
			if (!soirees.estEnregistree(soireeId)) {
				return; // Les Joueurs de démonstration sont toujours affichés connectés.
			}
			verrous.executer(soireeId, () -> {
				if (changement.test(joueur.identifiant())) {
					vues.publier(soireeId, JOUEURS_MODIFIES);
				}
				return null;
			});
		} catch (RuntimeException erreur) {
			// La connexion est déjà ouverte ou fermée : un échec de publication ne la remet pas en cause.
			JOURNAL.error("Présence du Joueur {} non publiée pour la Soirée {}", joueur.identifiant(), soireeId, erreur);
		}
	}
}
