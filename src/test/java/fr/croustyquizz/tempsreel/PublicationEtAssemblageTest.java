package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
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
	void lesChampsCommunsSontAjoutesAuxSeulesDestinationsConcerneesMemeQuandIlsSontNuls() {
		var banc = new BancDuSocle();
		UUID soiree = UUID.randomUUID();
		banc.creer(soiree);
		var assembleur = avecChamps(banc, champ("information"), champ("etat"));

		assertThat(assembleur.assembler(soiree, Destination.SALLE, "manche-terminee").champs()).containsEntry("information", null);
		assertThat(assembleur.assembler(soiree, Destination.CONSOLE, "manche-terminee").champs()).containsEntry("etat", null);
		assertThat(assembleur.assembler(soiree, Destination.joueur(UUID.randomUUID()), "manche-terminee").champs())
				.doesNotContainKeys("information", "etat");
	}

	@Test
	void unChampCommunNePeutPasEtreFourniDeuxFoisNiEcraserLaPartieCommune() {
		var banc = new BancDuSocle();
		UUID soiree = UUID.randomUUID();
		banc.creer(soiree);

		assertThatThrownBy(() -> avecChamps(banc, champ("information"), champ("information")))
				.isInstanceOf(IllegalArgumentException.class);
		var assembleur = avecChamps(banc, champ("dernierBuzz"));
		assertThatThrownBy(() -> assembleur.capturerSalle(soiree)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unChampCommunEstPrepareUneSeuleFoisParAssemblage() {
		var banc = new BancDuSocle();
		UUID soiree = UUID.randomUUID();
		banc.creer(soiree);
		var preparations = new AtomicInteger();
		var champ = new FournisseurDeChampCommun() {
			@Override public String champ() { return "palmares"; }
			@Override public boolean concerne(Destination destination) {
				return Destination.SALLE.equals(destination) || Destination.CONSOLE.equals(destination);
			}
			@Override public Object fournir(UUID soireeId, Destination destination, Map<String, Object> partieCommune) {
				throw new AssertionError("L'assembleur passe par preparer.");
			}
			@Override public ChampPrepare preparer(UUID soireeId) {
				int numero = preparations.incrementAndGet();
				return (destination, partieCommune) -> destination.equals(Destination.SALLE) ? "salle " + numero : "console " + numero;
			}
		};
		var assembleur = avecChamps(banc, champ);

		var vues = assembleur.assembler(soiree, Set.of(Destination.SALLE, Destination.CONSOLE), "manche-terminee");
		assertThat(preparations).hasValue(1);
		assertThat(vues.get(Destination.SALLE).champs()).containsEntry("palmares", "salle 1");
		assertThat(vues.get(Destination.CONSOLE).champs()).containsEntry("palmares", "console 1");
		assembleur.assembler(soiree, Set.of(Destination.joueur(UUID.randomUUID())), "manche-terminee");
		assertThat(preparations).hasValue(1);
	}

	@Test
	void unChampCommunSePrepareUneFoisParAssemblagePourToutesSesDestinations() {
		var banc = new BancDuSocle();
		UUID soiree = UUID.randomUUID();
		banc.creer(soiree);
		var preparations = new AtomicInteger();
		var champ = new FournisseurDeChampCommun() {
			@Override public String champ() { return "transitionManche"; }
			@Override public boolean concerne(Destination destination) { return true; }
			@Override public Object fournir(UUID soireeId, Destination destination, Map<String, Object> partieCommune) {
				throw new AssertionError("La préparation sert toutes les destinations.");
			}
			@Override public ChampPrepare preparer(UUID soireeId) {
				preparations.incrementAndGet();
				return (destination, partieCommune) -> "même valeur";
			}
		};
		var destinations = Set.of(Destination.SALLE, Destination.CONSOLE,
				Destination.joueur(UUID.randomUUID()), Destination.joueur(UUID.randomUUID()));

		var vues = avecChamps(banc, champ).assembler(soiree, destinations, "manche-terminee");

		assertThat(preparations).hasValue(1);
		assertThat(vues.values()).hasSize(4)
				.allSatisfy(vue -> assertThat(vue.champs()).containsEntry("transitionManche", "même valeur"));
	}

	private static AssembleurDeVues avecChamps(BancDuSocle banc, FournisseurDeChampCommun... champs) {
		var beans = new StaticListableBeanFactory();
		for (int i = 0; i < champs.length; i++) {
			beans.addBean("champ" + i, champs[i]);
		}
		return new AssembleurDeVues(banc.commun, List.of(), beans.getBeanProvider(FournisseurDeChampCommun.class),
				banc.verrous, banc.json);
	}

	private static FournisseurDeChampCommun champ(String nom) {
		return new FournisseurDeChampCommun() {
			@Override public String champ() { return nom; }
			@Override public boolean concerne(Destination destination) {
				return Destination.SALLE.equals(destination) || Destination.CONSOLE.equals(destination);
			}
			@Override public Object fournir(UUID soireeId, Destination destination, Map<String, Object> partieCommune) {
				assertThat(partieCommune).containsKey("soiree").doesNotContainKey("karaoke");
				assertThatThrownBy(() -> partieCommune.put("mutation", true)).isInstanceOf(UnsupportedOperationException.class);
				return null;
			}
		};
	}

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
				new VerrousSoiree(), new CaptureSalleFactice()::capturerSalle, new CaptureSalleFactice());
		UUID soiree = UUID.randomUUID(); UUID joueur = UUID.randomUUID();
		var vue = new VueComposee("manche-reprise", Map.of("joueurId", joueur));
		diffuseur.envoyerJoueur(soiree, joueur, vue);
		assertThat(messages.getFirst()).contains("soireeId=" + soiree, "revision=1");
		assertThat(diffuseur.capturerJoueur(soiree, joueur, null).revision()).isEqualTo(1);
		assertThat(diffuseur.capturerSalle(soiree, null).revision()).isZero();
	}
}
