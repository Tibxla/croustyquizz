package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.mock.web.MockHttpServletRequest;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.BancDuSocle;

/** Les Manettes de démonstration conservent leur identité sans partager leur canal ni leur cache. */
class FournisseurCommunDemonstrationTest {
	@Test
	void deuxSoireesOntDesManettesStablesEtDesCapturesIndependantes() {
		var banc = new BancDuSocle();
		UUID soiree = UUID.fromString("11111111-1111-4111-8111-111111111111");
		UUID autreSoiree = UUID.fromString("22222222-2222-4222-8222-222222222222");
		UUID joueur = banc.commun.joueurs(soiree).getFirst();
		UUID autreJoueur = banc.commun.joueurs(autreSoiree).getFirst();
		var destinations = new ArrayList<String>();
		var canal = new ExecutorSubscribableChannel();
		canal.subscribe(message -> destinations.add(SimpMessageHeaderAccessor.getDestination(message.getHeaders())));
		var diffuseur = new DiffuseurStomp(new SimpMessagingTemplate(canal), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
				banc.verrous, banc.assembleur, new CaptureSalleFactice());
		var vue = banc.assembleur.capturerJoueur(soiree, joueur);
		diffuseur.envoyerJoueur(soiree, joueur, vue);
		var autreCapture = diffuseur.capturerJoueur(autreSoiree, autreJoueur, null);
		assertThat(autreCapture.revision()).isZero();
		var autreVue = (VueComposee) autreCapture.vue();
		assertThat(autreVue.champs()).containsEntry("joueurId", autreJoueur.toString());
		assertThat(autreVue.champs().get("soiree")).extracting("id").isEqualTo(autreSoiree.toString());
		diffuseur.envoyerJoueur(autreSoiree, autreJoueur, autreVue);
		assertThat(diffuseur.capturerJoueur(soiree, joueur, null)).extracting(VueSynchronisee::revision, VueSynchronisee::vue)
				.containsExactly(1L, vue);
		assertThat(destinations).containsExactly(Canaux.joueur(joueur), Canaux.joueur(autreJoueur)).doesNotHaveDuplicates();
		assertThat(new FournisseurCommunDemonstration(banc.stockage, banc.json).joueurs(soiree)).containsExactly(joueur);
		var requete = new MockHttpServletRequest();
		requete.addHeader("Authorization", "Bearer demo:JOUEUR:" + autreSoiree + ":" + autreJoueur);
		assertThat(new ResolveurDActeurDemonstration().resoudre(requete))
				.isEqualTo(new Acteur(Acteur.Type.JOUEUR, autreJoueur, autreSoiree));
	}
}
