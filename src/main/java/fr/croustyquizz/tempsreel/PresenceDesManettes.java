package fr.croustyquizz.tempsreel;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

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
 * <p>
 * À la fin de la Soirée, la présence de ses Joueurs est oubliée, et les connexions qui s'ouvrent ou se
 * ferment ensuite ne changent ni ne publient plus rien (#266).
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
		changer(evenement.getUser(), (soiree, joueur) -> connexions.ouvrir(soiree, joueur, connexion));
	}

	@EventListener
	void deconnecter(SessionDisconnectEvent evenement) {
		changer(evenement.getUser(), (soiree, joueur) -> connexions.fermer(soiree, joueur, evenement.getSessionId()));
	}

	/** Reçu sous le verrou, après le commit de {@code soiree-terminee}, comme pour les photos. */
	@EventListener
	void oublierALaFin(CycleDeScene cycle) {
		if (cycle.soireeTerminee()) {
			connexions.oublier(cycle.soireeId());
		}
	}

	private void changer(Principal utilisateur, BiPredicate<UUID, UUID> changement) {
		if (!(utilisateur instanceof IdentiteStomp identite) || identite.acteur().type() != Acteur.Type.JOUEUR) {
			return;
		}
		UUID joueurId = identite.acteur().identifiant();
		UUID soireeId = identite.acteur().soireeId();
		try {
			if (!soirees.estEnregistree(soireeId)) {
				return; // Les Joueurs de démonstration sont toujours affichés connectés.
			}
			verrous.executer(soireeId, () -> {
				// Lu sous le verrou, avant tout changement : une Manette encore ouverte après la fin ne recrée rien.
				if (soirees.estTerminee(soireeId)) {
					connexions.oublier(soireeId);
				} else if (changement.test(soireeId, joueurId)) {
					vues.publier(soireeId, JOUEURS_MODIFIES);
				}
				return null;
			});
		} catch (RuntimeException erreur) {
			// La connexion est déjà ouverte ou fermée : un échec de publication ne la remet pas en cause.
			JOURNAL.error("Présence du Joueur {} non publiée pour la Soirée {}", joueurId, soireeId, erreur);
		}
	}
}
