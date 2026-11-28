package fr.croustyquizz.manche;

import static fr.croustyquizz.Attentes.PLAFOND;
import static fr.croustyquizz.Attentes.PLAFOND_SECONDES;
import static fr.croustyquizz.Attentes.retenir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionSystemException;
import fr.croustyquizz.tempsreel.DiffuseurFactice;

/** Garanties observables du service : reçus, pause durable, annulation et concurrence. */
class ServiceDeMancheTest {
	private final BancDuSocle banc = new BancDuSocle();
	private final UUID soiree = UUID.randomUUID();

	@Test
	void leDeroulementRecoitLePilotageDeSaSoireeALaCreationEtALaRestauration() {
		var automatique = new AtomicBoolean(true);
		var bancPilote = new BancDuSocle(null, id -> soiree.equals(id) && automatique.get());
		UUID manche = bancPilote.creer(soiree);
		assertThat(bancPilote.service.demarrer(manche, bancPilote.envoi(soiree, "/demarrer", Map.of())))
				.isInstanceOf(Accepte.class);
		ContexteDeManche initial = bancPilote.mode.dernierContexte;
		assertThat(initial.pilotageAutomatique()).isTrue();

		automatique.set(false);
		bancPilote.service.instantane(soiree, manche);
		assertThat(bancPilote.mode.dernierContexte).isSameAs(initial);
		assertThat(initial.pilotageAutomatique()).isTrue();
		automatique.set(true);
		bancPilote.registre.retirer(manche);
		bancPilote.service.instantane(soiree, manche);
		assertThat(bancPilote.mode.dernierContexte).isNotSameAs(initial);
		assertThat(bancPilote.mode.dernierContexte.pilotageAutomatique()).isTrue();

		UUID autreSoiree = UUID.randomUUID();
		UUID autreManche = bancPilote.creer(autreSoiree);
		assertThat(bancPilote.service.demarrer(autreManche, bancPilote.envoi(autreSoiree, "/demarrer", Map.of())))
				.isInstanceOf(Accepte.class);
		assertThat(bancPilote.mode.dernierContexte.pilotageAutomatique()).isFalse();
	}

	@Test
	void uneRestaurationApresRedemarragePerdLeChoixDeDemonstration() {
		var choix = new AtomicReference<>(new PilotageDeSoireeDemonstration());
		choix.get().choisir(soiree, true);
		var bancPilote = new BancDuSocle(null, id -> choix.get().estAutomatique(id));
		UUID manche = bancPilote.creer(soiree);
		assertThat(bancPilote.service.demarrer(manche, bancPilote.envoi(soiree, "/demarrer", Map.of())))
				.isInstanceOf(Accepte.class);
		assertThat(bancPilote.mode.dernierContexte.pilotageAutomatique()).isTrue();

		choix.set(new PilotageDeSoireeDemonstration());
		bancPilote.registre.retirer(manche);
		bancPilote.service.instantane(soiree, manche);
		assertThat(bancPilote.mode.dernierContexte.pilotageAutomatique()).isFalse();
		var fournisseur = new fr.croustyquizz.tempsreel.FournisseurCommunDemonstration(
				bancPilote.stockage, bancPilote.json, choix.get());
		assertThat(fournisseur.fournir(soiree, Destination.CONSOLE).get("soiree")).extracting("pilotage").isEqualTo("MANUEL");
	}

