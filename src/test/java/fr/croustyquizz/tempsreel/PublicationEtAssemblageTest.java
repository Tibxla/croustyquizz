package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.transaction.support.TransactionTemplate;
import fr.croustyquizz.ContratApi;
import fr.croustyquizz.manche.BancDuSocle;
import fr.croustyquizz.manche.Destination;
import tools.jackson.databind.json.JsonMapper;

/** Publication après commit, repli de mode absent et portée des envois personnels. */
class PublicationEtAssemblageTest {
	@Test
	void laPublicationAttendLeCommitEtDisparaitSurRollback() {
		var banc = new BancDuSocle();
		var transaction = new TransactionTemplate(banc.stockage);
		var publications = new ArrayList<String>();
		var apresCommit = new PublicationApresCommit();
		transaction.executeWithoutResult(statut -> {
			apresCommit.publier(() -> publications.add("validée"));
			assertThat(publications).isEmpty();
		});
		assertThat(publications).containsExactly("validée");
		transaction.executeWithoutResult(statut -> {
			apresCommit.publier(() -> publications.add("annulée")); statut.setRollbackOnly();
		});
		apresCommit.publier(() -> publications.add("immédiate"));
		assertThat(publications).containsExactly("validée", "immédiate");
	}

	@Test
	void leModeAbsentLaisseSonBlocNulEtLaVueResteConformeEtImmuable() throws Exception {
		var banc = new BancDuSocle();
		UUID soiree = UUID.randomUUID();
		UUID manche = banc.creer(soiree);
		banc.service.demarrer(manche, banc.envoi(soiree, "/demarrer", Map.of()));
		var assembleur = new AssembleurDeVues(banc.commun, List.of(), banc.verrous, banc.json);
		var vue = assembleur.assembler(soiree, Destination.SALLE, "manche-demarree");
		assertThat(vue.champs()).containsEntry("quiz", null).containsEntry("modeEnCours", "QUIZ");
		assertThatThrownBy(() -> vue.champs().put("quiz", "mutation")).isInstanceOf(UnsupportedOperationException.class);
		var capture = new VueSynchronisee(0, Instant.parse("2026-11-16T12:00:00Z"), null, vue);
		ContratApi.verifier("docs/api/openapi.yaml", "retrouverEtatSalle", 200, "application/json", banc.json.writeValueAsString(capture));
	}

	@Test
	void lesEnvoisJoueurPortentLaSoireeEtUneRevisionPropre() {
		List<String> messages = new ArrayList<>();
		var canal = new ExecutorSubscribableChannel(); canal.subscribe(message -> messages.add(message.getPayload().toString()));
		var diffuseur = new DiffuseurStomp(new SimpMessagingTemplate(canal), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
				new VerrousSoiree(), new CaptureSalleFactice()::capturerSalle, new CaptureSalleFactice(), Duration.ofHours(24));
		UUID soiree = UUID.randomUUID(); UUID joueur = UUID.randomUUID();
		var vue = new VueComposee("manche-reprise", Map.of("joueurId", joueur));
		diffuseur.envoyerJoueur(soiree, joueur, vue);
		assertThat(messages.getFirst()).contains("soireeId=" + soiree, "revision=1");
		assertThat(diffuseur.capturerJoueur(soiree, joueur, null).revision()).isEqualTo(1);
		assertThat(diffuseur.capturerSalle(soiree, null).revision()).isZero();
	}
}
