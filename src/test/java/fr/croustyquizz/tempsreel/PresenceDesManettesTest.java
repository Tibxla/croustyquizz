package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.messaging.simp.SimpAttributes;
import org.springframework.messaging.simp.SimpAttributesContextHolder;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatDeSoiree;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class PresenceDesManettesTest {

	private final UUID soiree = UUID.randomUUID();
	private final UUID joueur = UUID.randomUUID();
	private final UUID sam = UUID.randomUUID();
	private final ConnexionsDesManettes connexions = new ConnexionsDesManettes();
	private final VerrousSoiree verrous = new VerrousSoiree();
	private final EtatDeSoiree soirees = mock(EtatDeSoiree.class);
	private boolean baseIndisponible;
	private final FournisseurPartieCommune commun = new FournisseurPartieCommune() {
		@Override
		public Map<String, Object> fournir(UUID soireeId, Destination destination) {
			if (baseIndisponible) {
				throw new CannotGetJdbcConnectionException("PostgreSQL indisponible pendant la lecture de la vue");
			}
			return Map.of("joueurs", List.of(Map.of("id", joueur, "pseudo", "Léa", "connecte", connexions.estConnecte(joueur))));
		}

		@Override
		public List<UUID> joueurs(UUID soireeId) {
			return List.of(joueur);
		}
	};
	private final AssembleurDeVues assembleur = new AssembleurDeVues(commun, List.of(), verrous, JsonMapper.shared());
	private final List<String> destinations = new ArrayList<>();
	private final ExecutorSubscribableChannel canal = new ExecutorSubscribableChannel();
	private final DiffuseurStomp diffuseur = new DiffuseurStomp(new SimpMessagingTemplate(canal),
			Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), verrous, assembleur, assembleur::capturerSalle);
	private final PublicationDeVues vues = new PublicationDeVues(assembleur, commun, new PublicationApresCommit(),
			diffuseur, evenement -> { });
	private final PresenceDesManettes presence = new PresenceDesManettes(connexions, verrous, vues, soirees);

	PresenceDesManettesTest() {
		when(soirees.estEnregistree(soiree)).thenReturn(true);
		canal.subscribe(message -> destinations.add(SimpMessageHeaderAccessor.getDestination(message.getHeaders())));
	}

	@Test
	void avantLaFinLaFermetureDeLaDerniereConnexionPublieLesJoueurs() {
		var onglet = new Onglet("a");
		onglet.ouvrir();
		onglet.fermer();

		assertThat(destinations).hasSize(2);
		assertThat(diffuseur.capturerSalle(soiree, null).vue().type()).isEqualTo("joueurs-modifies");
		assertThat(connexions.estConnecte(joueur)).isFalse();
	}

	@Test
	void laFinDeLaSoireeOublieLaPresenceDeSesJoueurs() {
		var onglet = new Onglet("a");
		onglet.ouvrir();
		onglet.fermer();
		new Onglet("b", sam).ouvrir();

		presence.oublierALaFin(new CycleDeScene(soiree, false, true));

		assertThat(connexions.estConnecte(joueur)).isTrue();
		assertThat(connexions.suitLaSoiree(soiree)).isFalse();
	}

	@Test
	void unCycleDeSceneSansFinGardeLaPresence() {
		var onglet = new Onglet("a");
		onglet.ouvrir();
		onglet.fermer();

		presence.oublierALaFin(new CycleDeScene(soiree, true, false));
		presence.oublierALaFin(new CycleDeScene(soiree, false, false));

		assertThat(connexions.estConnecte(joueur)).isFalse();
		assertThat(connexions.suitLaSoiree(soiree)).isTrue();
	}

	@Test
	void apresLaFinUneManetteQuiSeFermeOuRevientNePublieRienEtNeLaisseRienEnMemoire() {
		var ongletSam = new Onglet("b", sam);
		ongletSam.ouvrir();
		when(soirees.estTerminee(soiree)).thenReturn(true);

		ongletSam.fermer();
		var onglet = new Onglet("a");
		onglet.ouvrir();
		assertThat(connexions.suitLaSoiree(soiree)).isFalse();
		onglet.fermer();

		assertThat(destinations).isEmpty();
		assertThat(connexions.suitLaSoiree(soiree)).isFalse();
		assertThat(connexions.estConnecte(joueur)).isTrue();
		assertThat(connexions.estConnecte(sam)).isTrue();
	}

	@Test
	void uneOuvertureRecueApresSaFermetureNeGardePasLeJoueurEnLigne() {
		var premierOnglet = new Onglet("premier");
		var dernierOnglet = new Onglet("dernier");
		var ongletFermeAvantConnected = new Onglet("ferme");
		premierOnglet.ouvrir();
		dernierOnglet.ouvrir();
		ongletFermeAvantConnected.fermer();
		ongletFermeAvantConnected.ouvrir();
		var ongletFermePendantConnected = new Onglet("ferme-pendant-connected");
		when(soirees.estEnregistree(soiree)).thenAnswer(lecture -> {
			ongletFermePendantConnected.fermer();
			return true;
		});
		ongletFermePendantConnected.ouvrir();

		premierOnglet.fermer();
		assertThat(presenceDans(diffuseur.capturerSalle(soiree, null))).isTrue();
		dernierOnglet.fermer();
		assertThat(presenceDans(diffuseur.capturerSalle(soiree, null))).isFalse();
	}

	@Test
	void laDerniereFermetureMetLeJoueurHorsLigneMemeSiPostgreSQLNeRepondPas(CapturedOutput journal) {
		var onglet = new Onglet("dernier");
		onglet.ouvrir();
		when(soirees.estEnregistree(soiree)).thenThrow(new CannotGetJdbcConnectionException("PostgreSQL indisponible"));
		baseIndisponible = true;

		onglet.fermer();
		baseIndisponible = false;

		assertThat(presenceDans(diffuseur.capturerSalle(soiree, null))).isFalse();
		assertThat(journal.getAll()).contains("Présence du Joueur " + joueur + " non publiée pour la Soirée " + soiree,
				"PostgreSQL indisponible pendant la lecture de la vue");
	}

	@Test
	void laDerniereFermetureRetireLaConnexionMemeSiLaLectureDeLEtatEchoue() {
		var onglet = new Onglet("dernier");
		onglet.ouvrir();
		when(soirees.estTerminee(soiree)).thenThrow(new CannotGetJdbcConnectionException("PostgreSQL indisponible"));

		onglet.fermer();

		assertThat(connexions.estConnecte(joueur)).isFalse();
		assertThat(destinations).isEmpty();
	}

	@Test
	void laPresenceNeSePublieQuALaSalleEtALaConsole() {
		var onglet = new Onglet("dernier");
		onglet.ouvrir();
		destinations.clear();

		onglet.fermer();

		assertThat(destinations).containsExactlyInAnyOrder(Canaux.salle(soiree), Canaux.console(soiree));
		assertThat(presenceDans(diffuseur.capturerSalle(soiree, null))).isFalse();
		assertThat(presenceDans(diffuseur.capturerConsole(soiree, null))).isFalse();

		new Onglet("reconnecte").ouvrir();
		assertThat(destinations).containsExactlyInAnyOrder(
				Canaux.salle(soiree), Canaux.console(soiree), Canaux.salle(soiree), Canaux.console(soiree));
	}

	private static boolean presenceDans(VueSynchronisee capture) {
		return JsonMapper.shared().valueToTree(capture.vue()).at("/joueurs/0/connecte").asBoolean();
	}

	private final class Onglet {
		private final String session;
		private final IdentiteStomp identite;
		private final Map<String, Object> attributs = new ConcurrentHashMap<>();

		private Onglet(String session) {
			this(session, joueur);
		}

		private Onglet(String session, UUID joueurId) {
			this.session = session;
			this.identite = new IdentiteStomp(new Acteur(Acteur.Type.JOUEUR, joueurId, soiree));
		}

		private void ouvrir() {
			var entetes = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT_ACK);
			entetes.setSessionId(session);
			var message = MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders());
			// Spring fournit les attributs du WebSocket au listener CONNECTED via ce contexte.
			SimpAttributesContextHolder.setAttributes(new SimpAttributes(session, attributs));
			try {
				presence.connecter(new SessionConnectedEvent(this, message, identite));
			} finally {
				SimpAttributesContextHolder.resetAttributes();
			}
		}

		private void fermer() {
			var entetes = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT);
			entetes.setSessionId(session);
			entetes.setSessionAttributes(attributs);
			var message = MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders());
			presence.deconnecter(new SessionDisconnectEvent(this, message, session, CloseStatus.NORMAL, identite));
		}
	}
}