	@Test
	void repeterUnPilotageRendLeRecuInitialSansRepublierMemeApresRestauration() {
		UUID manche = demarrer();
		var pause = new InterventionDeManche(InterventionDeManche.Action.METTRE_EN_PAUSE, null);
		var envoi = banc.envoi(soiree, "/commandes", pause);
		var initial = banc.service.piloter(manche, envoi, pause);
		int publications = banc.diffuseur.envois().size();
		banc.registre.retirer(manche);
		banc.horloge.avancer(Duration.ofHours(1));
		var repetition = banc.service.piloter(manche, envoi, pause);
		assertThat(banc.json.writeValueAsString(ReponsesDeCommande.corps(repetition, envoi.route())))
				.isEqualTo(banc.json.writeValueAsString(ReponsesDeCommande.corps(initial, envoi.route())));
		assertThat(banc.diffuseur.envois()).hasSize(publications);
		var etat = (DeroulementFactice.Etat) banc.service.instantane(soiree, manche);
		assertThat(etat.pause()).isTrue();
		assertThat(etat.etape()).isEqualTo(1);
		assertThat(banc.horloge.minuteursEnAttente()).isZero();
		var reprise = new InterventionDeManche(InterventionDeManche.Action.REPRENDRE, null);
		assertThat(banc.service.piloter(manche, banc.envoi(soiree, "/commandes", reprise), reprise)).isInstanceOf(Accepte.class);
		banc.horloge.avancer(Duration.ofSeconds(20));
		assertThat(((DeroulementFactice.Etat) banc.service.instantane(soiree, manche)).etape()).isEqualTo(2);
	}

	@Test
	void laTransitionDemandeeParLeModeNestPersisteeQuePourUneAcceptation() {
		UUID manche = demarrer();
		var refus = new Refuse(409, "ETAT_INCOMPATIBLE", "Transition refusée.");
		var envoiRefuse = banc.envoi(soiree, "/transition", Map.of());
		assertThat(banc.service.executer(manche, envoiRefuse, () -> PreparationDeCommande.prete(d -> refus),
				EtatManche.EN_PAUSE)).isEqualTo(refus);
		assertThat(banc.stockage.charger(manche).getEtat()).isEqualTo(EtatManche.EN_COURS);

		var envoi = banc.envoi(soiree, "/transition", Map.of());
		assertThat(banc.service.executer(manche, envoi, () -> PreparationDeCommande.prete(d ->
				d.mettreEnPause(new CommandePilotage(envoi.acteur(), envoi.envoiId(), banc.horloge.maintenant()))),
				EtatManche.EN_PAUSE)).isInstanceOf(Accepte.class);
		assertThat(banc.stockage.charger(manche).getEtat()).isEqualTo(EtatManche.EN_PAUSE);
	}

	@Test
	void laDatePersistanteEstCelleDeLaCommandeMemeSiLHorlogeAvance() {
		var temps = new HorlogeFactice(Instant.parse("2026-10-02T20:00:00.123456789Z"));
		Horloge horloge = new Horloge() {
			@Override public Instant maintenant() {
				Instant instant = temps.maintenant();
				temps.avancer(Duration.ofMillis(2));
				return instant;
			}
			@Override public Minuteur programmer(Duration delai, Runnable action) { return temps.programmer(delai, action); }
		};
		var socle = new BancDuSocle(horloge);
		UUID manche = socle.creer(soiree);
		socle.service.demarrer(manche, socle.envoi(soiree, "/demarrer", Map.of()));

		var pause = new InterventionDeManche(InterventionDeManche.Action.METTRE_EN_PAUSE, null);
		var resultat = (Accepte) socle.service.piloter(manche, socle.envoi(soiree, "/commandes", pause), pause);
		Instant recueLe = ((AccuseCommande) resultat.accuse()).recueLe();
		assertThat(socle.stockage.charger(manche).getModifieLe()).isEqualTo(recueLe);
		assertThat(recueLe.getNano() % 1_000).isZero();
	}

