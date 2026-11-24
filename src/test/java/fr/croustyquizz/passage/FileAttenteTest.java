package fr.croustyquizz.passage;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class FileAttenteTest {
	private static final UUID SOIREE = UUID.randomUUID();
	private static final UUID MANCHE = UUID.randomUUID();
	private static final UUID ANA = UUID.randomUUID();
	private static final UUID BORIS = UUID.randomUUID();
	private static final UUID CELIA = UUID.randomUUID();
	private static final Instant RECU = Instant.parse("2026-11-17T12:00:00Z");
	private final FileAttente file = FileAttente.depuis(new EtatFileAttente(
			SOIREE, MANCHE, 10, 1, 1, 1, null, List.of(), Map.of()));
	private long numero;

    @Test
    void appelleSeulementLaPremiereAttenteAuBonCompteur() {
        inscrireDeuxTours();
        EtatFileAttente avant = file.etat();
        verifierRefus(file.appelerPassage(new CiblePassage(new UUID(0, 99), 0)), 404, "RESSOURCE_INTROUVABLE");
        verifierRefus(file.appelerPassage(cible("Boris-1")), 409, "COMMANDE_PERIMEE");
        verifierRefus(file.appelerPassage(new CiblePassage(cible("Ana-1").passageId(), 1)), 409, "COMMANDE_PERIMEE");
        assertThat(file.etat()).isEqualTo(avant);
        assertThat(file.appelerPassage(cible("Ana-1"))).isInstanceOf(ResultatFile.Modifiee.class);
        assertThat(passage("Ana-1").etat()).isEqualTo(EtatPassage.APPELE);
        assertThat(passage("Ana-1").numeroModification()).isEqualTo(1);
        verifierRefus(file.appelerPassage(cible("Ana-1")), 409, "COMMANDE_PERIMEE");
    }

    @Test
    void demarreSeulementUnPassageAppeleAuBonCompteur() {
        inscrire(ANA, "Ana-1");
        var attente = cible("Ana-1");
        var avant = file.etat();
        verifierRefus(file.demarrerPassage(new CiblePassage(new UUID(0, 99), 0)), 404, "RESSOURCE_INTROUVABLE");
        verifierRefus(file.demarrerPassage(attente), 409, "COMMANDE_PERIMEE");
        assertThat(file.etat()).isEqualTo(avant);
        file.appelerPassage(attente);
        avant = file.etat();
        verifierRefus(file.demarrerPassage(attente), 409, "COMMANDE_PERIMEE");
        assertThat(file.etat()).isEqualTo(avant);
        assertThat(file.demarrerPassage(cible("Ana-1"))).isInstanceOf(ResultatFile.Modifiee.class);
        assertThat(passage("Ana-1").etat()).isEqualTo(EtatPassage.EN_COURS);
        assertThat(passage("Ana-1").numeroModification()).isEqualTo(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "TERMINE, EN_COURS", "SAUTE, APPELE", "SAUTE, EN_COURS", "ANNULE, APPELE", "ANNULE, EN_COURS"
    })
    void clotureAvecLeTourConsommeEtSansReplacerLesAutresAttentes(EtatPassage terminal, EtatPassage depart) {
        inscrireDeuxTours();
        file.definirPriorite(cible("Ana-2"), true);
        var attente = cible("Ana-2");
        var trace = switch (terminal) {
            case TERMINE -> new TraceTerminale("FIN_PREVUE", null, RECU);
            case SAUTE -> new TraceTerminale("PASSE_PAR_ANIMATEUR", BORIS, RECU);
            default -> new TraceTerminale("MANCHE_TERMINEE", BORIS, RECU);
        };
        var avant = file.etat();
        verifierRefus(file.clorePassage(attente, terminal, trace), 409, "COMMANDE_PERIMEE");
        assertThat(file.etat()).isEqualTo(avant);
        file.appelerPassage(attente);
        if (terminal == EtatPassage.TERMINE) {
            avant = file.etat();
            verifierRefus(file.clorePassage(cible("Ana-2"), terminal, trace), 409, "COMMANDE_PERIMEE");
            assertThat(file.etat()).isEqualTo(avant);
        }
        if (depart == EtatPassage.EN_COURS) { file.demarrerPassage(cible("Ana-2")); }
        var courant = cible("Ana-2");
        var autres = file.ordreEffectif();
        var tours = file.etat().derniersToursConsommes();
        avant = file.etat();
        verifierRefus(file.clorePassage(attente, terminal, trace), 409, "COMMANDE_PERIMEE");
        assertThat(file.etat()).isEqualTo(avant);
        assertThat(file.clorePassage(courant, terminal, trace)).isInstanceOf(ResultatFile.Modifiee.class);
        Passage clos = passage("Ana-2");
        assertThat(clos.etat()).isEqualTo(terminal);
        assertThat(clos.numeroModification()).isEqualTo(courant.numeroModification() + 1);
        assertThat(clos.tourConsomme()).isEqualTo(1L);
        assertThat(clos.traceTerminale()).isEqualTo(trace);
        assertThat(file.ordreEffectif()).isEqualTo(autres);
        assertThat(file.etat().derniersToursConsommes()).isEqualTo(tours);
        avant = file.etat();
        verifierRefus(file.clorePassage(cible("Ana-2"), terminal, trace), 409, "COMMANDE_PERIMEE");
        assertThat(file.etat()).isEqualTo(avant);
    }

    @Test
    void sauteLePassagePrioritaireALEcheanceSansActeurEtGardeLesReservations() {
        inscrireDeuxTours();
        file.definirPriorite(cible("Ana-2"), true);
        file.appelerPassage(cible("Ana-2"));
        var attentes = file.ordreEffectif();
        var tours = file.etat().derniersToursConsommes();
        var trace = new TraceTerminale("ECHEANCE_APPEL", null, RECU.plusSeconds(30));

        assertThat(file.clorePassage(cible("Ana-2"), EtatPassage.SAUTE, trace))
                .isInstanceOf(ResultatFile.Modifiee.class);

        var saute = passage("Ana-2");
        assertThat(saute.etat()).isEqualTo(EtatPassage.SAUTE);
        assertThat(saute.traceTerminale()).isEqualTo(trace);
        assertThat(saute.tourConsomme()).isEqualTo(1L);
        assertThat(file.ordreEffectif()).isEqualTo(attentes);
        assertThat(file.etat().derniersToursConsommes()).isEqualTo(tours);
    }

    @Test
    void uneClotureExigeLeMotifEtLActeurCorrespondants() {
        inscrire(ANA, "Ana-1");
        file.appelerPassage(cible("Ana-1"));
        file.demarrerPassage(cible("Ana-1"));
        var depart = file.etat();
        var courant = cible("Ana-1");
        assertThatIllegalArgumentException().isThrownBy(() -> file.clorePassage(courant,
                EtatPassage.TERMINE, new TraceTerminale("FIN_LECTURE", BORIS, RECU)));
        assertThatIllegalArgumentException().isThrownBy(() -> file.clorePassage(courant,
                EtatPassage.TERMINE, new TraceTerminale("PASSE_PAR_ANIMATEUR", BORIS, RECU)));
        assertThatIllegalArgumentException().isThrownBy(() -> file.clorePassage(courant,
                EtatPassage.SAUTE, new TraceTerminale("PASSE_PAR_ANIMATEUR", null, RECU)));
        assertThatIllegalArgumentException().isThrownBy(() -> file.clorePassage(courant,
                EtatPassage.ANNULE, new TraceTerminale("MANCHE_TERMINEE", null, RECU)));
        assertThat(file.etat()).isEqualTo(depart);
    }

    private static void verifierRefus(ResultatFile resultat, int statut, String code) {
        assertThat(resultat).isInstanceOfSatisfying(ResultatFile.Refus.class, r -> {
            assertThat(r.refus().statutHttp()).isEqualTo(statut);
            assertThat(r.refus().code()).isEqualTo(code);
        });
    }

	@Test
	void reserveUnTourParInterpreteDansLOrdreDInscription() {
		inscrire(ANA, "Ana-1"); inscrire(ANA, "Ana-2");
		inscrire(BORIS, "Boris-1"); inscrire(CELIA, "Célia-1"); inscrire(BORIS, "Boris-2");
		assertThat(file.ordreEffectif()).extracting(Passage::contenuId)
				.containsExactly("Ana-1", "Boris-1", "Célia-1", "Ana-2", "Boris-2");
		assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 1L, 1L, 2L, 2L);
		assertThat(file.etat().passages()).extracting(Passage::numeroModification).containsOnly(0L);
	}

    private void inscrireDeuxTours() {
        inscrire(ANA, "Ana-1"); inscrire(ANA, "Ana-2"); inscrire(BORIS, "Boris-1");
        inscrire(CELIA, "Célia-1"); inscrire(BORIS, "Boris-2");
    }

    private Passage passage(String contenu) {
        return file.etat().passages().stream().filter(p -> p.contenuId().equals(contenu)).findFirst().orElseThrow();
    }

    private CiblePassage cible(String contenu) {
        Passage p = passage(contenu); return new CiblePassage(p.id(), p.numeroModification());
    }

    private void verifierOrdre(String... contenus) {
        assertThat(file.ordreEffectif()).extracting(Passage::contenuId).containsExactly(contenus);
        assertThat(file.positions().values()).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, contenus.length).boxed().toList());
    }

    @Test
    void accueilleUnRetardataireDansLeTourEncoreOuvert() {
        inscrireDeuxTours(); file.extraireProchainPassage(false); inscrire(UUID.randomUUID(), "Dina-1");
        verifierOrdre("Boris-1", "Célia-1", "Dina-1", "Ana-2", "Boris-2");
    }

    @Test
    void libereLaReservationRetireeEtReplaceLaSuivanteEnFinDeTour() {
        inscrireDeuxTours();
        file.retirer(BORIS, cible("Ana-1"), RECU);
        verifierOrdre("Boris-1", "Célia-1", "Ana-2", "Boris-2");
        assertThat(passage("Ana-2").numeroModification()).isEqualTo(1);
        assertThat(passage("Ana-1").traceTerminale()).isEqualTo(new TraceTerminale("RETRAIT_ANIMATEUR", BORIS, RECU));
        assertThat(file.etat().derniersToursConsommes()).isEmpty();
    }

    @Test
    void conserveLesReservationsSousLaPrioriteEtConsommeLeTourAAppel() {
        inscrireDeuxTours();
        file.definirPriorite(cible("Ana-2"), true);
        verifierOrdre("Ana-2", "Ana-1", "Boris-1", "Célia-1", "Boris-2");
        assertThat(passage("Ana-2").tourNormal()).isEqualTo(2);
        Passage appele = file.extraireProchainPassage(false).orElseThrow();
        assertThat(appele.etat()).isEqualTo(EtatPassage.APPELE);
        assertThat(appele.tourConsomme()).isEqualTo(1L);
        assertThat(appele.numeroModification()).isEqualTo(2);
        verifierOrdre("Boris-1", "Célia-1", "Boris-2", "Ana-1");
        assertThat(file.etat().tourOuvert()).isEqualTo(1);
        assertThat(passage("Ana-1").numeroModification()).isEqualTo(1);
        assertThat(file.etat().passagePrioritaireId()).isNull();
    }

    @Test
    void remplaceLaPrioriteSansChangerLOrdreNormal() {
        inscrireDeuxTours(); file.definirPriorite(cible("Ana-2"), true);
        file.definirPriorite(cible("Boris-2"), true);
        verifierOrdre("Boris-2", "Ana-1", "Boris-1", "Célia-1", "Ana-2");
        assertThat(passage("Ana-2").numeroModification()).isEqualTo(2);
        assertThat(passage("Boris-2").numeroModification()).isEqualTo(1);
        assertThat(passage("Ana-1").numeroModification()).isZero();
    }

    @Test
    void ouvreLeTourExtraitEtYInscritUnNouvelInterprete() {
        inscrire(ANA, "Ana-1"); inscrire(ANA, "Ana-2");
        file.extraireProchainPassage(false); file.extraireProchainPassage(false);
        inscrire(UUID.randomUUID(), "Emma-1");
        assertThat(file.etat().tourOuvert()).isEqualTo(2);
        assertThat(passage("Emma-1").tourNormal()).isEqualTo(2);
    }

    @Test
    void neDeplaceQueLaCibleDansSonTour() {
        inscrire(ANA, "Ana-1"); inscrire(BORIS, "Boris-1"); inscrire(ANA, "Ana-2");
        inscrire(BORIS, "Boris-2"); inscrire(ANA, "Ana-3");
        file.deplacer(cible("Ana-2"), 2, null);
        verifierOrdre("Ana-1", "Boris-1", "Boris-2", "Ana-2", "Ana-3");
        assertThat(passage("Ana-2").numeroModification()).isEqualTo(1);
        assertThat(passage("Boris-2").numeroModification()).isZero();
    }

    @Test
    void conserveLaConsommationSansAttentePersonnelle() {
        inscrire(ANA, "Ana-1"); inscrire(BORIS, "Boris-1"); file.extraireProchainPassage(false);
        inscrire(ANA, "Ana-2");
        verifierOrdre("Boris-1", "Ana-2");
        assertThat(passage("Ana-2").tourNormal()).isEqualTo(2);
        assertThat(file.etat().derniersToursConsommes()).containsEntry(ANA, 1L);
    }

    private void refuserSansMutation(java.util.function.Supplier<ResultatFile> action, int statut, String code) {
        EtatFileAttente avant = file.etat();
        assertThat(action.get()).isInstanceOfSatisfying(ResultatFile.Refus.class, r -> {
            assertThat(r.refus().statutHttp()).isEqualTo(statut); assertThat(r.refus().code()).isEqualTo(code);
        });
        assertThat(file.etat()).isEqualTo(avant);
    }

    private void sansEffet(java.util.function.Supplier<ResultatFile> action) {
        EtatFileAttente avant = file.etat();
        assertThat(action.get()).isInstanceOf(ResultatFile.SansEffet.class);
        assertThat(file.etat()).isEqualTo(avant);
    }

    @Test
    void gardeLeQuotaPersonnelEtLeDoublonAvantLaLimiteSansConsommerDeNumero() {
        inscrire(ANA, "même"); file.reglerLimite(1);
        EtatFileAttente avant = file.etat();
        assertThat(file.verifierInscription(ANA, "même").orElseThrow().code()).isEqualTo("PASSAGE_DEJA_INSCRIT");
        assertThat(file.verifierInscription(ANA, "autre").orElseThrow().code()).isEqualTo("LIMITE_ATTEINTE");
        assertThat(file.etat()).isEqualTo(avant);
        refuserSansMutation(() -> file.inscrire(new NouveauPassage(UUID.randomUUID(), SOIREE, MANCHE,
                ANA, List.of(ANA), "même", Duration.ofMinutes(3), 2, RECU)), 409, "PASSAGE_DEJA_INSCRIT");
        refuserSansMutation(() -> file.inscrire(new NouveauPassage(UUID.randomUUID(), SOIREE, MANCHE,
                ANA, List.of(ANA), "autre", Duration.ofMinutes(3), 2, RECU)), 409, "LIMITE_ATTEINTE");
        inscrire(BORIS, "même");
        file.desinscrire(ANA, cible("même"), RECU.plusSeconds(1));
        inscrire(ANA, "nouvelle");
        assertThat(passage("nouvelle").ordreInscription()).isEqualTo(3);
        assertThat(passage("même").traceTerminale()).isEqualTo(new TraceTerminale("DESINSCRIPTION", ANA, RECU.plusSeconds(1)));
    }

    @Test
    void baisserLaLimiteNeRetirePersonneEtLaValeurCouranteEstSansEffet() {
        inscrire(ANA, "1"); inscrire(ANA, "2"); file.reglerLimite(1);
        assertThat(file.ordreEffectif()).hasSize(2);
        sansEffet(() -> file.reglerLimite(1));
        assertThat(passage("1").numeroModification()).isZero();
        assertThatThrownBy(() -> file.reglerLimite(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> file.reglerLimite(21)).isInstanceOf(IllegalArgumentException.class);
        assertThat(file.reglerLimite(20)).isInstanceOf(ResultatFile.Modifiee.class);
    }

    @Test
    void desinscriptionDautruiEstIntrouvableAvantCompteurEtEtat() {
        inscrire(ANA, "1"); CiblePassage ancienne = cible("1");
        file.retirer(BORIS, ancienne, RECU);
        refuserSansMutation(() -> file.desinscrire(BORIS, ancienne, RECU), 404, "RESSOURCE_INTROUVABLE");
        refuserSansMutation(() -> file.desinscrire(ANA, ancienne, RECU), 409, "COMMANDE_PERIMEE");
        refuserSansMutation(() -> file.retirer(BORIS, cible("1"), RECU), 409, "COMMANDE_PERIMEE");
    }

    @Test
    void refuseLesCiblesInconnuesPuisPerimeesPourChaqueGeste() {
        CiblePassage inconnue = new CiblePassage(UUID.randomUUID(), 0);
        refuserSansMutation(() -> file.desinscrire(ANA, inconnue, RECU), 404, "RESSOURCE_INTROUVABLE");
        refuserSansMutation(() -> file.retirer(BORIS, inconnue, RECU), 404, "RESSOURCE_INTROUVABLE");
        refuserSansMutation(() -> file.definirPriorite(inconnue, true), 404, "RESSOURCE_INTROUVABLE");
        refuserSansMutation(() -> file.deplacer(inconnue, 1, inconnue), 404, "RESSOURCE_INTROUVABLE");
        inscrire(ANA, "1"); CiblePassage ancienne = cible("1"); file.definirPriorite(ancienne, true);
        refuserSansMutation(() -> file.desinscrire(ANA, ancienne, RECU), 409, "COMMANDE_PERIMEE");
        refuserSansMutation(() -> file.retirer(BORIS, ancienne, RECU), 409, "COMMANDE_PERIMEE");
        refuserSansMutation(() -> file.definirPriorite(ancienne, true), 409, "COMMANDE_PERIMEE");
        refuserSansMutation(() -> file.deplacer(ancienne, 2, ancienne), 409, "COMMANDE_PERIMEE");
        file.extraireProchainPassage(false);
        refuserSansMutation(() -> file.definirPriorite(cible("1"), false), 409, "COMMANDE_PERIMEE");
        refuserSansMutation(() -> file.deplacer(cible("1"), 1, null), 409, "COMMANDE_PERIMEE");
    }

    @Test
    void appliqueLesGardesDuPlacementDansLOrdreSansMutation() {
        inscrireDeuxTours(); CiblePassage inconnue = new CiblePassage(UUID.randomUUID(), 0);
        refuserSansMutation(() -> file.deplacer(cible("Ana-1"), 2, cible("Ana-1")), 422, "DONNEES_INVALIDES");
        refuserSansMutation(() -> file.deplacer(cible("Ana-1"), 2, inconnue), 409, "ROTATION_PROTEGEE");
        refuserSansMutation(() -> file.deplacer(cible("Ana-1"), 1, inconnue), 404, "RESSOURCE_INTROUVABLE");
        refuserSansMutation(() -> file.deplacer(cible("Ana-1"), 1, cible("Ana-2")), 409, "COMMANDE_PERIMEE");
        CiblePassage ancienne = cible("Boris-1"); file.definirPriorite(ancienne, true);
        refuserSansMutation(() -> file.deplacer(cible("Ana-1"), 1, ancienne), 409, "COMMANDE_PERIMEE");
        refuserSansMutation(() -> file.deplacer(cible("Ana-1"), 1, cible("Boris-1")), 409, "ROTATION_PROTEGEE");
        refuserSansMutation(() -> file.deplacer(cible("Boris-1"), 1, inconnue), 409, "ROTATION_PROTEGEE");
        file.retirer(BORIS, cible("Célia-1"), RECU);
        refuserSansMutation(() -> file.deplacer(cible("Ana-1"), 1, cible("Célia-1")), 409, "COMMANDE_PERIMEE");
    }

    @Test
    void neMutePasLesGestesSatisfaitsEnTenantCompteDeLaReservationPrioritaire() {
        inscrireDeuxTours(); file.definirPriorite(cible("Boris-1"), true);
        sansEffet(() -> file.definirPriorite(cible("Boris-1"), true));
        sansEffet(() -> file.definirPriorite(cible("Ana-1"), false));
        // Boris reste entre Ana et Célia dans l’ordre normal : ce placement change donc la File.
        assertThat(file.deplacer(cible("Ana-1"), 1, cible("Célia-1"))).isInstanceOf(ResultatFile.Modifiee.class);
        sansEffet(() -> file.deplacer(cible("Ana-1"), 1, cible("Célia-1")));
        sansEffet(() -> file.deplacer(cible("Célia-1"), 1, null));
        file.definirPriorite(cible("Boris-1"), false);
        verifierOrdre("Boris-1", "Ana-1", "Célia-1", "Ana-2", "Boris-2");
        assertThat(passage("Boris-1").numeroModification()).isEqualTo(2);
    }

    @Test
    void retireUnePrioriteSansDoubleIncrementEtAutoriseLaReinscription() {
        inscrireDeuxTours(); file.definirPriorite(cible("Ana-2"), true);
        file.desinscrire(ANA, cible("Ana-2"), RECU);
        assertThat(file.etat().passagePrioritaireId()).isNull();
        assertThat(passage("Ana-2").numeroModification()).isEqualTo(2);
        inscrire(ANA, "Ana-3");
        verifierOrdre("Ana-1", "Boris-1", "Célia-1", "Boris-2", "Ana-3");
    }

    @Test
    void extractionAvecCourantEchoueMemeSiLaFileEstVideSansMutation() {
        EtatFileAttente avant = file.etat();
        assertThatThrownBy(() -> file.extraireProchainPassage(true)).isInstanceOf(IllegalStateException.class);
        assertThat(file.etat()).isEqualTo(avant);
        assertThat(file.extraireProchainPassage(false)).isEmpty();
        inscrireDeuxTours(); avant = file.etat();
        assertThatThrownBy(() -> file.extraireProchainPassage(true)).isInstanceOf(IllegalStateException.class);
        assertThat(file.etat()).isEqualTo(avant);
    }

    @Test
    void estimeDansLOrdreEffectifAvecDelaisEtPrevisionOuPause() {
        inscrireDeuxTours(); file.definirPriorite(cible("Ana-2"), true);
        var estimations = file.estimerAttentes(RECU, false, null, false, Duration.ofSeconds(30));
        assertThat(estimations.values()).extracting(AttenteEstimee::dureeMs)
                .containsExactly(0L, 210000L, 420000L, 630000L, 840000L);
        assertThat(estimations.values()).allSatisfy(a -> {
            assertThat(a.calculeeLe()).isEqualTo(RECU); assertThat(a.etat()).isEqualTo(EtatAttente.ESTIMEE);
        });
        assertThat(file.estimerAttentes(RECU, false, RECU.plusSeconds(12), true, Duration.ofSeconds(10)).values())
                .extracting(AttenteEstimee::dureeMs).containsExactly(12000L, 202000L, 392000L, 582000L, 772000L);
        assertThat(file.estimerAttentes(RECU, false, RECU.minusSeconds(12), true, Duration.ofSeconds(30)).values())
                .extracting(AttenteEstimee::dureeMs).containsExactly(0L, 210000L, 420000L, 630000L, 840000L);
        assertThat(file.estimerAttentes(RECU, true, null, true, Duration.ZERO).values()).allSatisfy(a -> {
            assertThat(a.etat()).isEqualTo(EtatAttente.SUSPENDUE); assertThat(a.dureeMs()).isNull();
        });
        assertThat(file.estimerAttentes(RECU, false, null, true, Duration.ZERO).values()).allSatisfy(a -> {
            assertThat(a.etat()).isEqualTo(EtatAttente.INCONNUE); assertThat(a.dureeMs()).isNull();
        });
        assertThatThrownBy(estimations::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void annuleLesAttentesEnUneDecisionEtConserveLesPassagesDejaAppeles() {
        inscrireDeuxTours(); file.extraireProchainPassage(false); file.definirPriorite(cible("Ana-2"), true);
        file.annulerAttentes(RECU, BORIS);
        verifierOrdre(); assertThat(passage("Ana-1").etat()).isEqualTo(EtatPassage.APPELE);
        assertThat(passage("Ana-2").numeroModification()).isEqualTo(2);
        assertThat(passage("Ana-2").traceTerminale()).isEqualTo(new TraceTerminale("MANCHE_TERMINEE", BORIS, RECU));
        assertThat(file.etat().passagePrioritaireId()).isNull();
        sansEffet(() -> file.annulerAttentes(RECU, null));
    }

    @Test
    void refuseLeCollectifDansCetteTrancheEtCopieLesLectures() {
        assertThatThrownBy(() -> new NouveauPassage(UUID.randomUUID(), SOIREE, MANCHE, ANA, List.of(ANA, BORIS),
                "1", Duration.ofSeconds(1), 1, RECU)).isInstanceOf(IllegalArgumentException.class);
        Passage p = inscrire(ANA, "1");
        Passage collectif = new Passage(p.id(), SOIREE, MANCHE, ANA, List.of(ANA, BORIS), "1", p.duree(), 1, 0,
                p.etat(), RECU, 1, 1, 1, null, null);
        assertThatThrownBy(() -> FileAttente.depuis(new EtatFileAttente(SOIREE, MANCHE, 1, 1, 1, 1, null,
                List.of(collectif), Map.of()))).isInstanceOf(IllegalArgumentException.class);
        var capture = file.etat(); var copie = FileAttente.depuis(capture);
        copie.retirer(BORIS, cible("1"), RECU);
        assertThat(file.etat()).isEqualTo(capture);
        assertThatThrownBy(() -> capture.passages().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> capture.derniersToursConsommes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> p.interpretes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> file.ordreEffectif().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> file.positions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

	private Passage inscrire(UUID interprete, String contenu) {
		UUID id = UUID.randomUUID();
		assertThat(file.inscrire(new NouveauPassage(id, SOIREE, MANCHE, interprete, List.of(interprete),
				contenu, Duration.ofMinutes(3), ++numero, RECU))).isInstanceOf(ResultatFile.Modifiee.class);
		return file.etat().passages().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
	}
}
