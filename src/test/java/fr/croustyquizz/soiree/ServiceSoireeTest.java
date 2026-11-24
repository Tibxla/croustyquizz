package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.VerrousSoiree;

/**
 * Le service sur un vrai PostgreSQL, avec une heure figée. Chaque appel du
 * service valide sa propre transaction, comme en production : l'ouverture
 * retente dans une nouvelle transaction et doit voir les Soirées déjà créées.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ TestcontainersConfiguration.class, ServiceSoiree.class, ServiceSoireeTest.Reglages.class, VerrousSoiree.class })
class ServiceSoireeTest {

	static final Instant MAINTENANT = Instant.parse("2026-11-12T19:30:00Z");

	@MockitoBean
	private PublicationDeVues vues;

	@TestConfiguration(proxyBeanMethods = false)
	static class Reglages {

		/** Remplace l'horloge système de l'application. */
		@Bean
		@Primary
		Clock heureFigee() {
			return Clock.fixed(MAINTENANT, ZoneOffset.UTC);
		}

		@Bean
		TirageDeTest tirageDeTest() {
			return new TirageDeTest();
		}

	}

	/** Tire au hasard, sauf les codes prévus par le test, rendus dans l'ordre. */
	static final class TirageDeTest implements TirageDeCode {

		private final TirageAuHasard hasard = new TirageAuHasard();
		private final Queue<String> prevus = new ConcurrentLinkedQueue<>();
		private final AtomicInteger tirages = new AtomicInteger();
		private volatile CyclicBarrier rendezVous;

		void prevoir(String... codes) {
			prevus.addAll(List.of(codes));
		}

		/** Les premiers tirages attendent que tous les participants aient tiré leur code. */
		void retenirLesPremiersTirages(CyclicBarrier barriere) {
			this.rendezVous = barriere;
		}

		int tirages() {
			return tirages.get();
		}

		void oublier() {
			prevus.clear();
			tirages.set(0);
			rendezVous = null;
		}

		@Override
		public String tirer() {
			int rang = tirages.incrementAndGet();
			String prevu = prevus.poll();
			String code = prevu != null ? prevu : hasard.tirer();
			CyclicBarrier barriere = rendezVous;
			if (barriere != null && rang <= barriere.getParties()) {
				try {
					barriere.await(10, TimeUnit.SECONDS);
				} catch (Exception interruption) {
					throw new IllegalStateException("Les ouvertures simultanées ne se sont pas rejointes", interruption);
				}
			}
			return code;
		}

	}

	@Autowired
	private ServiceSoiree service;

	@Autowired
	private DepotDeJoueurs joueurs;

	@Autowired
	private TirageDeTest tirage;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void effacerLaBase() {
		tirage.oublier();
		jdbc.update("delete from acces_ecran");
		jdbc.update("delete from joueur");
		jdbc.update("delete from soiree");
	}

	@Test
	void creeUneSoireeEnPreparationALHeureDuServeur() {
		Soiree soiree = service.creer("Quiz du jeudi", Format.SOLO, Pilotage.MANUEL);

		assertThat(service.consulter(soiree.getId())).satisfies(lue -> {
			assertThat(lue.getEtat()).isEqualTo(EtatSoiree.PREPARATION);
			assertThat(lue.getCodeAcces()).isNull();
			assertThat(lue.getCreeeLe()).isEqualTo(MAINTENANT);
		});
	}

	@Test
	void refuseUneSoireeSansTitre() {
		assertThatThrownBy(() -> service.creer(null, Format.SOLO, Pilotage.MANUEL)).isInstanceOf(TitreInvalide.class);
	}

	@Test
	void uneSoireeInconnueEstIntrouvable() {
		UUID inconnue = UUID.randomUUID();

		assertThatThrownBy(() -> service.consulter(inconnue)).isInstanceOf(SoireeIntrouvable.class);
		assertThatThrownBy(() -> service.ouvrir(inconnue)).isInstanceOf(SoireeIntrouvable.class);
		assertThatThrownBy(() -> service.joueurs(inconnue)).isInstanceOf(SoireeIntrouvable.class);
	}

	@Test
	void ouvrirTireUnCodeQuiNeChangePlus() {
		UUID soiree = soireeCreee();

		Soiree ouverte = service.ouvrir(soiree);
		String code = ouverte.getCodeAcces();

		assertThat(ouverte.getEtat()).isEqualTo(EtatSoiree.OUVERTE);
		assertThat(ouverte.getOuverteLe()).isEqualTo(MAINTENANT);
		assertThat(code).matches("[A-HJ-NP-Z2-9]{6}");
		assertThat(service.ouvrir(soiree).getCodeAcces()).isEqualTo(code);
		assertThat(tirage.tirages()).isOne();
	}

	@Test
	void unCodeDejaPrisEstRetireAvecUnNouveauTirage() {
		tirage.prevoir("K7PQ2M", "K7PQ2M", "ABCDEF");
		Soiree premiere = soireeOuverte();

		Soiree seconde = service.ouvrir(soireeCreee());

		assertThat(premiere.getCodeAcces()).isEqualTo("K7PQ2M");
		assertThat(seconde.getCodeAcces()).isEqualTo("ABCDEF");
		assertThat(seconde.getEtat()).isEqualTo(EtatSoiree.OUVERTE);
		assertThat(tirage.tirages()).isEqualTo(3);
	}

	@Test
	void deuxOuverturesSimultaneesQuiTirentLeMemeCodeEnObtiennentDeuxDifferents() throws Exception {
		UUID premiere = soireeCreee();
		UUID seconde = soireeCreee();
		// Les deux ouvertures tirent K7PQ2M, chacune dans sa transaction, avant que l'une ne l'écrive.
		tirage.prevoir("K7PQ2M", "K7PQ2M", "ABCDEF");
		tirage.retenirLesPremiersTirages(new CyclicBarrier(2));

		ExecutorService deuxAnimateurs = Executors.newFixedThreadPool(2);
		try {
			Future<Soiree> ouverturePremiere = deuxAnimateurs.submit(() -> service.ouvrir(premiere));
			Future<Soiree> ouvertureSeconde = deuxAnimateurs.submit(() -> service.ouvrir(seconde));

			List<String> codes = List.of(ouverturePremiere.get(30, TimeUnit.SECONDS).getCodeAcces(),
					ouvertureSeconde.get(30, TimeUnit.SECONDS).getCodeAcces());

			assertThat(codes).containsExactlyInAnyOrder("K7PQ2M", "ABCDEF");
			assertThat(tirage.tirages()).isEqualTo(3);
		} finally {
			deuxAnimateurs.shutdownNow();
		}
		assertThat(jdbc.queryForList("select code_acces from soiree where etat = 'OUVERTE'", String.class))
				.containsExactlyInAnyOrder("K7PQ2M", "ABCDEF");
	}

	@Test
	void abandonneApresTropDeCodesDejaPris() {
		tirage.prevoir("K7PQ2M");
		soireeOuverte();
		for (int essai = 0; essai < ServiceSoiree.ESSAIS_DE_CODE; essai++) {
			tirage.prevoir("K7PQ2M");
		}
		UUID seconde = soireeCreee();

		assertThatThrownBy(() -> service.ouvrir(seconde)).isInstanceOf(IllegalStateException.class);
		assertThat(service.consulter(seconde).getEtat()).isEqualTo(EtatSoiree.PREPARATION);
		assertThat(tirage.tirages()).isEqualTo(1 + ServiceSoiree.ESSAIS_DE_CODE);
	}

	@Test
	void uneSoireeLanceeNePeutPlusEtreOuverte() {
		UUID soiree = soireeOuverte().getId();
		forcerEtat(soiree, EtatSoiree.EN_COURS);

		assertThatThrownBy(() -> service.ouvrir(soiree)).isInstanceOf(SoireeDejaLancee.class);
	}

	@Test
	void unJoueurRejointAvecLeCodeTapeALaMain() {
		Soiree soiree = soireeOuverte();

		ArriveeDeJoueur arrivee = service.rejoindre(" " + soiree.getCodeAcces().toLowerCase() + " ", "  Léa ");

		assertThat(arrivee.soiree().getId()).isEqualTo(soiree.getId());
		assertThat(arrivee.joueur().getPseudo()).isEqualTo("Léa");
		assertThat(arrivee.joueur().getArriveLe()).isEqualTo(MAINTENANT);
		assertThat(jdbc.queryForObject("select empreinte_jeton from joueur where id = ?", byte[].class,
				arrivee.joueur().getId())).isEqualTo(arrivee.jeton().empreinte());
	}

	@Test
	void unJoueurPeutArriverDansUneSoireeDejaCommencee() {
		Soiree soiree = soireeOuverte();
		forcerEtat(soiree.getId(), EtatSoiree.EN_COURS);

		assertThat(service.rejoindre(soiree.getCodeAcces(), "Tom").soiree().getId()).isEqualTo(soiree.getId());
	}

	@Test
	void unCodeQuiNeMeneANulleSoireeEstRefuse() {
		Soiree terminee = soireeOuverte();
		forcerEtat(terminee.getId(), EtatSoiree.TERMINEE);

		assertThatThrownBy(() -> service.rejoindre("pas-un-code", "Léa")).isInstanceOf(CodeAccesInvalide.class);
		assertThatThrownBy(() -> service.rejoindre(null, "Léa")).isInstanceOf(CodeAccesInvalide.class);
		assertThatThrownBy(() -> service.rejoindre(terminee.getCodeAcces(), "Léa"))
				.isInstanceOf(CodeAccesInvalide.class);
	}

	@Test
	void unPseudoVideNeCreeAucunJoueur() {
		Soiree soiree = soireeOuverte();

		assertThatThrownBy(() -> service.rejoindre(soiree.getCodeAcces(), "   ")).isInstanceOf(PseudoInvalide.class);
		assertThat(joueurs.count()).isZero();
	}

	@Test
	void listeLesJoueursDansLeurOrdreDArrivee() {
		Soiree soiree = soireeOuverte();
		service.rejoindre(soiree.getCodeAcces(), "Léa");
		service.rejoindre(soiree.getCodeAcces(), "Tom");

		assertThat(service.joueurs(soiree.getId())).extracting(Joueur::getPseudo).containsExactly("Léa", "Tom");
	}

	private UUID soireeCreee() {
		return service.creer("Quiz du jeudi", Format.SOLO, Pilotage.MANUEL).getId();
	}

	private Soiree soireeOuverte() {
		return service.ouvrir(soireeCreee());
	}

	/** Le lancement et la fin de Soirée arrivent plus tard : on force l'état en base. */
	private void forcerEtat(UUID soiree, EtatSoiree etat) {
		jdbc.update("update soiree set etat = ? where id = ?", etat.name(), soiree);
	}

}
