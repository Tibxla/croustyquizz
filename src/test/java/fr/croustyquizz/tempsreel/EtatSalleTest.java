package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EtatSalleTest {
	@Test
	void laRouteRefuseUnAbonnementInactifPuisCaptureLaVueCompleteDuBuzz() throws Exception {
		var horloge = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
		var verrous = new VerrousSoiree();
		var diffuseur = new DiffuseurStomp(new SimpMessagingTemplate(new ExecutorSubscribableChannel()),
				horloge, verrous, new CaptureSalleFactice());
		var registre = new RegistreAbonnements();
		var mvc = MockMvcBuilders.standaloneSetup(new EtatSalleControleur(diffuseur, registre)).build();
		UUID soiree = UUID.randomUUID();
		UUID abonnement = UUID.randomUUID();
		String route = "/api/test/soirees/" + soiree + "/etat/salle";
		mvc.perform(get(route).param("abonnementId", abonnement.toString()))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ABONNEMENT_INACTIF"))
				.andExpect(header().string("Cache-Control", "no-store"));
		mvc.perform(get(route)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(0))
				.andExpect(jsonPath("$.vue.buzz").isEmpty());
		var broker = mock(SimpleBrokerMessageHandler.class);
		var entetes = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT);
		entetes.setSessionId("connexion");
		registre.beforeHandle(MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders()), null, broker);
		entetes = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
		entetes.setSessionId("connexion");
		entetes.setSubscriptionId(abonnement.toString());
		entetes.setDestination(Canaux.salle(soiree));
		registre.afterMessageHandled(MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders()), null, broker, null);
		var buzz = new BuzzControleur(diffuseur, horloge, verrous);
		buzz.buzzer(soiree, new BuzzControleur.DemandeDeBuzz("Alpha"));
		buzz.buzzer(soiree, new BuzzControleur.DemandeDeBuzz("Bravo"));
		mvc.perform(get(route).param("abonnementId", abonnement.toString())).andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.abonnementId").value(abonnement.toString()))
				.andExpect(jsonPath("$.revision").value(2))
				.andExpect(jsonPath("$.heureServeur").value("1970-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.vue.buzz[0].pseudo").value("Alpha"))
				.andExpect(jsonPath("$.vue.buzz[1].pseudo").value("Bravo"));
		mvc.perform(get(route).param("abonnementId", "invalide")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/test/soirees/" + UUID.randomUUID() + "/etat/salle")
				.param("abonnementId", abonnement.toString())).andExpect(status().isConflict());
	}

	@Test
	void lesVuesDeBuzzSontImmuablesEtLimiteesAux64Derniers() {
		var diffuseur = new DiffuseurFactice();
		var controleur = new BuzzControleur(diffuseur, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), new VerrousSoiree());
		UUID soiree = UUID.randomUUID();
		for (int i = 0; i < 65; i++) {
			controleur.buzzer(soiree, new BuzzControleur.DemandeDeBuzz("Joueur " + i));
		}
		var premiere = (VueSallePrototype) diffuseur.envois().getFirst().evenement();
		var derniere = (VueSallePrototype) diffuseur.envois().getLast().evenement();
		assertThat(premiere.buzz()).isEqualTo(List.of(new BuzzRecu("Joueur 0", Instant.EPOCH)));
		assertThat(derniere.buzz()).hasSize(64);
		assertThat(derniere.buzz().getFirst().pseudo()).isEqualTo("Joueur 1");
		assertThat(derniere.buzz().getLast().pseudo()).isEqualTo("Joueur 64");
	}
}
