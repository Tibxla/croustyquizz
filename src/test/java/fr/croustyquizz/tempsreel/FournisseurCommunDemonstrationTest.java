package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.mock.web.MockHttpServletRequest;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.BancDuSocle;
import fr.croustyquizz.manche.Destination;

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
		var joueurs = new FournisseurCommunDemonstration(banc.stockage, banc.json).joueurs(soiree);
		assertThat(joueurs).containsExactly(
				UUID.fromString("7a9401fe-ee99-3882-8c7b-6bf81fea0f7c"),
				UUID.fromString("78458e17-7742-37c6-a037-6d3b7a0e4726"),
				UUID.fromString("200792f5-8574-3627-bf57-18f4393749b1"),
				UUID.fromString("6a611f5f-8dd3-30d0-a6d7-020d8f301941"));
		assertThat(joueurs.getFirst()).isEqualTo(joueur);
		assertThat(banc.commun.joueurs(autreSoiree)).hasSize(4).doesNotHaveDuplicates()
				.doesNotContainAnyElementsOf(joueurs);
		var requete = new MockHttpServletRequest();
		requete.addHeader("Authorization", "Bearer demo:JOUEUR:" + soiree + ":7a9401fe-ee99-3882-8c7b-6bf81fea0f7c");
		assertThat(new ResolveurDActeurDemonstration().resoudre(requete))
				.isEqualTo(new Acteur(Acteur.Type.JOUEUR, joueur, soiree));
		for (int rang = 1; rang <= 4; rang++) {
			assertThat(FournisseurCommunDemonstration.joueurId(soiree, rang)).isEqualTo(joueurs.get(rang - 1));
		}
		assertThat(FournisseurCommunDemonstration.joueurId(soiree)).isEqualTo(joueur);
	}

	@Test
	void lesRangsHorsDeLaDemonstrationSontRefuses() {
		UUID soiree = UUID.randomUUID();
		assertThatIllegalArgumentException().isThrownBy(() -> FournisseurCommunDemonstration.joueurId(soiree, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> FournisseurCommunDemonstration.joueurId(soiree, 5));
	}

	@Test
	void lesTroisDestinationsVoientLesQuatreJoueursDansLOrdre() {
		var banc = new BancDuSocle();
		UUID soiree = UUID.randomUUID();
		var joueurs = banc.commun.joueurs(soiree);
		assertThat(joueurs).hasSize(4);
		var attendus = List.of(
				Map.of("id", joueurs.get(0), "pseudo", "Joueur de démonstration 1", "connecte", true),
				Map.of("id", joueurs.get(1), "pseudo", "Joueur de démonstration 2", "connecte", true),
				Map.of("id", joueurs.get(2), "pseudo", "Joueur de démonstration 3", "connecte", true),
				Map.of("id", joueurs.get(3), "pseudo", "Joueur de démonstration 4", "connecte", true));
		assertThat(banc.commun.fournir(soiree, Destination.SALLE)).containsEntry("joueurs", attendus);
		assertThat(banc.commun.fournir(soiree, Destination.CONSOLE)).containsEntry("joueurs", attendus);
		for (UUID joueur : joueurs) {
			assertThat(banc.commun.fournir(soiree, Destination.joueur(joueur)))
					.containsEntry("joueurs", attendus)
					.containsEntry("joueurId", joueur)
					.containsEntry("eligiblePourMancheActive", false)
					.containsEntry("derniereReponse", null)
					.containsEntry("resultatPersonnel", null);
		}
	}
}
