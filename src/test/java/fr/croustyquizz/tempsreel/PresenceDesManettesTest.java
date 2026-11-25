package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.EtatDeSoiree;

/** Présence des Manettes autour de la fin de la Soirée (#266), sans serveur ni base. */
class PresenceDesManettesTest {

	private final UUID soiree = UUID.randomUUID();
	private final UUID lea = UUID.randomUUID();
	private final UUID sam = UUID.randomUUID();
	private final ConnexionsDesManettes connexions = new ConnexionsDesManettes();
	private final PublicationDeVues vues = mock(PublicationDeVues.class);
	private final Soirees soirees = new Soirees();
	private final PresenceDesManettes presence = new PresenceDesManettes(connexions, new VerrousSoiree(), vues, soirees);

	@Test
	void avantLaFinLaFermetureDeLaDerniereConnexionPublieLesJoueurs() {
		connecter(lea, "a");
		deconnecter(lea, "a");

		verify(vues).publier(eq(soiree), argThat((Accepte accepte) -> "joueurs-modifies".equals(accepte.typeEvenement())));
		assertThat(connexions.estConnecte(lea)).isFalse();
	}

	@Test
	void laFinDeLaSoireeOublieLaPresenceDeSesJoueurs() {
		connecter(lea, "a");
		deconnecter(lea, "a");
		connecter(sam, "b");

		presence.oublierALaFin(new CycleDeScene(soiree, false, true));

		assertThat(connexions.estConnecte(lea)).isTrue();
		assertThat(connexions.suitLaSoiree(soiree)).isFalse();
	}

	@Test
	void unCycleDeSceneSansFinGardeLaPresence() {
		connecter(lea, "a");
		deconnecter(lea, "a");

		presence.oublierALaFin(new CycleDeScene(soiree, true, false));
		presence.oublierALaFin(new CycleDeScene(soiree, false, false));

		assertThat(connexions.estConnecte(lea)).isFalse();
		assertThat(connexions.suitLaSoiree(soiree)).isTrue();
	}

	@Test
	void apresLaFinUneManetteQuiSeFermeOuRevientNePublieRienEtNeLaisseRienEnMemoire() {
		connecter(sam, "b");
		soirees.terminees.add(soiree);

		deconnecter(sam, "b");
		connecter(lea, "a");
		deconnecter(lea, "a");

		verify(vues, never()).publier(any(), any());
		assertThat(connexions.suitLaSoiree(soiree)).isFalse();
		assertThat(connexions.estConnecte(lea)).isTrue();
		assertThat(connexions.estConnecte(sam)).isTrue();
	}

	private void connecter(UUID joueur, String connexion) {
		presence.connecter(new SessionConnectedEvent(this, message(connexion), identite(joueur)));
	}

	private void deconnecter(UUID joueur, String connexion) {
		presence.deconnecter(new SessionDisconnectEvent(this, message(connexion), connexion, CloseStatus.NORMAL,
				identite(joueur)));
	}

	private IdentiteStomp identite(UUID joueur) {
		return new IdentiteStomp(new Acteur(Acteur.Type.JOUEUR, joueur, soiree));
	}

	private static Message<byte[]> message(String connexion) {
		var entetes = SimpMessageHeaderAccessor.create();
		entetes.setSessionId(connexion);
		return MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders());
	}

	/** Toutes les Soirées sont enregistrées ; seules celles de {@code terminees} sont finies. */
	private static final class Soirees implements EtatDeSoiree {
		private final Set<UUID> terminees = new HashSet<>();

		@Override public boolean existe(UUID soireeId) { return true; }
		@Override public boolean estEnregistree(UUID soireeId) { return true; }
		@Override public boolean estEnCours(UUID soireeId) { return !terminees.contains(soireeId); }
		@Override public boolean estTerminee(UUID soireeId) { return terminees.contains(soireeId); }
	}
}
