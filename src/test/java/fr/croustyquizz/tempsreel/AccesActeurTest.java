package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.RefusDeCommande;

/** Même identité HTTP/STOMP, rôle, portée et propriété des abonnements. */
class AccesActeurTest {
	@Test
	void leResolveurDeProductionRefuseMemeUnJetonDeDemonstration() {
		var requete = new MockHttpServletRequest();
		requete.addHeader("Authorization", "Bearer demo:ANIMATEUR:" + UUID.randomUUID() + ":" + UUID.randomUUID());
		assertThatThrownBy(() -> new ResolveurDActeurProduction().resoudre(requete)).isInstanceOfSatisfying(RefusDeCommande.class,
				erreur -> assertThat(erreur.refus().statutHttp()).isEqualTo(401));
		assertThatThrownBy(() -> new ResolveurDActeurProduction().resoudre(StompHeaderAccessor.create(StompCommand.CONNECT)))
				.isInstanceOf(RefusDeCommande.class);
	}

	@Test
	void leBrokerRattacheLaBarriereAuMemeActeurEtRefuseUneAutreDestination() {
		try (var contexte = new AnnotationConfigWebApplicationContext()) {
			contexte.setServletContext(new MockServletContext());
			contexte.getEnvironment().setActiveProfiles("recette");
			contexte.register(ConfigurationTempsReel.class, RegistreAbonnements.class,
					ControleAccesStomp.class, ResolveurDActeurDemonstration.class);
			contexte.refresh();
			var entrant = contexte.getBean("clientInboundChannel", MessageChannel.class);
			var registre = contexte.getBean(RegistreAbonnements.class);
			Acteur acteur = new Acteur(Acteur.Type.JOUEUR, UUID.randomUUID(), UUID.randomUUID());
			UUID abonnement = UUID.randomUUID();
			String canal = Canaux.joueur(acteur.identifiant());
			entrant.send(trame(StompCommand.CONNECT, acteur, abonnement, canal));
			entrant.send(trame(StompCommand.SUBSCRIBE, acteur, abonnement, canal));
			await().atMost(Duration.ofSeconds(2)).until(() -> registre.estInstalle(abonnement, canal, acteur));
			assertThat(registre.estInstalle(abonnement, canal, new Acteur(Acteur.Type.JOUEUR, UUID.randomUUID(), acteur.soireeId()))).isFalse();
			assertThatThrownBy(() -> entrant.send(trame(StompCommand.SUBSCRIBE, acteur, UUID.randomUUID(), Canaux.console(acteur.soireeId()))))
					.isInstanceOf(MessageDeliveryException.class);
			entrant.send(trame(StompCommand.UNSUBSCRIBE, acteur, abonnement, canal));
			await().atMost(Duration.ofSeconds(2)).until(() -> !registre.estInstalle(abonnement, canal, acteur));
		}
	}
	private Message<byte[]> trame(StompCommand commande, Acteur acteur, UUID abonnement, String destination) {
		var entetes = StompHeaderAccessor.create(commande);
		entetes.setSessionId("connexion"); entetes.setSubscriptionId(abonnement.toString()); entetes.setDestination(destination);
		if (commande == StompCommand.CONNECT) {
			entetes.setNativeHeader("Authorization", "Bearer demo:" + acteur.type() + ":" + acteur.soireeId() + ":" + acteur.identifiant());
		} else { entetes.setUser(new IdentiteStomp(acteur)); }
		return MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders());
	}
}
