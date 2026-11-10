package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import fr.croustyquizz.TestcontainersConfiguration;

/** Le service sur un vrai PostgreSQL, avec une heure figée. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, ServiceSoiree.class, ServiceSoireeTest.HeureFigee.class })
class ServiceSoireeTest {

	static final Instant MAINTENANT = Instant.parse("2026-11-12T19:30:00Z");

	@TestConfiguration(proxyBeanMethods = false)
	static class HeureFigee {

		/** Remplace l'horloge système de l'application. */
		@Bean
		@Primary
		Clock heureFigee() {
			return Clock.fixed(MAINTENANT, ZoneOffset.UTC);
		}

	}

	@Autowired
	private ServiceSoiree service;

	@Autowired
	private DepotDeJoueurs joueurs;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TestEntityManager entites;

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
		entites.flush();
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
		entites.flush();
		jdbc.update("update soiree set etat = ? where id = ?", etat.name(), soiree);
		// Oublie les Soirées déjà chargées, pour relire l'état modifié en base.
		entites.clear();
	}

}