	@Test
	void reutiliserUnEnvoiAvecUnAutreCorpsEstRefuse() {
		UUID manche = demarrer();
		var envoi = banc.envoi(soiree, "/commande-mode", Map.of("titre", "initial"));
		AtomicInteger appels = new AtomicInteger();
		var resultat = banc.service.executer(manche, envoi, () -> PreparationDeCommande.prete(d -> {
			appels.incrementAndGet(); return new Refuse(409, "ETAT_INCOMPATIBLE", "Refus de test.");
		}));
		assertThat(resultat).isInstanceOf(Refuse.class);
		assertThat(banc.service.executer(manche, new EnvoiDeCommande(envoi.acteur(), envoi.route(), envoi.envoiId(), Map.of("titre", "autre")),
				() -> { throw new AssertionError("Pas de nouvelle préparation"); }))
				.isEqualTo(new Refuse(409, "ENVOI_REUTILISE", "Cet identifiant d'envoi a déjà été utilisé avec un autre contenu."));
		assertThat(appels).hasValue(1);
	}

	@Test
	void unRefusPrepareEstEnregistreSansAppelerLeDeroulement() {
		UUID manche = demarrer();
		var envoi = banc.envoi(soiree, "/externe", Map.of());
		var refus = new Refuse(404, "RESSOURCE_INTROUVABLE", "Contenu externe introuvable.");
		int avant = banc.diffuseur.envois().size();
		assertThat(banc.service.executer(manche, envoi, () -> PreparationDeCommande.refusee(refus))).isEqualTo(refus);
		assertThat(banc.service.executer(manche, envoi, () -> { throw new AssertionError("Nouvel appel externe"); })).isEqualTo(refus);
		assertThat(banc.diffuseur.envois()).hasSize(avant);
	}

	@Test
	void uneExceptionDePreparationConserveLesMinuteursDuDeroulement() {
		UUID manche = demarrer();
		int publications = banc.diffuseur.envois().size();
		var panne = new IllegalStateException("Préparation externe indisponible.");
		assertThatThrownBy(() -> banc.service.executer(manche, banc.envoi(soiree, "/externe", Map.of()),
				() -> { throw panne; })).isSameAs(panne);
		assertThat(banc.diffuseur.envois()).hasSize(publications);
		banc.horloge.avancer(Duration.ofSeconds(20));
		// Vérifier le rappel avant toute lecture qui pourrait restaurer et réarmer le déroulement.
		var envois = banc.diffuseur.envois();
		assertThat(envois).hasSize(publications + 6);
		assertThat(envois.subList(publications, envois.size()))
				.filteredOn(envoi -> envoi.cible() == DiffuseurFactice.Cible.JOUEUR)
				.extracting(DiffuseurFactice.Envoi::destinataire)
				.containsExactlyInAnyOrderElementsOf(banc.commun.joueurs(soiree));
		assertThat(banc.horloge.minuteursEnAttente()).isEqualTo(1);
		assertThat(((DeroulementFactice.Etat) banc.service.instantane(soiree, manche)).etape()).isEqualTo(2);
	}

	@Test
	void unCommitAnnuleNePubliePasEtLeProchainAppelRestaureLaBase() {
		UUID manche = demarrer();
		Object avant = banc.service.instantane(soiree, manche);
		int publications = banc.diffuseur.envois().size();
		var pause = new InterventionDeManche(InterventionDeManche.Action.METTRE_EN_PAUSE, null);
		var envoi = banc.envoi(soiree, "/commandes", pause);
		// L'échec vise la transaction de mutation, après la préparation et le premier contrôle d'envoi.
		assertThatThrownBy(() -> banc.service.executer(manche, envoi, () -> {
			banc.stockage.echouerCommit = true;
			return PreparationDeCommande.prete(d -> d.mettreEnPause(new CommandePilotage(envoi.acteur(), envoi.envoiId(), banc.horloge.maintenant())));
		})).isInstanceOf(TransactionSystemException.class);
		assertThat(banc.diffuseur.envois()).hasSize(publications);
		assertThat(banc.service.instantane(soiree, manche)).isEqualTo(avant);
		assertThat(banc.service.piloter(manche, envoi, pause)).isInstanceOf(Accepte.class);
	}

