package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.CloseStatus;

class RegistreAbonnementsTest {
	private final RegistreAbonnements registre = new RegistreAbonnements();
	private final MessageHandler broker = mock(SimpleBrokerMessageHandler.class);
	private final UUID abonnement = UUID.randomUUID();
	private final String salle = Canaux.salle(UUID.randomUUID());

	@Test
	void seulLeTraitementReussiDuBrokerInstalleLAbonnementSurSaDestination() {
		connecter("connexion");
		var message = message(SimpMessageType.SUBSCRIBE, "connexion");
		registre.beforeHandle(message, null, broker);
		assertThat(registre.estInstalle(abonnement, salle)).isFalse();
		registre.afterMessageHandled(message, null, mock(MessageHandler.class), null);
		assertThat(registre.estInstalle(abonnement, salle)).isFalse();
		registre.afterMessageHandled(message, null, broker, new IllegalStateException("Refus"));
		assertThat(registre.estInstalle(abonnement, salle)).isFalse();
		registre.afterMessageHandled(message, null, broker, null);
		assertThat(registre.estInstalle(abonnement, salle)).isTrue();
		assertThat(registre.estInstalle(abonnement, Canaux.console(UUID.randomUUID()))).isFalse();
	}

	@Test
	void unDesabonnementNeRetireQueCeluiDeSaConnexionEtUnUuidAmbiguEstInactif() {
		installer("premiere");
		installer("seconde");
		assertThat(registre.estInstalle(abonnement, salle)).isFalse();
		registre.afterMessageHandled(message(SimpMessageType.UNSUBSCRIBE, "premiere"), null, broker, null);
		assertThat(registre.estInstalle(abonnement, salle)).isTrue();
		registre.afterMessageHandled(message(SimpMessageType.DISCONNECT, "seconde"), null, broker, null);
		assertThat(registre.estInstalle(abonnement, salle)).isFalse();
	}

	@Test
	void laFermetureRetireToutEtUnSubscribeRetardeNeRessuscitePasLaConnexion() {
		installer("connexion");
		var fermeture = message(SimpMessageType.DISCONNECT, "connexion");
		registre.deconnecter(new SessionDisconnectEvent(this, fermeture, "connexion", CloseStatus.NORMAL));
		registre.deconnecter(new SessionDisconnectEvent(this, fermeture, "connexion", CloseStatus.NORMAL));
		registre.afterMessageHandled(message(SimpMessageType.SUBSCRIBE, "connexion"), null, broker, null);
		assertThat(registre.estInstalle(abonnement, salle)).isFalse();
	}

	private void connecter(String connexion) {
		registre.beforeHandle(message(SimpMessageType.CONNECT, connexion), null, broker);
	}

	private void installer(String connexion) {
		connecter(connexion);
		registre.afterMessageHandled(message(SimpMessageType.SUBSCRIBE, connexion), null, broker, null);
	}

	private Message<byte[]> message(SimpMessageType type, String connexion) {
		var entetes = SimpMessageHeaderAccessor.create(type);
		entetes.setSessionId(connexion);
		entetes.setSubscriptionId(abonnement.toString());
		entetes.setDestination(salle);
		return MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders());
	}
}
