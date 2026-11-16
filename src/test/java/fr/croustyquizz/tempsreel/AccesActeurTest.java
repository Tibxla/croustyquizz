package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
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
	@ParameterizedTest
	@CsvSource({"dev, console", "dev, manette", "recette, console", "recette, manette", "prod, console", "prod, manette"})
	void unAbonnementPriveExigeUneIdentiteDansChaqueProfil(String profil, String vue) {
		var environnement = new MockEnvironment();
		environnement.setActiveProfiles(profil);
		var controle = new ControleAccesStomp(new ResolveurDActeurDemonstration(), environnement);
		if (!profil.equals("prod")) {
			assertThat(controle.preSend(anonyme(StompCommand.CONNECT, null), null)).isNotNull();
		}
		String destination = vue.equals("console") ? Canaux.console(UUID.randomUUID()) : Canaux.joueur(UUID.randomUUID());
		assertThatThrownBy(() -> controle.preSend(anonyme(StompCommand.SUBSCRIBE, destination), null))
				.isInstanceOf(MessageDeliveryException.class);
	}

	@ParameterizedTest
	@CsvSource({"dev, true", "recette, true", "prod, false"})
	void seulLeCanalSalleDuPrototypeResteOuvertSansIdentite(String profil, boolean ouvert) {
		var environnement = new MockEnvironment();
		environnement.setActiveProfiles(profil);
		var controle = new ControleAccesStomp(new ResolveurDActeurDemonstration(), environnement);
		var salle = anonyme(StompCommand.SUBSCRIBE, Canaux.salle(UUID.randomUUID()));
		if (ouvert) { assertThat(controle.preSend(salle, null)).isNotNull(); }
		else { assertThatThrownBy(() -> controle.preSend(salle, null)).isInstanceOf(MessageDeliveryException.class); }
		for (String destination : new String[] {"/topic/inconnu", "/topic/soirees/*/salle", "/topic/soirees/invalide/salle"}) {
			assertThatThrownBy(() -> controle.preSend(anonyme(StompCommand.SUBSCRIBE, destination), null))
					.isInstanceOf(MessageDeliveryException.class);
		}
	}

	private Message<byte[]> anonyme(StompCommand commande, String destination) {
		var entetes = StompHeaderAccessor.create(commande);
		entetes.setSessionId("connexion-anonyme");
		entetes.setDestination(destination);
		return MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders());
	}

	@ParameterizedTest
	@ValueSource(strings = {"dev", "recette", "prod"})
	void uneIdentiteAutoriseeResteLimiteeASonRoleEtSaPortee(String profil) {
		var environnement = new MockEnvironment();
		environnement.setActiveProfiles(profil);
		var controle = new ControleAccesStomp(new ResolveurDActeurDemonstration(), environnement);
		UUID soiree = UUID.randomUUID();
		for (Acteur.Type type : Acteur.Type.values()) {
			var acteur = new Acteur(type, FournisseurCommunDemonstration.joueurId(soiree), soiree);
			assertThat(controle.preSend(trame(StompCommand.SUBSCRIBE, acteur, UUID.randomUUID(), Canaux.salle(soiree)), null)).isNotNull();
			String canalPrive = type == Acteur.Type.ANIMATEUR ? Canaux.console(soiree) : Canaux.joueur(acteur.identifiant());
			if (type == Acteur.Type.ECRAN) {
				assertThatThrownBy(() -> controle.preSend(trame(StompCommand.SUBSCRIBE, acteur, UUID.randomUUID(), canalPrive), null))
						.isInstanceOf(MessageDeliveryException.class);
			} else {
				assertThat(controle.preSend(trame(StompCommand.SUBSCRIBE, acteur, UUID.randomUUID(), canalPrive), null)).isNotNull();
			}
			for (String interdit : new String[] {Canaux.console(UUID.randomUUID()), Canaux.joueur(UUID.randomUUID()), Canaux.salle(UUID.randomUUID())}) {
				assertThatThrownBy(() -> controle.preSend(trame(StompCommand.SUBSCRIBE, acteur, UUID.randomUUID(), interdit), null))
						.isInstanceOf(MessageDeliveryException.class);
			}
		}
	}

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
			UUID soiree = UUID.randomUUID();
			Acteur acteur = new Acteur(Acteur.Type.JOUEUR, FournisseurCommunDemonstration.joueurId(soiree), soiree);
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