	@Test
	void exceptionDuDomaineAnnuleEtLibereLeVerrou() {
		UUID manche = demarrer();
		int avant = banc.diffuseur.envois().size();
		assertThatThrownBy(() -> banc.service.executer(manche, banc.envoi(soiree, "/casse", Map.of()),
				() -> PreparationDeCommande.prete(d -> { throw new IllegalStateException("panne"); })))
				.isInstanceOf(IllegalStateException.class);
		assertThat(banc.diffuseur.envois()).hasSize(avant);
		assertThat(banc.service.instantane(soiree, manche)).isNotNull();
	}

	@Test
	void commandesEtMinuteursDuneSoireeSontSerialises() throws Exception {
		UUID manche = demarrer();
		CountDownLatch entree = new CountDownLatch(1);
		CountDownLatch sortir = new CountDownLatch(1);
		AtomicInteger simultanes = new AtomicInteger();
		AtomicInteger maximum = new AtomicInteger();
		try (var fils = Executors.newFixedThreadPool(2)) {
			try {
				var premier = fils.submit(() -> banc.service.executer(manche, banc.envoi(soiree, "/lent", Map.of()),
						() -> PreparationDeCommande.prete(d -> {
							maximum.accumulateAndGet(simultanes.incrementAndGet(), Math::max); entree.countDown();
							retenir(sortir); simultanes.decrementAndGet();
							return new Refuse(409, "ETAT_INCOMPATIBLE", "Sans effet.");
						})));
				assertThat(entree.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
				var filMinuteur = new AtomicReference<Thread>();
				var minuteur = fils.submit(() -> { filMinuteur.set(Thread.currentThread()); banc.horloge.avancer(Duration.ofSeconds(20)); });
				await().atMost(PLAFOND).until(() -> filMinuteur.get() != null && banc.verrous.estAttenduPar(soiree, filMinuteur.get()));
				assertThat(minuteur.isDone()).isFalse();
				sortir.countDown(); premier.get(PLAFOND_SECONDES, TimeUnit.SECONDS); minuteur.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
				assertThat(maximum).hasValue(1);
				assertThat(((DeroulementFactice.Etat) banc.service.instantane(soiree, manche)).etape()).isEqualTo(2);
			} finally { sortir.countDown(); }
		}
	}

	@Test
	void passerEtapeAnnuleAussiLeMinuteurQuiAttendDejaLeVerrou() throws Exception {
		UUID manche = demarrer();
		int publications = banc.diffuseur.envois().size();
		var filMinuteur = new AtomicReference<Thread>();
		try (var fils = Executors.newSingleThreadExecutor()) {
			var minuteur = banc.verrous.executer(soiree, () -> {
				var rappel = fils.submit(() -> {
					filMinuteur.set(Thread.currentThread());
					banc.horloge.avancer(Duration.ofSeconds(20));
				});
				await().atMost(PLAFOND).until(() -> filMinuteur.get() != null
						&& banc.verrous.estAttenduPar(soiree, filMinuteur.get()));
				var passer = new InterventionDeManche(InterventionDeManche.Action.PASSER_ETAPE, null);
				assertThat(banc.service.piloter(manche, banc.envoi(soiree, "/commandes", passer), passer)).isInstanceOf(Accepte.class);
				return rappel;
			});
			minuteur.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
		}
		assertThat(((DeroulementFactice.Etat) banc.service.instantane(soiree, manche)).etape()).isEqualTo(2);
		assertThat(banc.diffuseur.envois()).hasSize(publications + 6);
		banc.registre.retirer(manche);
		assertThat(((DeroulementFactice.Etat) banc.service.instantane(soiree, manche)).etape()).isEqualTo(2);
		banc.horloge.avancer(Duration.ofSeconds(20));
		assertThat(((DeroulementFactice.Etat) banc.service.instantane(soiree, manche)).etape()).isEqualTo(3);
	}

	@Test
	void restaurerUneEcheanceDepasseeNePerdPasLeRappelLanceAvantLAttachement() throws Exception {
		var temps = new HorlogeFactice();
		var filMinuteur = new AtomicReference<Thread>();
		var expiration = new AtomicReference<java.util.concurrent.Future<?>>();
		var verrousDeRestauration = new AtomicReference<fr.croustyquizz.tempsreel.VerrousSoiree>();
		try (var fils = Executors.newSingleThreadExecutor()) {
			Horloge immediate = new Horloge() {
				@Override
				public java.time.Instant maintenant() { return temps.maintenant(); }
				@Override
				public Minuteur programmer(Duration delai, Runnable action) {
					if (!delai.isZero()) { return temps.programmer(delai, action); }
					var rappel = fils.submit(() -> { filMinuteur.set(Thread.currentThread()); action.run(); });
					expiration.set(rappel);
					// L'ordonnanceur a lancé le rappel avant que programmer ne rende la main à restaurer.
					await().atMost(PLAFOND).until(() -> filMinuteur.get() != null
							&& verrousDeRestauration.get().estAttenduPar(soiree, filMinuteur.get()));
					return () -> rappel.cancel(false);
				}
			};
			var restauration = new BancDuSocle(immediate);
			verrousDeRestauration.set(restauration.verrous);
			UUID manche = restauration.creer(soiree);
			restauration.service.demarrer(manche, restauration.envoi(soiree, "/demarrer", Map.of()));
			restauration.registre.retirer(manche);
			temps.avancer(Duration.ofSeconds(21));
			int publications = restauration.diffuseur.envois().size();
			restauration.service.instantane(soiree, manche);
			assertThat(expiration.get()).isNotNull();
			expiration.get().get(PLAFOND_SECONDES, TimeUnit.SECONDS);
			assertThat(((DeroulementFactice.Etat) restauration.service.instantane(soiree, manche)).etape()).isEqualTo(2);
			assertThat(restauration.diffuseur.envois()).hasSize(publications + 6);
			restauration.registre.retirer(manche);
			assertThat(((DeroulementFactice.Etat) restauration.service.instantane(soiree, manche)).etape()).isEqualTo(2);
		}
	}

	@Test
	void deuxCommandesConcurrentesDuneSoireePassentDansLOrdre() throws Exception {
		UUID manche = demarrer();
		var entree = new CountDownLatch(1); var sortir = new CountDownLatch(1);
		var ordre = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
		var secondFil = new AtomicReference<Thread>();
		try (var fils = Executors.newFixedThreadPool(2)) {
			try {
				var premiere = fils.submit(() -> banc.service.executer(manche, banc.envoi(soiree, "/premiere", Map.of()),
						() -> PreparationDeCommande.prete(d -> {
							ordre.add(1); entree.countDown(); retenir(sortir);
							return new Refuse(409, "ETAT_INCOMPATIBLE", "Sans effet.");
						})));
				assertThat(entree.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
				var seconde = fils.submit(() -> {
					secondFil.set(Thread.currentThread());
					return banc.service.executer(manche, banc.envoi(soiree, "/seconde", Map.of()),
							() -> PreparationDeCommande.prete(d -> { ordre.add(2); return new Refuse(409, "ETAT_INCOMPATIBLE", "Sans effet."); }));
				});
				await().atMost(PLAFOND).until(() -> secondFil.get() != null && banc.verrous.estAttenduPar(soiree, secondFil.get()));
				assertThat(ordre).containsExactly(1);
				sortir.countDown(); premiere.get(PLAFOND_SECONDES, TimeUnit.SECONDS); seconde.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
				assertThat(ordre).containsExactly(1, 2);
			} finally { sortir.countDown(); }
		}
	}

	@Test
	void preparerUnAppelExterneNeRetientPasLeVerrou() throws Exception {
		UUID manche = demarrer();
		var preparation = new CountDownLatch(1); var finir = new CountDownLatch(1);
		try (var fils = Executors.newFixedThreadPool(2)) {
			try {
				var externe = fils.submit(() -> banc.service.executer(manche, banc.envoi(soiree, "/externe", Map.of()), () -> {
					preparation.countDown(); retenir(finir);
					return PreparationDeCommande.refusee(new Refuse(404, "RESSOURCE_INTROUVABLE", "Contenu absent."));
				}));
				assertThat(preparation.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
				var pause = new InterventionDeManche(InterventionDeManche.Action.METTRE_EN_PAUSE, null);
				var autre = fils.submit(() -> banc.service.piloter(manche, banc.envoi(soiree, "/commandes", pause), pause));
				assertThat(autre.get(PLAFOND_SECONDES, TimeUnit.SECONDS)).isInstanceOf(Accepte.class);
				finir.countDown(); assertThat(externe.get(PLAFOND_SECONDES, TimeUnit.SECONDS)).isInstanceOf(Refuse.class);
			} finally { finir.countDown(); }
		}
	}

	@Test
	void conserveLeStatut201EtLeCorpsDuRecu() {
		UUID manche = demarrer();
		var envoi = banc.envoi(soiree, "/creation", Map.of());
		var initial = banc.service.executer(manche, envoi, () -> PreparationDeCommande.prete(d ->
				new Accepte(Map.of("id", "cree"), java.util.Set.of(), "file-attente-modifiee", 201)));
		assertThat(ReponsesDeCommande.statut(initial)).isEqualTo(201);
		var rejeu = banc.service.executer(manche, envoi, () -> { throw new AssertionError("Rejeu préparé"); });
		assertThat(ReponsesDeCommande.statut(rejeu)).isEqualTo(201);
		assertThat(banc.json.writeValueAsString(((Accepte) rejeu).accuse())).isEqualTo("{\"id\":\"cree\"}");
	}

	@Test
	void lesPointsProtegesPermettentUnRefusAvantPreparationSansRecu() {
		var socle = new SocleDeManche(banc.verrous, banc.stockage, banc.stockage, banc.registre,
				new RegistreDesEnvois(banc.stockage, banc.json),
				new fr.croustyquizz.tempsreel.PublicationDeVues(banc.assembleur, banc.commun,
						new fr.croustyquizz.tempsreel.PublicationApresCommit(), banc.diffuseur, evenement -> { }), banc.horloge, banc.json, soireeId -> false, java.util.List.of());
		class ServiceAvecGarde extends ServiceDeManche<ModeFacticeDuSocle.Reglages, DeroulementFactice.Etat> {
			ServiceAvecGarde() { super(banc.mode, banc.mode, socle); }
			@Override protected Refuse verifierAvantPreparation(ManchePersistante manche, EnvoiDeCommande envoi, boolean demarrage) {
				return new Refuse(409, "ETAPE_INACTIVE", "Sans étape.");
			}
			@Override protected boolean doitConserverRecu(ResultatDeCommande resultat) { return false; }
		}
		var service = new ServiceAvecGarde();
		UUID manche = banc.creer(soiree);
		var envoi = banc.envoi(soiree, "/avant", Map.of());
		assertThatThrownBy(() -> service.executer(manche, envoi, () -> { throw new AssertionError("Source appelée"); }))
				.isInstanceOf(RefusDeCommande.class);
		assertThat(banc.stockage.retrouverEnvoi(EnvoiDeManche.cle(envoi.acteur(), envoi.route(), envoi.envoiId()))).isNull();
	}

	@Test
	void leRefusPreparePrecedeLEtatAvecLesGardesParDefaut() {
		UUID manche = banc.creer(soiree);
		Refuse refus = new Refuse(404, "RESSOURCE_INTROUVABLE", "Contenu absent.");
		assertThat(banc.service.executer(manche, banc.envoi(soiree, "/prepare", Map.of()),
				() -> PreparationDeCommande.refusee(refus))).isEqualTo(refus);
	}

	@Test
	void rejoueUnSucces204SansCorpsNiPublication() {
		UUID manche = demarrer();
		var envoi = banc.envoi(soiree, "/sans-corps", Map.of());
		int avant = banc.diffuseur.envois().size();
		banc.service.executer(manche, envoi, () -> PreparationDeCommande.prete(d -> new Accepte(null, java.util.Set.of(), "file-attente-modifiee")));
		var rejeu = banc.service.executer(manche, envoi, () -> { throw new AssertionError("Rejeu préparé"); });
		assertThat(ReponsesDeCommande.statut(rejeu)).isEqualTo(204);
		assertThat(((Accepte) rejeu).accuse()).isNull();
		assertThat(banc.diffuseur.envois()).hasSize(avant);
	}

	@Test
	void lObservateurApprendLaFinNaturelleUneSeuleFoisDansLaTransaction() {
		var fins = new java.util.ArrayList<String>();
		var bancObserve = new BancDuSocle(null, id -> false, java.util.List.of((soireeId, mancheId, maintenant) -> {
			assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
					.isTrue();
			fins.add(soireeId + "/" + mancheId + "/" + maintenant);
		}));
		UUID manche = bancObserve.creer(soiree);
		bancObserve.service.demarrer(manche, bancObserve.envoi(soiree, "/demarrer", Map.of()));
		var pause = new InterventionDeManche(InterventionDeManche.Action.METTRE_EN_PAUSE, null);
		var reprise = new InterventionDeManche(InterventionDeManche.Action.REPRENDRE, null);
		bancObserve.service.piloter(manche, bancObserve.envoi(soiree, "/commandes", pause), pause);
		bancObserve.service.piloter(manche, bancObserve.envoi(soiree, "/commandes", reprise), reprise);
		bancObserve.horloge.avancer(Duration.ofSeconds(40));
		assertThat(fins).isEmpty();

		bancObserve.horloge.avancer(Duration.ofSeconds(20));
		assertThat(fins).containsExactly(soiree + "/" + manche + "/" + bancObserve.horloge.maintenant());
		var terminer = new InterventionDeManche(InterventionDeManche.Action.TERMINER, null);
		assertThat(bancObserve.service.piloter(manche, bancObserve.envoi(soiree, "/commandes", terminer), terminer))
				.isInstanceOf(Refuse.class);
		bancObserve.horloge.avancer(Duration.ofMinutes(5));
		assertThat(fins).hasSize(1);
	}

	@Test
	void lObservateurApprendLaFinCommandeeMaisPasLaClotureParLaSoiree() {
		var fins = new java.util.ArrayList<UUID>();
		var bancObserve = new BancDuSocle(null, id -> false,
				java.util.List.of((soireeId, mancheId, maintenant) -> fins.add(mancheId)));
		UUID terminee = bancObserve.creer(soiree);
		bancObserve.service.demarrer(terminee, bancObserve.envoi(soiree, "/demarrer", Map.of()));
		var terminer = new InterventionDeManche(InterventionDeManche.Action.TERMINER, null);
		var envoi = bancObserve.envoi(soiree, "/commandes", terminer);
		bancObserve.service.piloter(terminee, envoi, terminer);
		bancObserve.service.piloter(terminee, envoi, terminer);
		assertThat(fins).containsExactly(terminee);

		// La fin de Soirée clôt sa Manche active sans ouvrir de transition (#123, question 1).
		UUID close = bancObserve.creer(soiree);
		bancObserve.service.demarrer(close, bancObserve.envoi(soiree, "/demarrer", Map.of()));
		assertThat(bancObserve.service.terminerPourSoiree(close, bancObserve.envoi(soiree, "/cloture", terminer)))
				.isInstanceOf(Accepte.class);
		assertThat(fins).containsExactly(terminee);
	}

	private UUID demarrer() {
		UUID manche = banc.creer(soiree);
		assertThat(banc.service.demarrer(manche, banc.envoi(soiree, "/demarrer", Map.of()))).isInstanceOf(Accepte.class);
		assertThat(banc.diffuseur.envois()).hasSize(6);
		return manche;
	}
}
