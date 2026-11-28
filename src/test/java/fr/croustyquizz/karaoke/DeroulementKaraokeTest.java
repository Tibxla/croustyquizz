package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.*;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DeroulementKaraokeTest extends ContratDeroulementDeManche {
	@Override
	protected DeroulementDeManche creer(ContexteDeManche contexte) {
		var identifiants = java.util.stream.LongStream.iterate(100, id -> id + 1).iterator();
		return new DeroulementKaraoke(contexte, new ReglagesKaraoke(2), Duration.ofSeconds(30),
				() -> new UUID(0, identifiants.nextLong()));
	}

	@Override
	protected ActionJoueur uneAction(UUID joueurId, Instant recueLe) {
		return inscription(joueurId, recueLe, "chanson", 180000L, EtatParoles.SYNCHRONISEES,
				List.of(new LigneParoles(0, "Des paroles")));
	}

	private InscrirePassage inscription(UUID joueur, Instant recueLe, String chansonId, Long duree,
			EtatParoles paroles, List<LigneParoles> lignes) {
		return new InscrirePassage(new Acteur(Acteur.Type.JOUEUR, joueur, SOIREE), recueLe, UUID.randomUUID(),
				new FaitsInscription(UUID.randomUUID(), 1), chansonId,
				new ContenuKaraoke(new Chanson(chansonId, 42, "Titre", "Artiste", null, duree, paroles), lignes));
	}

	private DeroulementKaraoke karaoke() { return (DeroulementKaraoke) deroulement; }

	@Test
	void inscritSansInformationAudioAvecUnAccuse201EtLeTempsDeReception() {
		deroulement.demarrer();
		Instant recueLe = horloge.maintenant().minusSeconds(3);
		InscrirePassage action = (InscrirePassage) uneAction(JOUEUR, recueLe);
		assertThat(deroulement.recevoir(action)).isInstanceOfSatisfying(Accepte.class, accepte -> {
			assertThat(accepte.statutHttp()).isEqualTo(201);
			assertThat(accepte.typeEvenement()).isEqualTo("file-attente-modifiee");
			assertThat(accepte.aRepublier()).containsExactlyInAnyOrder(Destination.SALLE,
					Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);
			assertThat(accepte.accuse()).isEqualTo(new AccusePassage(action.envoiId(), SOIREE, MANCHE,
					action.faits().passageId(), 0, "INSCRIRE", recueLe, EtatPassage.EN_ATTENTE));
		});
		assertThat(karaoke().instantane().file().passages().getFirst().inscriptionLe()).isEqualTo(recueLe);
		assertThat(karaoke().instantane().contenus()).containsEntry(action.faits().passageId(), action.contenu());
	}

    private void refuserSansMutation(java.util.function.Supplier<ResultatDeCommande> action,
            int statut, String code, String motif) {
        var avant = karaoke().instantane();
        int minuteurs = horloge.minuteursEnAttente();
        assertThat(action.get()).isInstanceOfSatisfying(Refuse.class, r -> {
            assertThat(r.statutHttp()).isEqualTo(statut); assertThat(r.code()).isEqualTo(code);
            if (motif != null) { assertThat(r.detail()).contains(motif); }
        });
        assertThat(karaoke().instantane()).isEqualTo(avant);
        assertThat(horloge.minuteursEnAttente()).isEqualTo(minuteurs);
    }

    @Test
    void appliqueLesMotifsDansLOrdreMemeQuandLeQuotaEstAtteint() {
        deroulement.demarrer(); deroulement.recevoir(uneAction(JOUEUR, horloge.maintenant()));
        deroulement.commander(new ReglerLimitePassages(pilotage().acteur(), horloge.maintenant(), UUID.randomUUID(), 1));
        for (EtatParoles etat : EtatParoles.values()) {
            if (etat == EtatParoles.SYNCHRONISEES) { continue; }
            var action = inscription(JOUEUR, horloge.maintenant(), "chanson", null, etat, List.of());
            refuserSansMutation(() -> deroulement.recevoir(action), 422, "CHANSON_INDISPONIBLE",
                    etat == EtatParoles.SANS_VOIX ? "SANS_VOIX" : "PAROLES_NON_SYNCHRONISEES");
        }
        for (Long duree : Arrays.asList(null, 0L, -1L)) {
            var action = inscription(JOUEUR, horloge.maintenant(), "chanson", duree,
                    EtatParoles.SYNCHRONISEES, List.of(new LigneParoles(0, "Paroles")));
            refuserSansMutation(() -> deroulement.recevoir(action), 422, "CHANSON_INDISPONIBLE", "DUREE_INCONNUE");
        }
        refuserSansMutation(() -> deroulement.recevoir(uneAction(JOUEUR, horloge.maintenant())),
                409, "PASSAGE_DEJA_INSCRIT", null);
        var autre = inscription(JOUEUR, horloge.maintenant(), "autre", 100L, EtatParoles.SYNCHRONISEES,
                List.of(new LigneParoles(0, "Paroles")));
        refuserSansMutation(() -> deroulement.recevoir(autre), 409, "LIMITE_ATTEINTE", null);
        var file = FileAttente.depuis(karaoke().instantane().file());
        assertThat(DeroulementKaraoke.motifInscription(file, JOUEUR, autre.contenu().chanson()))
                .contains(MotifIndisponibilite.LIMITE_ATTEINTE);
    }

    @Test
    void refuseLesLignesInexploitablesAvantLaDureeMaisAccepteUneLigneExploitable() {
        deroulement.demarrer();
        for (List<LigneParoles> lignes : List.of(List.<LigneParoles>of(), List.of(new LigneParoles(0, "  ")),
                List.of(new LigneParoles(-1, "Paroles")), List.of(new LigneParoles(0, "\t\n")))) {
            var action = inscription(JOUEUR, horloge.maintenant(), "chanson", null, EtatParoles.SYNCHRONISEES, lignes);
            refuserSansMutation(() -> deroulement.recevoir(action), 422, "CHANSON_INDISPONIBLE", "PAROLES_NON_SYNCHRONISEES");
        }
        var action = inscription(JOUEUR, horloge.maintenant(), "chanson", 100L, EtatParoles.SYNCHRONISEES,
                List.of(new LigneParoles(-1, "Ignorée"), new LigneParoles(0, "Paroles")));
        assertThat(deroulement.recevoir(action)).isInstanceOf(Accepte.class);
    }

    @Test
    void refuseUnIdentifiantIncoherentEtLesBornesInvalides() {
        var action = (InscrirePassage) uneAction(JOUEUR, horloge.maintenant());
        assertThatThrownBy(() -> new InscrirePassage(action.acteur(), action.recueLe(), action.envoiId(),
                action.faits(), "autre", action.contenu())).isInstanceOf(IllegalArgumentException.class);
        for (long limite : new long[] {0, -1, 21, 9007199254740991L}) {
            assertThatThrownBy(() -> new ReglagesKaraoke(limite)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ReglerLimitePassages(pilotage().acteur(), horloge.maintenant(), UUID.randomUUID(), limite))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EtatFileAttente(SOIREE, MANCHE, limite, 1, 1, 1, null, List.of(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new ReglagesKaraoke(20).limitePassagesEnAttente()).isEqualTo(20);
        assertThat(new ReglerLimitePassages(pilotage().acteur(), horloge.maintenant(), UUID.randomUUID(), 20)
                .limitePassagesEnAttente()).isEqualTo(20);
        assertThatThrownBy(() -> new DeplacerPassage(pilotage().acteur(), horloge.maintenant(), UUID.randomUUID(),
                new CiblePassage(UUID.randomUUID(), 0), 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DefinirPrioritePassage(pilotage().acteur(), horloge.maintenant(), UUID.randomUUID(), null, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void gardeLaMancheAvantLesCiblesInconnuesEtToutesLesCommandesDePilotage() {
        for (boolean terminee : new boolean[] {false, true}) {
            if (terminee) { deroulement.demarrer(); deroulement.terminer(pilotage()); }
            String code = terminee ? "ETAT_INCOMPATIBLE" : "ETAPE_INACTIVE";
            CiblePassage cible = new CiblePassage(UUID.randomUUID(), 0);
            refuserSansMutation(() -> deroulement.recevoir(uneAction(JOUEUR, horloge.maintenant())), 409, code, null);
            refuserSansMutation(() -> deroulement.recevoir(new DesinscrirePassage(pilotage().acteur(),
                    horloge.maintenant(), UUID.randomUUID(), cible)), 409, code, null);
            refuserSansMutation(() -> deroulement.commander(new RetirerPassage(pilotage().acteur(),
                    horloge.maintenant(), UUID.randomUUID(), cible)), 409, code, null);
            refuserSansMutation(() -> deroulement.commander(new DeplacerPassage(pilotage().acteur(),
                    horloge.maintenant(), UUID.randomUUID(), cible, 1, null)), 409, code, null);
            refuserSansMutation(() -> deroulement.commander(new DefinirPrioritePassage(pilotage().acteur(),
                    horloge.maintenant(), UUID.randomUUID(), cible, true)), 409, code, null);
            refuserSansMutation(() -> deroulement.commander(new ReglerLimitePassages(pilotage().acteur(),
                    horloge.maintenant(), UUID.randomUUID(), 2)), 409, code, null);
            refuserSansMutation(() -> deroulement.mettreEnPause(pilotage()), 409, code, null);
            refuserSansMutation(() -> deroulement.reprendre(pilotage()), 409, code, null);
            refuserSansMutation(() -> deroulement.passerEtape(pilotage()), 409, code, null);
            refuserSansMutation(() -> deroulement.terminer(pilotage()), 409, code, null);
        }
    }

    private CiblePassage cible(UUID id) {
        var p = karaoke().instantane().file().passages().stream().filter(passage -> passage.id().equals(id)).findFirst().orElseThrow();
        return new CiblePassage(id, p.numeroModification());
    }

    private Accepte accepter(ResultatDeCommande resultat, String type, boolean mutation) {
        assertThat(resultat).isInstanceOf(Accepte.class);
        Accepte accepte = (Accepte) resultat;
        assertThat(accepte.statutHttp()).isEqualTo(200); assertThat(accepte.typeEvenement()).isEqualTo(type);
        assertThat(accepte.aRepublier()).isEqualTo(mutation
                ? Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES) : Set.of());
        return accepte;
    }

    @Test
    void raccordeLesCommandesEtLeursAccusesAvecEtSansEffetEnPause() {
        deroulement.demarrer();
        var action = (InscrirePassage) uneAction(JOUEUR, horloge.maintenant()); deroulement.recevoir(action);
        UUID id = action.faits().passageId(); var acteur = pilotage().acteur(); Instant recu = horloge.maintenant().minusSeconds(1);
        UUID envoi = UUID.randomUUID();
        accepter(deroulement.mettreEnPause(pilotage()), "manche-en-pause", true);
        Accepte priorite = accepter(deroulement.commander(new DefinirPrioritePassage(acteur, recu, envoi, cible(id), true)),
                "file-attente-modifiee", true);
        assertThat(priorite.accuse()).isEqualTo(new AccuseFileAttente(envoi, SOIREE, MANCHE, "DEFINIR_PRIORITE", recu, cible(id)));
        var avant = karaoke().instantane();
        accepter(deroulement.commander(new DefinirPrioritePassage(acteur, recu, envoi, cible(id), true)), "file-attente-modifiee", false);
        assertThat(karaoke().instantane()).isEqualTo(avant);
        accepter(deroulement.commander(new DefinirPrioritePassage(acteur, recu, envoi, cible(id), false)), "file-attente-modifiee", true);
        Accepte placement = accepter(deroulement.commander(new DeplacerPassage(acteur, recu, envoi, cible(id), 1, null)),
                "file-attente-modifiee", false);
        assertThat(placement.accuse()).isEqualTo(new AccusePassage(envoi, SOIREE, MANCHE, id, 2, "DEPLACER", recu, EtatPassage.EN_ATTENTE));
        Accepte limite = accepter(deroulement.commander(new ReglerLimitePassages(acteur, recu, envoi, 1)), "limite-passages-modifiee", true);
        assertThat(limite.accuse()).isEqualTo(new AccuseFileAttente(envoi, SOIREE, MANCHE, "REGLER_LIMITE", recu, null));
        accepter(deroulement.commander(new ReglerLimitePassages(acteur, recu, envoi, 1)), "limite-passages-modifiee", false);
        Accepte retrait = accepter(deroulement.commander(new RetirerPassage(acteur, recu, envoi, cible(id))), "file-attente-modifiee", true);
        assertThat(retrait.accuse()).isEqualTo(new AccusePassage(envoi, SOIREE, MANCHE, id, 3, "RETIRER", recu, EtatPassage.RETIRE));
        refuserSansMutation(() -> deroulement.commander(new RetirerPassage(acteur, recu, envoi, cible(id))), 409, "COMMANDE_PERIMEE", null);
        assertThat(deroulement.estTerminee()).isFalse();
        var nouvelle = (InscrirePassage) uneAction(JOUEUR, recu); deroulement.recevoir(nouvelle);
        UUID nouveauId = nouvelle.faits().passageId();
        Accepte desinscription = accepter(deroulement.recevoir(new DesinscrirePassage(nouvelle.acteur(), recu, envoi, cible(nouveauId))),
                "file-attente-modifiee", true);
        assertThat(desinscription.accuse()).isEqualTo(new AccusePassage(envoi, SOIREE, MANCHE, nouveauId, 1, "DESINSCRIRE", recu, EtatPassage.RETIRE));
    }

    @Test
    void preserveLaFilePendantLeCycleEtHorodateLAnnulationAvecLaCommande() {
        deroulement.demarrer(); deroulement.recevoir(uneAction(JOUEUR, horloge.maintenant()));
        var file = karaoke().instantane().file();
        refuserSansMutation(() -> deroulement.reprendre(pilotage()), 409, "ETAT_INCOMPATIBLE", null);
        assertThat(deroulement.viseEtape(null)).isTrue(); assertThat(deroulement.viseEtape(UUID.randomUUID())).isFalse();
        refuserSansMutation(() -> deroulement.passerEtape(pilotage()), 409, "ETAPE_INACTIVE", null);
        accepter(deroulement.mettreEnPause(pilotage()), "manche-en-pause", true);
        refuserSansMutation(() -> deroulement.mettreEnPause(pilotage()), 409, "ETAT_INCOMPATIBLE", null);
        assertThat(karaoke().instantane().file()).isEqualTo(file);
        assertThat(karaoke().estimerAttentes().values()).extracting(AttenteEstimee::etat).containsOnly(EtatAttente.SUSPENDUE);
        horloge.avancer(Duration.ofSeconds(42));
        accepter(deroulement.reprendre(pilotage()), "manche-reprise", true);
        assertThat(karaoke().estimerAttentes().values()).containsExactly(new AttenteEstimee(horloge.maintenant(), EtatAttente.ESTIMEE, 0L));
        assertThat(karaoke().instantane().file()).isEqualTo(file);
        var commande = pilotage();
        Accepte fin = accepter(deroulement.terminer(commande), "manche-terminee", true);
        assertThat(fin.accuse()).isEqualTo(commande.accuse());
        var passage = karaoke().instantane().file().passages().getFirst();
        assertThat(passage.etat()).isEqualTo(EtatPassage.ANNULE);
        assertThat(passage.traceTerminale()).isEqualTo(new TraceTerminale("MANCHE_TERMINEE", commande.acteur().identifiant(), commande.recueLe()));
        assertThat(finsSignalees).isZero(); assertThat(deroulement.points()).isEmpty();
    }

    @Test
    void conserveLesConstructeursHistoriquesDAccepteEtCopieLesDestinations() {
        assertThat(new Accepte(null, Set.of(), "manche-demarree").statutHttp()).isEqualTo(204);
        assertThat(new Accepte("reçu", Set.of(), "manche-demarree").statutHttp()).isEqualTo(200);
        Set<Destination> destinations = new HashSet<>(Set.of(Destination.SALLE));
        Accepte accepte = new Accepte("reçu", destinations, "file-attente-modifiee", 201);
        destinations.clear(); assertThat(accepte.aRepublier()).containsExactly(Destination.SALLE);
        assertThatThrownBy(() -> new Accepte(null, Set.of(), "inconnu")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Accepte(null, Set.of(Destination.SALLE), "reponse-enregistree"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    private static final UUID PASSAGE = new UUID(0, 10);
    private static final UUID SUIVANT = new UUID(0, 11);
    private static final UUID ETAPE = new UUID(0, 100);
    private static final UUID INCONNU = new UUID(0, 20);
    private static final UUID ENVOI = new UUID(0, 21);
    private static final Acteur INTERPRETE = new Acteur(Acteur.Type.JOUEUR, JOUEUR, SOIREE);

    @Test
    void appelleLaPremiereInscriptionEnAutomatiqueEtConserveSonAccuse() {
        demarrerEnAutomatique();
        assertThat(projection().courant()).isNull();
        Instant recueLe = horloge.maintenant().minusSeconds(2);
        var action = (InscrirePassage) uneAction(JOUEUR, recueLe);
        UUID passageId = action.faits().passageId();
        assertThat(deroulement.recevoir(action)).isEqualTo(new Accepte(
                new AccusePassage(action.envoiId(), SOIREE, MANCHE, passageId, 0, "INSCRIRE", recueLe, EtatPassage.EN_ATTENTE),
                Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES), "passage-appele", 201));
        assertThat(passage(passageId).etat()).isEqualTo(EtatPassage.APPELE);
        assertThat(projection().appeleLe()).isEqualTo(recueLe);
        assertThat(projection().etapeId()).isEqualTo(ETAPE);
        assertThat(inscrire(SUIVANT)).isInstanceOfSatisfying(Accepte.class,
                resultat -> assertThat(resultat.typeEvenement()).isEqualTo("file-attente-modifiee"));
        assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.EN_ATTENTE);
    }

    private void demarrerEnAutomatique() {
        deroulement = creer(new ContexteDeManche(SOIREE, MANCHE, reglages(), horloge, () -> finsSignalees++, true));
        deroulement.demarrer();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void laFinPrevueOuvreLeVoteEtAppelleLaPrioriteEnAutomatique(boolean automatique) {
        ouvrirPremierPassage(automatique);
        inscrire(SUIVANT);
        inscrire(INCONNU);
        deroulement.commander(new DefinirPrioritePassage(pilotage().acteur(), horloge.maintenant(), ENVOI,
                cible(INCONNU), true));
        confirmerCourant();

        horloge.avancer(Duration.ofSeconds(180));

        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.TERMINE);
        assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.EN_ATTENTE);
        assertThat(passage(INCONNU).etat()).isEqualTo(automatique ? EtatPassage.APPELE : EtatPassage.EN_ATTENTE);
        assertThat(karaoke().causeDernierMinuteur()).isEqualTo(automatique ? "passage-appele" : "passage-termine");
        assertThat(karaoke().instantane().voteDuPublic().votes()).singleElement().satisfies(vote -> {
            assertThat(vote.passageId()).isEqualTo(PASSAGE);
            assertThat(vote.ouvert()).isTrue();
        });
        if (!automatique) { accepter(appeler(cible(INCONNU)), "passage-appele", true); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void seulLeDemarrageDuPassageSuivantMarqueLeVoteMemeApresUnSautEtUnNouvelAppel(boolean demarre) {
        ouvrirPassage();
        confirmerCourant();
        horloge.avancer(Duration.ofSeconds(210));
        inscrire(SUIVANT);
        inscrire(INCONNU);
        accepter(appeler(cible(SUIVANT)), "passage-appele", true);
        assertThat(karaoke().instantane().voteDuPublic().votes()).singleElement().satisfies(vote -> {
            assertThat(vote.ouvert()).isFalse();
            assertThat(vote.suivantDemarre()).isFalse();
        });
        if (demarre) {
            accepter(confirmer(INTERPRETE, cible(SUIVANT), projection().etapeId()), "passage-demarre", true);
            assertThat(karaoke().instantane().voteDuPublic().votes().getFirst().suivantDemarre()).isTrue();
        }
        accepter(deroulement.passerEtape(pilotage()), "passage-saute", true);
        accepter(appeler(cible(INCONNU)), "passage-appele", true);
        assertThat(karaoke().instantane().voteDuPublic().votes()).singleElement()
                .satisfies(vote -> assertThat(vote.suivantDemarre()).isEqualTo(demarre));
    }

    @ParameterizedTest
    @CsvSource({"true, false", "true, true", "false, false", "false, true"})
    void laFinLocaleDevanceLaFinPrevueEtAttendLaRepriseSiBesoin(boolean automatique, boolean enPause) {
        ouvrirPremierPassage(automatique);
        designer(LECTEUR);
        declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0);
        confirmerCourant();
        inscrire(SUIVANT);
        UUID lectureId = lecture().lectureId();
        horloge.avancer(Duration.ofSeconds(10));
        if (enPause) { deroulement.mettreEnPause(pilotage()); }

        boolean appelImmediat = automatique && !enPause;
        var fin = accepter(signaler(Signal.FIN, PASSAGE, ETAPE, lectureId, LECTEUR),
                appelImmediat ? "passage-appele" : "passage-termine", true);
        assertThat(fin.accuse()).isEqualTo(new AccuseLectureChanson(ENVOI, SOIREE, MANCHE, LECTEUR, PASSAGE,
                lectureId, "SIGNALER_FIN_LECTURE", horloge.maintenant()));
        assertThat(passage(PASSAGE).traceTerminale().motif()).isEqualTo(TraceTerminale.FIN_LECTURE);
        assertThat(passage(SUIVANT).etat()).isEqualTo(appelImmediat ? EtatPassage.APPELE : EtatPassage.EN_ATTENTE);
        UUID etapeApresFin = projection().etapeId();
        if (enPause) {
            assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_PAUSE);
            accepter(deroulement.reprendre(pilotage()), automatique ? "passage-appele" : "manche-reprise", true);
            etapeApresFin = projection().etapeId();
        }
        refuserSansMutation(() -> signaler(Signal.FIN, PASSAGE, ETAPE, lectureId, LECTEUR), 409, "COMMANDE_PERIMEE", null);
        horloge.avancer(Duration.ofMinutes(5));
        assertThat(projection().etapeId()).isEqualTo(etapeApresFin);
        assertThat(passage(PASSAGE).traceTerminale().motif()).isEqualTo(TraceTerminale.FIN_LECTURE);
        assertThat(passage(SUIVANT).etat()).isEqualTo(automatique ? EtatPassage.SAUTE : EtatPassage.EN_ATTENTE);
        assertThat(new FabriqueKaraoke(Duration.ofSeconds(30)).typeEvenementMinuteur(deroulement))
                .isEqualTo(automatique ? "manche-terminee" : "vote-clos");
    }

    @Test
    void laFinPrevueRendLeSignalLocalConcurrentPerimeSansSecondAppel() {
        ouvrirPremierPassage(true);
        designer(LECTEUR);
        declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0);
        confirmerCourant();
        inscrire(SUIVANT);
        inscrire(INCONNU);
        UUID lectureId = lecture().lectureId();
        horloge.avancer(Duration.ofMinutes(5));

        assertThat(karaoke().causeDernierMinuteur()).isEqualTo("passage-appele");
        assertThat(passage(PASSAGE).traceTerminale().motif()).isEqualTo(TraceTerminale.FIN_PREVUE);
        assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.APPELE);
        assertThat(passage(INCONNU).etat()).isEqualTo(EtatPassage.EN_ATTENTE);
        refuserSansMutation(() -> signaler(Signal.FIN, PASSAGE, ETAPE, lectureId, LECTEUR), 409, "COMMANDE_PERIMEE", null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void passerHorsPauseAppelleImmediatementSeulementEnAutomatique(boolean automatique) {
        ouvrirPremierPassage(automatique);
        inscrire(SUIVANT);
        var commande = pilotage();
        var recu = accepter(deroulement.passerEtape(commande), automatique ? "passage-appele" : "passage-saute", true);
        assertThat(recu.accuse()).isEqualTo(commande.accuse());
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.SAUTE);
        assertThat(passage(SUIVANT).etat()).isEqualTo(automatique ? EtatPassage.APPELE : EtatPassage.EN_ATTENTE);
        assertThat(passage(PASSAGE).traceTerminale()).isEqualTo(new TraceTerminale(
                "PASSE_PAR_ANIMATEUR", commande.acteur().identifiant(), commande.recueLe()));
        assertThat(horloge.minuteursEnAttente()).isEqualTo(automatique ? 1 : 0);
        assertThat(karaoke().instantane().voteDuPublic().votes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sauteALEcheanceSansVoteEtPublieLaCauseDuNouvelAppelSiLaFileAttend(boolean avecSuivant) {
        ouvrirPremierPassage(true);
        if (avecSuivant) { inscrire(SUIVANT); }

        horloge.avancer(Duration.ofSeconds(30));

        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.SAUTE);
        assertThat(passage(PASSAGE).traceTerminale()).isEqualTo(new TraceTerminale("ECHEANCE_APPEL", null, horloge.maintenant()));
        assertThat(karaoke().instantane().voteDuPublic().votes()).isEmpty();
        assertThat(new FabriqueKaraoke(Duration.ofSeconds(30)).typeEvenementMinuteur(deroulement))
                .isEqualTo(avecSuivant ? "passage-appele" : "manche-terminee");
        assertThat(projection().courant().id()).isEqualTo(avecSuivant ? SUIVANT : PASSAGE);
        assertThat(projection().etat()).isEqualTo(avecSuivant ? "APPELE" : "SAUTE");
        assertThat(deroulement.estTerminee()).isEqualTo(!avecSuivant);
        assertThat(finsSignalees).isEqualTo(avecSuivant ? 0 : 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void laRepriseApresUnePauseALEcheanceSauteLePassageAussitot(boolean avecSuivant) {
        // La pause arrive à l’échéance, avant le rappel du saut.
        horloge.programmer(Duration.ofSeconds(30), () -> deroulement.mettreEnPause(pilotage()));
        ouvrirPremierPassage(true);
        if (avecSuivant) { inscrire(SUIVANT); }
        horloge.avancer(Duration.ofSeconds(30));
        assertThat(projection().etat()).isEqualTo("APPELE");
        horloge.avancer(Duration.ofSeconds(10));

        accepter(deroulement.reprendre(pilotage()), avecSuivant ? "passage-appele" : "manche-terminee", true);

        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.SAUTE);
        assertThat(passage(PASSAGE).traceTerminale()).isEqualTo(new TraceTerminale("ECHEANCE_APPEL", null, horloge.maintenant()));
        assertThat(karaoke().instantane().voteDuPublic().votes()).isEmpty();
        assertThat(projection().courant().id()).isEqualTo(avecSuivant ? SUIVANT : PASSAGE);
        assertThat(projection().etat()).isEqualTo(avecSuivant ? "APPELE" : "SAUTE");
        assertThat(deroulement.estTerminee()).isEqualTo(!avecSuivant);
        assertThat(finsSignalees).isEqualTo(avecSuivant ? 0 : 1);
    }

    @Test
    void laRepriseQuiSauteLeDernierPassageAttendLaClotureDuVoteDuPrecedent() {
        // La pause arrive à l’échéance du suivant, qui est aussi celle du vote du premier Passage.
        horloge.programmer(Duration.ofSeconds(210), () -> deroulement.mettreEnPause(pilotage()));
        ouvrirPremierPassage(true);
        confirmerCourant();
        inscrire(SUIVANT);
        horloge.avancer(Duration.ofSeconds(210));
        assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.APPELE);
        assertThat(karaoke().instantane().voteDuPublic().votes()).isNotEmpty();
        horloge.avancer(Duration.ofSeconds(10));

        accepter(deroulement.reprendre(pilotage()), "manche-reprise", true);

        assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.SAUTE);
        assertThat(projection().etat()).isEqualTo("SAUTE");
        assertThat(deroulement.estTerminee()).isFalse();
        assertThat(finsSignalees).isZero();
        horloge.avancer(Duration.ofMillis(1));
        assertThat(deroulement.estTerminee()).isTrue();
        assertThat(finsSignalees).isEqualTo(1);
    }

    @Test
    void laFinDeMancheNeLaissePasserNiDesignationNiDeclarationEnAutomatique() {
        ouvrirPremierPassage(true);
        designer(LECTEUR);
        accepter(deroulement.terminer(pilotage()), "manche-terminee", true);
        var preparation = preparationLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0);

        refuserSansMutation(() -> designer(AUTRE_LECTEUR), 409, "ETAT_INCOMPATIBLE", null);
        refuserSansMutation(() -> declarer(cible(PASSAGE), ETAPE, LECTEUR, preparation), 409, "ETAT_INCOMPATIBLE", null);
        assertThat(finsSignalees).isZero();
    }

    @Test
    void confirmeEnAutomatiqueUneMillisecondeAvantLEcheance() {
        ouvrirPremierPassage(true);
        horloge.avancer(Duration.ofMillis(29_999));
        confirmerCourant();
        horloge.avancer(Duration.ofMillis(1));
        assertThat(projection().etat()).isEqualTo("EN_COURS");
        assertThat(horloge.minuteursEnAttente()).isEqualTo(1);
    }

    @Test
    void refuseLaConfirmationALEcheanceAvantLeRappelSansMutation() {
        horloge.programmer(Duration.ofSeconds(30), () -> {
            var autre = new Acteur(Acteur.Type.JOUEUR, INCONNU, SOIREE);
            refuserSansMutation(() -> confirmer(autre, cible(PASSAGE), ETAPE), 404, "RESSOURCE_INTROUVABLE", null);
            refuserSansMutation(() -> confirmer(INTERPRETE, cible(PASSAGE), INCONNU), 409, "COMMANDE_PERIMEE", null);
            refuserSansMutation(() -> confirmer(INTERPRETE, cible(PASSAGE), ETAPE), 409, "HORS_DELAI",
                    "Le temps pour confirmer est écoulé.");
            var tardive = new ConfirmerPresencePassage(INTERPRETE, horloge.maintenant().plusMillis(1),
                    ENVOI, cible(PASSAGE), ETAPE);
            refuserSansMutation(() -> deroulement.recevoir(tardive), 409, "HORS_DELAI", null);
        });
        ouvrirPremierPassage(true);

        horloge.avancer(Duration.ofSeconds(30));

        assertThat(projection().etat()).isEqualTo("SAUTE");
        assertThat(karaoke().causeDernierMinuteur()).isEqualTo("passage-saute");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lErreurEnAutomatiqueGardeLeCourantOuAttendLaRepriseApresPasser(boolean passer) {
        ouvrirPremierPassage(true);
        designer(LECTEUR);
        declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0);
        confirmerCourant();
        inscrire(SUIVANT);
        UUID lectureId = lecture().lectureId();
        accepter(signaler(Signal.ERREUR, PASSAGE, ETAPE, lectureId, LECTEUR), LECTURE_MODIFIEE, true);
        assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_PAUSE);
        refuserSansMutation(() -> deroulement.reprendre(pilotage()), 409, "ETAT_INCOMPATIBLE", null);
        if (passer) {
            accepter(deroulement.passerEtape(pilotage()), "passage-saute", true);
            assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.EN_ATTENTE);
            accepter(deroulement.reprendre(pilotage()), "passage-appele", true);
            assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.APPELE);
        } else {
            accepter(reprendreEnParoles(PASSAGE, ETAPE, lectureId), LECTURE_MODIFIEE, true);
            assertThat(lecture().source()).isEqualTo(SourceLecture.PAROLES_SEULES);
            assertThat(projection().etapeId()).isEqualTo(ETAPE);
            assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.EN_ATTENTE);
        }
        assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_COURS);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void uneInscriptionPendantLeDernierVotePeutAppelerSonPassage(boolean automatique) {
        ouvrirPremierPassage(automatique);
        confirmerCourant();
        horloge.avancer(Duration.ofMinutes(3));
        assertThat(karaoke().causeDernierMinuteur()).isEqualTo("passage-termine");
        assertThat(projection().courant().id()).isEqualTo(PASSAGE);
        assertThat(projection().actif()).isFalse();
        assertThat(deroulement.estTerminee()).isFalse();

        horloge.avancer(Duration.ofSeconds(1));
        assertThat(inscrire(SUIVANT)).isInstanceOfSatisfying(Accepte.class, accepte ->
                assertThat(accepte.typeEvenement()).isEqualTo(automatique ? "passage-appele" : "file-attente-modifiee"));
        assertThat(passage(SUIVANT).etat()).isEqualTo(automatique ? EtatPassage.APPELE : EtatPassage.EN_ATTENTE);
        assertThat(karaoke().instantane().voteDuPublic().votes()).singleElement()
                .satisfies(vote -> assertThat(vote.ouvert()).isTrue());
        horloge.avancer(Duration.ofSeconds(29));
        assertThat(karaoke().causeDernierMinuteur()).isEqualTo("vote-clos");
        assertThat(passage(SUIVANT).etat()).isEqualTo(automatique ? EtatPassage.APPELE : EtatPassage.EN_ATTENTE);
        assertThat(deroulement.estTerminee()).isFalse();
        assertThat(finsSignalees).isZero();
    }

    @Test
    void leDernierPassageSauteAttendEncoreLeVoteDuPrecedent() {
        ouvrirPremierPassage(true);
        confirmerCourant();
        inscrire(SUIVANT);
        horloge.avancer(Duration.ofMinutes(3));
        accepter(deroulement.passerEtape(pilotage()), "passage-saute", true);
        assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.SAUTE);
        assertThat(deroulement.estTerminee()).isFalse();
        assertThat(finsSignalees).isZero();

        horloge.avancer(Duration.ofSeconds(30));

        assertThat(deroulement.estTerminee()).isTrue();
        assertThat(finsSignalees).isEqualTo(1);
        assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.SAUTE);
    }

    @Test
    void uneMancheAutomatiqueDemarreeSansInscriptionResteOuverte() {
        demarrerEnAutomatique();
        horloge.avancer(Duration.ofHours(1));
        accepter(deroulement.commander(new ReglerLimitePassages(pilotage().acteur(), horloge.maintenant(), ENVOI, 3)),
                "limite-passages-modifiee", true);
        assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_COURS);
        assertThat(finsSignalees).isZero();
    }

    @ParameterizedTest
    @CsvSource({"true, false", "true, true", "false, false"})
    void passerLeDernierEnPauseAttendLaRepriseAvantDeTerminer(boolean automatique, boolean erreurLecture) {
        ouvrirPremierPassage(automatique);
        if (erreurLecture) {
            designer(LECTEUR);
            declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0);
            confirmerCourant();
            accepter(signaler(Signal.ERREUR, PASSAGE, ETAPE, lecture().lectureId(), LECTEUR), LECTURE_MODIFIEE, true);
        } else {
            accepter(deroulement.mettreEnPause(pilotage()), "manche-en-pause", true);
        }
        accepter(deroulement.passerEtape(pilotage()), "passage-saute", true);
        horloge.avancer(Duration.ofMinutes(1));
        assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_PAUSE);
        assertThat(finsSignalees).isZero();

        var commande = pilotage();
        var reprise = accepter(deroulement.reprendre(commande), automatique ? "manche-terminee" : "manche-reprise", true);
        assertThat(reprise.accuse()).isEqualTo(commande.accuse());
        assertThat(reprise.statutHttp()).isEqualTo(200);
        assertThat(deroulement.estTerminee()).isEqualTo(automatique);
        assertThat(finsSignalees).isEqualTo(automatique ? 1 : 0);
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.SAUTE);
    }

    @Test
    void retirerLeSeulPassageEnAttenteLaisseLeChantPuisLeVoteSePoursuivre() {
        ouvrirPremierPassage(true);
        confirmerCourant();
        inscrire(SUIVANT);
        accepter(deroulement.commander(new RetirerPassage(pilotage().acteur(), horloge.maintenant(),
                ENVOI, cible(SUIVANT))), "file-attente-modifiee", true);
        assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.RETIRE);
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.EN_COURS);
        assertThat(deroulement.estTerminee()).isFalse();

        horloge.avancer(Duration.ofMinutes(3));
        assertThat(deroulement.estTerminee()).isFalse();
        assertThat(finsSignalees).isZero();
        horloge.avancer(Duration.ofSeconds(30));
        assertThat(deroulement.estTerminee()).isTrue();
        assertThat(finsSignalees).isEqualTo(1);
        assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.RETIRE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lesDeuxOrdresDeFinDuDernierPassageNeSignalentLaFinDeMancheQuUneFois(boolean lecteurPremier) {
        ouvrirPremierPassage(true);
        designer(LECTEUR);
        declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0);
        confirmerCourant();
        UUID lectureId = lecture().lectureId();
        if (lecteurPremier) {
            horloge.avancer(Duration.ofMillis(299_999));
            accepter(signaler(Signal.FIN, PASSAGE, ETAPE, lectureId, LECTEUR), "passage-termine", true);
            horloge.avancer(Duration.ofMillis(1));
        } else {
            horloge.avancer(Duration.ofMinutes(5));
            refuserSansMutation(() -> signaler(Signal.FIN, PASSAGE, ETAPE, lectureId, LECTEUR),
                    409, "COMMANDE_PERIMEE", null);
        }
        assertThat(passage(PASSAGE).traceTerminale().motif())
                .isEqualTo(lecteurPremier ? TraceTerminale.FIN_LECTURE : TraceTerminale.FIN_PREVUE);
        assertThat(karaoke().instantane().voteDuPublic().votes()).hasSize(1);
        horloge.avancer(Duration.ofMinutes(1));
        assertThat(deroulement.estTerminee()).isTrue();
        assertThat(finsSignalees).isEqualTo(1);
    }

    @Test
    void enAutomatiqueLaCibleInconnueEtLaPausePrimentSurLeRefusDeLAppelManuel() {
        ouvrirPremierPassage(true);
        inscrire(SUIVANT);
        refuserSansMutation(() -> appeler(new CiblePassage(INCONNU, 0)), 404, "RESSOURCE_INTROUVABLE", null);
        refuserSansMutation(() -> appeler(new CiblePassage(SUIVANT, 99)), 409, "ETAT_INCOMPATIBLE",
                "En Pilotage Automatique, le Passage suivant est appelé seul.");
        deroulement.mettreEnPause(pilotage());
        refuserSansMutation(() -> appeler(new CiblePassage(INCONNU, 0)), 404, "RESSOURCE_INTROUVABLE", null);
        refuserSansMutation(() -> appeler(cible(SUIVANT)), 409, "ETAT_INCOMPATIBLE", "l’état de la Manche");
    }

    private void ouvrirPremierPassage(boolean automatique) {
        if (automatique) {
            demarrerEnAutomatique();
            inscrire(PASSAGE);
        } else {
            ouvrirPassage();
        }
    }

    @Test
    void appelleSelonLOrdreDesRefusEtViseUniquementLEtapeActive() {
        refuserSansMutation(() -> appeler(new CiblePassage(INCONNU, 0)), 409, "ETAPE_INACTIVE", null);
        deroulement.demarrer();
        inscrire(PASSAGE);
        inscrire(SUIVANT);
        deroulement.mettreEnPause(pilotage());
        refuserSansMutation(() -> appeler(new CiblePassage(INCONNU, 0)), 404, "RESSOURCE_INTROUVABLE", null);
        refuserSansMutation(() -> appeler(new CiblePassage(SUIVANT, 42)), 409, "ETAT_INCOMPATIBLE", null);
        deroulement.reprendre(pilotage());
        refuserSansMutation(() -> appeler(cible(SUIVANT)), 409, "COMMANDE_PERIMEE", null);
        var recu = accepter(appeler(cible(PASSAGE)), "passage-appele", true);
        assertThat(recu.accuse()).isEqualTo(new AccuseCyclePassage(ENVOI, SOIREE, MANCHE, PASSAGE,
                ETAPE, 1, "APPELER", horloge.maintenant(), EtatPassage.APPELE));
        assertThat(deroulement.viseEtape(ETAPE)).isTrue();
        assertThat(deroulement.viseEtape(null)).isFalse();
        assertThat(deroulement.viseEtape(INCONNU)).isFalse();
        refuserSansMutation(() -> appeler(cible(SUIVANT)), 409, "ETAT_INCOMPATIBLE", null);
    }

    @Test
    void confirmeSeulLInterpreteMemeApresLEcheanceEtUneSeuleFois() {
        ouvrirPassage();
        Acteur autre = new Acteur(Acteur.Type.JOUEUR, INCONNU, SOIREE);
        deroulement.mettreEnPause(pilotage());
        assertThat(deroulement.viseEtape(ETAPE)).isTrue();
        refuserSansMutation(() -> confirmer(autre, cible(PASSAGE), ETAPE), 404, "RESSOURCE_INTROUVABLE", null);
        refuserSansMutation(() -> confirmer(INTERPRETE, new CiblePassage(INCONNU, 0), ETAPE),
                404, "RESSOURCE_INTROUVABLE", null);
        refuserSansMutation(() -> confirmer(INTERPRETE, new CiblePassage(PASSAGE, 0), INCONNU),
                409, "ETAT_INCOMPATIBLE", null);
        deroulement.reprendre(pilotage());
        inscrire(SUIVANT);
        refuserSansMutation(() -> confirmer(INTERPRETE, cible(SUIVANT), ETAPE), 409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> confirmer(INTERPRETE, cible(PASSAGE), INCONNU), 409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> confirmer(INTERPRETE, new CiblePassage(PASSAGE, 0), ETAPE),
                409, "COMMANDE_PERIMEE", null);
        horloge.avancer(Duration.ofSeconds(31));
        assertThat(horloge.minuteursEnAttente()).isZero();
        CiblePassage appele = cible(PASSAGE);
        var recu = accepter(confirmer(INTERPRETE, appele, ETAPE), "passage-demarre", true);
        assertThat(recu.accuse()).isEqualTo(new AccuseCyclePassage(ENVOI, SOIREE, MANCHE, PASSAGE,
                ETAPE, 2, "CONFIRMER_PRESENCE", horloge.maintenant(), EtatPassage.EN_COURS));
        refuserSansMutation(() -> confirmer(INTERPRETE, appele, ETAPE), 409, "COMMANDE_PERIMEE", null);
        assertThat(horloge.minuteursEnAttente()).isEqualTo(1);
        assertThat(karaoke().instantane().lectures().get(PASSAGE)).isEqualTo(new LecturePassage(new UUID(0, 101),
                PASSAGE, null, EtatLecture.DEMARREE, SourceLecture.PAROLES_SEULES, null, MotifParolesSeules.ECRAN_NON_PRET,
                OrigineParoles.INSCRIPTION, new ParolesDuPassage(new UUID(0, 102), 180_000L,
                        List.of(new LigneParoles(12_000, "Des paroles"))), 42, 0, 0, null, null));
        assertThat(karaoke().instantane().cycle().dureeReference()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void estimeLesAttentesDepuisLeCourantPuisSaFinPrevue() {
        ouvrirPassage();
        inscrire(SUIVANT);
        assertThat(karaoke().estimerAttentes().get(SUIVANT).dureeMs()).isEqualTo(210_000L);
        horloge.avancer(Duration.ofSeconds(30));
        assertThat(karaoke().estimerAttentes().get(SUIVANT).etat()).isEqualTo(EtatAttente.INCONNUE);
        confirmer(INTERPRETE, cible(PASSAGE), ETAPE);
        assertThat(karaoke().estimerAttentes().get(SUIVANT).dureeMs()).isEqualTo(180_000L);
        var pause = pilotage();
        deroulement.mettreEnPause(pause);
        assertThat(karaoke().instantane().cycle().pauseDepuis()).isEqualTo(pause.recueLe());
        assertThat(horloge.minuteursEnAttente()).isZero();
        assertThat(karaoke().estimerAttentes().get(SUIVANT).etat()).isEqualTo(EtatAttente.SUSPENDUE);
        horloge.avancer(Duration.ofSeconds(10));
        deroulement.reprendre(pilotage());
        assertThat(karaoke().instantane().cycle().tempsPause()).isEqualTo(Duration.ofSeconds(10));
        assertThat(horloge.minuteursEnAttente()).isEqualTo(1);
    }

    @Test
    void deuxPausesPendantLAppelPreserventLeDelaiRestantEtLaDureeChantee() {
        ouvrirPassage();
        horloge.avancer(Duration.ofSeconds(5));
        deroulement.mettreEnPause(pilotage());
        horloge.avancer(Duration.ofSeconds(20));
        assertThat(projection().delaiAppelRestantMs()).isEqualTo(25_000);
        deroulement.reprendre(pilotage());
        horloge.avancer(Duration.ofSeconds(7));
        deroulement.mettreEnPause(pilotage());
        horloge.avancer(Duration.ofSeconds(10));
        assertThat(projection().delaiAppelRestantMs()).isEqualTo(18_000);
        deroulement.reprendre(pilotage());
        assertThat(projection().delaiAppelRestantMs()).isEqualTo(18_000);
        confirmerCourant();
        assertThat(karaoke().instantane().cycle().tempsPause()).isZero();
        assertThat(projection().finPrevueLe()).isEqualTo(horloge.maintenant().plusSeconds(180));
    }

    @Test
    void laPauseApresLEcheanceGardeUnDelaiNulEtAutoriseLaConfirmation() {
        ouvrirPassage();
        horloge.avancer(Duration.ofSeconds(31));
        deroulement.mettreEnPause(pilotage());
        horloge.avancer(Duration.ofSeconds(10));
        assertThat(projection().delaiAppelRestantMs()).isZero();
        deroulement.reprendre(pilotage());
        assertThat(projection().delaiAppelRestantMs()).isZero();
        confirmerCourant();
        assertThat(karaoke().instantane().cycle().tempsPause()).isZero();
    }

    @Test
    void sauteEnPausePuisAnnuleLeCourantEtLesAttentesEnTerminant() {
        ouvrirPassage();
        inscrire(SUIVANT);
        confirmer(INTERPRETE, cible(PASSAGE), ETAPE);
        deroulement.mettreEnPause(pilotage());
        var commande = pilotage();
        var saute = accepter(deroulement.passerEtape(commande), "passage-saute", true);
        assertThat(saute.accuse()).isEqualTo(commande.accuse());
        assertThat(passage(PASSAGE).traceTerminale()).isEqualTo(
                new TraceTerminale("PASSE_PAR_ANIMATEUR", commande.acteur().identifiant(), commande.recueLe()));
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.SAUTE);
        assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_PAUSE);
        assertThat(karaoke().instantane().lectures()).containsOnlyKeys(PASSAGE);
        assertThat(deroulement.viseEtape(null)).isTrue();
        assertThat(deroulement.viseEtape(ETAPE)).isFalse();
        refuserSansMutation(() -> deroulement.passerEtape(pilotage()), 409, "ETAPE_INACTIVE", null);
        deroulement.reprendre(pilotage());
        appeler(cible(SUIVANT));
        assertThat(karaoke().instantane().lectures()).isEmpty();
        inscrire(INCONNU);
        var terminer = pilotage();
        accepter(deroulement.terminer(terminer), "manche-terminee", true);
        var trace = new TraceTerminale("MANCHE_TERMINEE", terminer.acteur().identifiant(), terminer.recueLe());
        assertThat(passage(SUIVANT).etat()).isEqualTo(EtatPassage.ANNULE);
        assertThat(passage(SUIVANT).tourConsomme()).isEqualTo(2L);
        assertThat(passage(INCONNU).etat()).isEqualTo(EtatPassage.ANNULE);
        assertThat(passage(INCONNU).tourConsomme()).isNull();
        assertThat(passage(SUIVANT).traceTerminale()).isEqualTo(trace);
        assertThat(passage(INCONNU).traceTerminale()).isEqualTo(trace);
        assertThat(horloge.minuteursEnAttente()).isZero();
        assertThat(karaoke().instantane().voteDuPublic().votes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void laFinPrevueDuDernierPassageLaisseLeVoteSeCloreAvantUneFinAutomatique(boolean automatique) {
        ouvrirPremierPassage(automatique);
        var fabrique = new FabriqueKaraoke(Duration.ofSeconds(30));
        assertThat(karaoke().causeDernierMinuteur()).isNull();
        confirmer(INTERPRETE, cible(PASSAGE), ETAPE);
        horloge.avancer(Duration.ofMinutes(3));
        assertThat(fabrique.typeEvenementMinuteur(deroulement)).isEqualTo("passage-termine");
        assertThat(karaoke().causeDernierMinuteur()).isEqualTo("passage-termine");
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.TERMINE);
        assertThat(passage(PASSAGE).traceTerminale()).isEqualTo(new TraceTerminale("FIN_PREVUE", null, horloge.maintenant()));
        assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_COURS);
        assertThat(karaoke().instantane().lectures()).containsOnlyKeys(PASSAGE);
        assertThat(deroulement.points()).isEmpty();
        assertThat(finsSignalees).isZero();
        Instant ouverture = horloge.maintenant();
        assertThat(karaoke().instantane().voteDuPublic().votes()).containsExactly(
                new EtatVotePassage(PASSAGE, ouverture, ouverture.plusSeconds(30), null, null, List.of()));
        Acteur votant = new Acteur(Acteur.Type.JOUEUR, INCONNU, SOIREE);
        var vote = new VoterPassage(votant, ouverture, ENVOI, cible(PASSAGE), 4);
        assertThat(deroulement.recevoir(vote)).isEqualTo(new Accepte(
                new AccuseVote(ENVOI, SOIREE, MANCHE, PASSAGE, 4, ouverture),
                Set.of(Destination.SALLE, Destination.CONSOLE, Destination.joueur(INCONNU)), "vote-enregistre", 200));
        horloge.avancer(Duration.ofMillis(29_999));
        assertThat(deroulement.estTerminee()).isFalse();
        assertThat(finsSignalees).isZero();
        horloge.avancer(Duration.ofMillis(1));
        assertThat(fabrique.typeEvenementMinuteur(deroulement)).isEqualTo(automatique ? "manche-terminee" : "vote-clos");
        assertThat(karaoke().instantane().voteDuPublic().votes()).containsExactly(
                new EtatVotePassage(PASSAGE, ouverture, horloge.maintenant(), null, horloge.maintenant(),
                        List.of(new VoteRecu(INCONNU, 4, ouverture))));
        assertThat(deroulement.points()).isEmpty();
        assertThat(deroulement.estTerminee()).isEqualTo(automatique);
        assertThat(finsSignalees).isEqualTo(automatique ? 1 : 0);
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.TERMINE);
    }

    @Test
    void unPassageAPlusieursSeConfirmeParNimporteQuelInterpreteEtRefuseLeVoteATous() {
        deroulement.demarrer();
        UUID invite = new UUID(0, 50);
        UUID silencieux = new UUID(0, 51);
        Acteur public_ = new Acteur(Acteur.Type.JOUEUR, new UUID(0, 52), SOIREE);
        refuserSansMutation(() -> deroulement.recevoir(inscriptionAvecInvites("moi", List.of(invite, JOUEUR), true)),
                422, "DONNEES_INVALIDES", null);
        refuserSansMutation(() -> deroulement.recevoir(inscriptionAvecInvites("absent", List.of(invite), false)),
                404, "RESSOURCE_INTROUVABLE", null);
        accepter201(deroulement.recevoir(inscriptionAvecInvites(PASSAGE.toString(), List.of(invite, silencieux), true)));
        assertThat(passage(PASSAGE).interpretes()).containsExactly(JOUEUR);

        // La pause ne suspend pas les gestes de la File d’attente, réponses aux Invitations comprises.
        accepter(deroulement.mettreEnPause(pilotage()), "manche-en-pause", true);
        Accepte acceptation = accepter(repondre(invite, true), "file-attente-modifiee", true);
        assertThat(acceptation.accuse()).isEqualTo(new AccusePassage(ENVOI, SOIREE, MANCHE, PASSAGE, 1, "ACCEPTER",
                horloge.maintenant(), EtatPassage.EN_ATTENTE));
        accepter(deroulement.reprendre(pilotage()), "manche-reprise", true);
        refuserSansMutation(() -> repondre(new UUID(0, 53), true), 404, "RESSOURCE_INTROUVABLE", null);
        refuserSansMutation(() -> repondre(invite, false), 409, "COMMANDE_PERIMEE", null);

        accepter(appeler(cible(PASSAGE)), "passage-appele", true);
        assertThat(karaoke().instantane().file().invitations()).extracting(InvitationPassage::etat)
                .containsExactly(InvitationPassage.Etat.ACCEPTEE, InvitationPassage.Etat.EXPIREE);
        UUID etape = karaoke().instantane().cycle().etapeId();
        var cibleAppel = cible(PASSAGE);
        accepter(confirmer(new Acteur(Acteur.Type.JOUEUR, invite, SOIREE), cibleAppel, etape), "passage-demarre", true);
        refuserSansMutation(() -> confirmer(INTERPRETE, cibleAppel, etape), 409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> confirmer(new Acteur(Acteur.Type.JOUEUR, silencieux, SOIREE), cible(PASSAGE), etape),
                404, "RESSOURCE_INTROUVABLE", null);
        horloge.avancer(Duration.ofMinutes(3));
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.TERMINE);
        for (UUID interprete : List.of(JOUEUR, invite)) {
            refuserSansMutation(() -> deroulement.recevoir(new VoterPassage(new Acteur(Acteur.Type.JOUEUR, interprete, SOIREE),
                    horloge.maintenant(), ENVOI, cible(PASSAGE), 5)), 409, "VOTE_INTERDIT", null);
        }
        assertThat(deroulement.recevoir(new VoterPassage(public_, horloge.maintenant(), ENVOI, cible(PASSAGE), 5)))
                .isInstanceOf(Accepte.class);
    }

    private InscrirePassage inscriptionAvecInvites(String chansonId, List<UUID> invites, boolean presents) {
        UUID id = chansonId.equals(PASSAGE.toString()) ? PASSAGE : UUID.randomUUID();
        var contenu = new ContenuKaraoke(new Chanson(chansonId, 42, "Titre", "Artiste", null, 180_000L,
                EtatParoles.SYNCHRONISEES), List.of(new LigneParoles(12_000, "Des paroles")));
        return new InscrirePassage(INTERPRETE, horloge.maintenant(), ENVOI, new FaitsInscription(id, 1), chansonId,
                contenu, invites, presents);
    }

    private ResultatDeCommande repondre(UUID joueur, boolean accepte) {
        return deroulement.recevoir(new RepondreInvitation(new Acteur(Acteur.Type.JOUEUR, joueur, SOIREE),
                horloge.maintenant(), ENVOI, cible(PASSAGE), accepte));
    }

    private void accepter201(ResultatDeCommande resultat) {
        assertThat(resultat).isInstanceOfSatisfying(Accepte.class, a -> assertThat(a.statutHttp()).isEqualTo(201));
    }

    private void ouvrirPassage() {
        deroulement.demarrer();
        inscrire(PASSAGE);
        accepter(appeler(cible(PASSAGE)), "passage-appele", true);
    }

    private ResultatDeCommande inscrire(UUID id) {
        var contenu = new ContenuKaraoke(new Chanson(id.toString(), 42, "Titre", "Artiste", null, 180_000L,
                EtatParoles.SYNCHRONISEES), List.of(new LigneParoles(12_000, "Des paroles")));
        var resultat = deroulement.recevoir(new InscrirePassage(INTERPRETE, horloge.maintenant(), ENVOI,
                new FaitsInscription(id, 1), id.toString(), contenu));
        assertThat(resultat).isInstanceOf(Accepte.class);
        return resultat;
    }

    private ResultatDeCommande appeler(CiblePassage cible) {
        return deroulement.commander(new AppelerPassage(pilotage().acteur(), horloge.maintenant(), ENVOI, cible));
    }

    private ResultatDeCommande confirmer(Acteur joueur, CiblePassage cible, UUID etape) {
        var action = new ConfirmerPresencePassage(joueur, horloge.maintenant(), ENVOI, cible, etape);
        assertThat(action.joueurId()).isEqualTo(joueur.identifiant());
        return deroulement.recevoir(action);
    }

    private Passage passage(UUID id) {
        return karaoke().instantane().file().passages().stream()
                .filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }
    private static final UUID LECTEUR = new UUID(0, 40);
    private static final UUID AUTRE_LECTEUR = new UUID(0, 41);
    private static final Acteur ECRAN = new Acteur(Acteur.Type.ECRAN, new UUID(0, 42), SOIREE);
    private static final String LECTURE_MODIFIEE = "lecture-chanson-modifiee";
    private static final String CALAGE_MODIFIE = "calage-chanson-modifie";
    private static final Set<Destination> SALLE_ET_CONSOLE = Set.of(Destination.SALLE, Destination.CONSOLE);
    private static final List<LigneParoles> LIGNES_ADAPTEES = List.of(new LigneParoles(0, ""),
            new LigneParoles(12_000, "Première ligne"), new LigneParoles(15_000, "Deuxième ligne"));

    @Test
    void designeUnLecteurSansEffetAuRejeuEtRetireLaPreparationDeLAncienLecteur() {
        ouvrirPassage();
        assertThat(accepterLecture(designer(LECTEUR), LECTURE_MODIFIEE, SALLE_ET_CONSOLE).accuse())
                .isEqualTo(new AccuseLectureChanson(ENVOI, SOIREE, MANCHE, LECTEUR,
                        null, null, "DESIGNER_LECTEUR", horloge.maintenant()));
        declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 75);
        var avant = karaoke().instantane();
        accepterLecture(designer(LECTEUR), LECTURE_MODIFIEE, Set.of());
        assertThat(karaoke().instantane()).isEqualTo(avant);
        accepterLecture(designer(AUTRE_LECTEUR), LECTURE_MODIFIEE, SALLE_ET_CONSOLE);
        assertThat(karaoke().instantane().lecteurDesigneId()).isEqualTo(AUTRE_LECTEUR);
        assertThat(karaoke().instantane().lectures()).isEmpty();
        confirmerCourant();
        assertThat(lecture().motifParolesSeules()).isEqualTo(MotifParolesSeules.ECRAN_NON_PRET);
        assertThat(lecture().lecteurId()).isNull();
    }

    @Test
    void laDeclarationGardePassageEtapeCompteurPuisLecteurEtRemplaceLaPreparation() {
        ouvrirPassage();
        inscrire(SUIVANT);
        var preparation = preparationLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, -75);
        refuserSansMutation(() -> declarer(new CiblePassage(INCONNU, 0), INCONNU, AUTRE_LECTEUR, preparation),
                404, "RESSOURCE_INTROUVABLE", null);
        refuserSansMutation(() -> declarer(cible(SUIVANT), ETAPE, AUTRE_LECTEUR, preparation),
                409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> declarer(cible(PASSAGE), INCONNU, AUTRE_LECTEUR, preparation),
                409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> declarer(new CiblePassage(PASSAGE, 0), ETAPE, AUTRE_LECTEUR, preparation),
                409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> declarer(cible(PASSAGE), ETAPE, LECTEUR, preparation),
                409, "LECTEUR_NON_DESIGNE", null);
        designer(LECTEUR);
        refuserSansMutation(() -> declarer(cible(PASSAGE), ETAPE, AUTRE_LECTEUR, preparation),
                409, "LECTEUR_NON_DESIGNE", null);
        var appele = passage(PASSAGE);
        deroulement.mettreEnPause(pilotage());
        var recu = accepterLecture(declarer(cible(PASSAGE), ETAPE, LECTEUR, preparation), LECTURE_MODIFIEE, SALLE_ET_CONSOLE);
        var premiere = lecture();
        assertThat(recu.accuse()).isEqualTo(new AccuseLectureChanson(ENVOI, SOIREE, MANCHE, LECTEUR,
                PASSAGE, premiere.lectureId(), "DECLARER_LECTURE", horloge.maintenant()));
        assertThat(premiere).isEqualTo(new LecturePassage(new UUID(0, 101), PASSAGE, LECTEUR, EtatLecture.PREPAREE,
                SourceLecture.FICHIER_LOCAL, preparation.fichier(), null, OrigineParoles.VERSION_ADAPTEE,
                new ParolesDuPassage(new UUID(0, 102), 290_000L, LIGNES_ADAPTEES), 43, -75, 0, null, null));
        declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 50);
        assertThat(lecture().lectureId()).isNotEqualTo(premiere.lectureId());
        assertThat(lecture().decalageMs()).isEqualTo(50);
        assertThat(passage(PASSAGE)).isEqualTo(appele);
        deroulement.reprendre(pilotage());
        confirmerCourant();
        refuserSansMutation(() -> declarer(cible(PASSAGE), ETAPE, LECTEUR, preparation), 409, "COMMANDE_PERIMEE", null);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @org.junit.jupiter.params.provider.ValueSource(longs = {0, 300_000})
    void conserveLaDureeDuFichierConnuOuPrendCelleDesParoles(Long dureeFichier) {
        ouvrirPassage();
        designer(LECTEUR);
        declarerLocale(dureeFichier, dureeFichier == null || dureeFichier == 0
                ? OrigineParoles.REPLI_INSCRIPTION : OrigineParoles.VERSION_ADAPTEE, 0);
        var preparee = lecture();
        confirmerCourant();
        assertThat(lecture().lectureId()).isEqualTo(preparee.lectureId());
        assertThat(lecture().paroles()).isEqualTo(preparee.paroles());
        assertThat(lecture().etat()).isEqualTo(EtatLecture.DEMARREE);
        long duree = Long.valueOf(300_000).equals(dureeFichier) ? 300_000 : 290_000;
        var projection = projection();
        assertThat(projection.finPrevueLe()).isEqualTo(projection.demarreLe().plusMillis(duree));
        horloge.avancer(Duration.ofMillis(duree - 1));
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.EN_COURS);
        horloge.avancer(Duration.ofMillis(1));
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.TERMINE);
    }

    @Test
    void unePreparationEnParolesSeulesDemarreAvecSaPropreDuree() {
        ouvrirPassage();
        designer(LECTEUR);
        var preparation = new LecturePreparee(SourceLecture.PAROLES_SEULES, null, MotifParolesSeules.FICHIER_ABSENT,
                OrigineParoles.INSCRIPTION, 170_000L, LIGNES_ADAPTEES, 42, 0);
        declarer(cible(PASSAGE), ETAPE, LECTEUR, preparation);
        var preparee = lecture();
        confirmerCourant();
        assertThat(lecture().lecteurId()).isEqualTo(LECTEUR);
        assertThat(lecture().motifParolesSeules()).isEqualTo(MotifParolesSeules.FICHIER_ABSENT);
        assertThat(lecture().paroles()).isEqualTo(preparee.paroles());
        assertThat(karaoke().instantane().contenus().get(PASSAGE).chanson().dureeMs()).isEqualTo(180_000);
        assertThat(projection().finPrevueLe()).isEqualTo(horloge.maintenant().plusSeconds(170));
        for (Signal signal : Signal.values()) {
            refuserSansMutation(() -> signaler(signal, PASSAGE, ETAPE, lecture().lectureId(), LECTEUR),
                    409, signal == Signal.POSITION ? "COMMANDE_PERIMEE" : "ETAT_INCOMPATIBLE", null);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(Signal.class)
    void gardeLesSignauxDansLOrdrePassageEtapeLectureDemandeLecteurPuisEtat(Signal signal) {
        ouvrirPassage();
        inscrire(SUIVANT);
        designer(LECTEUR);
        declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0);
        UUID premiere = lecture().lectureId();
        declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0);
        UUID lectureId = lecture().lectureId();
        refuserSansMutation(() -> signaler(signal, INCONNU, INCONNU, premiere, AUTRE_LECTEUR),
                404, "RESSOURCE_INTROUVABLE", null);
        refuserSansMutation(() -> signaler(signal, SUIVANT, ETAPE, lectureId, AUTRE_LECTEUR),
                409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> signaler(signal, PASSAGE, INCONNU, lectureId, AUTRE_LECTEUR),
                409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> signaler(signal, PASSAGE, ETAPE, premiere, AUTRE_LECTEUR),
                409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> signaler(signal, PASSAGE, ETAPE, lectureId, AUTRE_LECTEUR),
                409, signal == Signal.POSITION ? "COMMANDE_PERIMEE" : "LECTEUR_NON_DESIGNE", null);
        refuserSansMutation(() -> signaler(signal, PASSAGE, ETAPE, lectureId, LECTEUR),
                409, signal == Signal.POSITION ? "COMMANDE_PERIMEE" : "ETAT_INCOMPATIBLE", null);
        confirmerCourant();
        deroulement.passerEtape(pilotage());
        refuserSansMutation(() -> signaler(signal, PASSAGE, ETAPE, lectureId, LECTEUR),
                409, "COMMANDE_PERIMEE", null);
    }

    @Test
    void rejetteLeReleveDeLaDemandeEffaceeParUneErreurAvantDeVerifierLEtatDeLecture() {
        chanterSurFichier(OrigineParoles.VERSION_ADAPTEE);
        UUID lectureId = lecture().lectureId();
        marquerDebut(PASSAGE, ETAPE, lectureId);
        UUID demande = lecture().demandeId();
        assertThat(demande).isNotNull();
        refuserSansMutation(() -> deroulement.commander(new ReleverPositionLecture(ECRAN, horloge.maintenant(),
                ENVOI, PASSAGE, ETAPE, lectureId, AUTRE_LECTEUR, demande, 13_500)), 409, "LECTEUR_NON_DESIGNE", null);
        accepter(signaler(Signal.ERREUR, PASSAGE, ETAPE, lectureId, LECTEUR), LECTURE_MODIFIEE, true);
        refuserSansMutation(() -> relever(lectureId, demande, 13_500), 409, "COMMANDE_PERIMEE", null);
    }

    @Test
    void laFinDuLecteurInitialClotLeChantEnPauseEtEffaceLaDemande() {
        chanterSurFichier(OrigineParoles.VERSION_ADAPTEE);
        marquerDebut(PASSAGE, ETAPE, lecture().lectureId());
        designer(AUTRE_LECTEUR);
        UUID lectureId = lecture().lectureId();
        assertThat(lecture().lecteurId()).isEqualTo(LECTEUR);
        horloge.avancer(Duration.ofSeconds(10));
        deroulement.mettreEnPause(pilotage());
        horloge.avancer(Duration.ofSeconds(1));
        refuserSansMutation(() -> signaler(Signal.FIN, PASSAGE, ETAPE, lectureId, AUTRE_LECTEUR),
                409, "LECTEUR_NON_DESIGNE", null);
        var fin = accepter(signaler(Signal.FIN, PASSAGE, ETAPE, lectureId, LECTEUR), "passage-termine", true);
        assertThat(fin.accuse()).isEqualTo(new AccuseLectureChanson(ENVOI, SOIREE, MANCHE, LECTEUR,
                PASSAGE, lectureId, "SIGNALER_FIN_LECTURE", horloge.maintenant()));
        assertThat(passage(PASSAGE).traceTerminale()).isEqualTo(new TraceTerminale("FIN_LECTURE", null, horloge.maintenant()));
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.TERMINE);
        assertThat(lecture().demandeId()).isNull();
        assertThat(lecture().demandeeLe()).isNull();
        assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_PAUSE);
        assertThat(karaoke().instantane().cycle().tempsPause()).isZero();
        assertThat(karaoke().causeDernierMinuteur()).isNull();
        Instant ouverture = horloge.maintenant();
        horloge.avancer(Duration.ofMinutes(6));
        assertThat(passage(PASSAGE).numeroModification()).isEqualTo(3);
        assertThat(karaoke().instantane().voteDuPublic().votes()).containsExactly(
                new EtatVotePassage(PASSAGE, ouverture, ouverture.plusSeconds(30), ouverture, null, List.of()));
        deroulement.reprendre(pilotage());
        horloge.avancer(Duration.ofSeconds(30));
        assertThat(karaoke().instantane().voteDuPublic().votes().getFirst().clotureLe()).isEqualTo(horloge.maintenant());
        assertThat(karaoke().causeDernierMinuteur()).isEqualTo("vote-clos");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "SAUTE, passage-saute", "ANNULE, manche-terminee", "TERMINE, passage-termine"
    })
    void cloreHorsPauseAnnuleLeMinuteurDeFin(EtatPassage terminal, String cause) {
        chanterSurFichier(OrigineParoles.VERSION_ADAPTEE);
        assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_COURS);
        assertThat(passage(PASSAGE).etat()).isEqualTo(EtatPassage.EN_COURS);
        assertThat(horloge.minuteursEnAttente()).isEqualTo(1);
        var resultat = switch (terminal) {
            case SAUTE -> deroulement.passerEtape(pilotage());
            case ANNULE -> deroulement.terminer(pilotage());
            case TERMINE -> signaler(Signal.FIN, PASSAGE, ETAPE, lecture().lectureId(), LECTEUR);
            default -> throw new AssertionError(terminal);
        };
        assertThat(horloge.minuteursEnAttente()).isEqualTo(terminal == EtatPassage.TERMINE ? 1 : 0);
        assertThat(karaoke().instantane().voteDuPublic().votes()).hasSize(terminal == EtatPassage.TERMINE ? 1 : 0);
        accepter(resultat, cause, true);
        assertThat(passage(PASSAGE).etat()).isEqualTo(terminal);
    }

    @Test
    void unSignalDeFinApresLaFinPrevueEstPerime() {
        chanterSurFichier(OrigineParoles.VERSION_ADAPTEE);
        horloge.avancer(Duration.ofSeconds(300));
        refuserSansMutation(() -> signaler(Signal.FIN, PASSAGE, ETAPE, lecture().lectureId(), LECTEUR),
                409, "COMMANDE_PERIMEE", null);
        assertThat(passage(PASSAGE).traceTerminale().motif()).isEqualTo("FIN_PREVUE");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = OrigineParoles.class,
            names = {"VERSION_ADAPTEE", "REPLI_INSCRIPTION"})
    void lerreurSuspendPuisLesParolesSeulesReprennentLaMemeChronologie(OrigineParoles origine) {
        chanterSurFichier(origine);
        UUID lectureId = lecture().lectureId();
        refuserSansMutation(() -> reprendreEnParoles(PASSAGE, ETAPE, lectureId), 409, "ETAT_INCOMPATIBLE", null);
        refuserSansMutation(() -> reprendreEnParoles(PASSAGE, INCONNU, lectureId), 409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> reprendreEnParoles(PASSAGE, ETAPE, INCONNU), 409, "COMMANDE_PERIMEE", null);
        refuserSansMutation(() -> reprendreEnParoles(INCONNU, ETAPE, lectureId), 404, "RESSOURCE_INTROUVABLE", null);
        horloge.avancer(Duration.ofSeconds(10));
        marquerDebut(PASSAGE, ETAPE, lectureId);
        var depart = projection().demarreLe();
        var avantErreur = lecture();
        var erreur = accepter(signaler(Signal.ERREUR, PASSAGE, ETAPE, lectureId, LECTEUR), LECTURE_MODIFIEE, true);
        assertThat(erreur.accuse()).isEqualTo(new AccuseLectureChanson(ENVOI, SOIREE, MANCHE, LECTEUR,
                PASSAGE, lectureId, "SIGNALER_ERREUR_LECTURE", horloge.maintenant()));
        assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_PAUSE);
        assertThat(karaoke().instantane().cycle().pauseDepuis()).isEqualTo(horloge.maintenant());
        assertThat(lecture().etat()).isEqualTo(EtatLecture.EN_ERREUR);
        assertThat(lecture().demandeId()).isNull();
        assertThat(horloge.minuteursEnAttente()).isZero();
        refuserSansMutation(() -> deroulement.reprendre(pilotage()), 409, "ETAT_INCOMPATIBLE", null);
        refuserSansMutation(() -> signaler(Signal.FIN, PASSAGE, ETAPE, lectureId, LECTEUR), 409, "ETAT_INCOMPATIBLE", null);
        refuserSansMutation(() -> signaler(Signal.POSITION, PASSAGE, ETAPE, lectureId, LECTEUR), 409, "COMMANDE_PERIMEE", null);
        var enErreur = karaoke().instantane();
        horloge.avancer(Duration.ofSeconds(8));
        accepter(signaler(Signal.ERREUR, PASSAGE, ETAPE, lectureId, LECTEUR), LECTURE_MODIFIEE, false);
        assertThat(karaoke().instantane()).isEqualTo(enErreur);
        var reprise = accepter(reprendreEnParoles(PASSAGE, ETAPE, lectureId), LECTURE_MODIFIEE, true);
        assertThat(reprise.accuse()).isEqualTo(new AccuseCyclePassage(ENVOI, SOIREE, MANCHE, PASSAGE, ETAPE, 2,
                "REPRENDRE_EN_PAROLES_SEULES", horloge.maintenant(), EtatPassage.EN_COURS));
        assertThat(karaoke().instantane().etatManche()).isEqualTo(EtatManche.EN_COURS);
        assertThat(lecture()).isEqualTo(new LecturePassage(lectureId, PASSAGE, LECTEUR, EtatLecture.DEMARREE,
                SourceLecture.PAROLES_SEULES, null, MotifParolesSeules.ERREUR_LECTURE, origine, avantErreur.paroles(),
                avantErreur.versionSourceId(), avantErreur.decalageMs(), avantErreur.numeroModificationCalage(), null, null));
        assertThat(projection().finPrevueLe()).isEqualTo(depart.plusSeconds(308));
        assertThat(projection().positionChronologiqueMs()).isEqualTo(10_000L);
        assertThat(horloge.minuteursEnAttente()).isEqualTo(1);
    }

    @Test
    void lerreurPendantUnePauseManuelleGardeSonDebutEtBloqueLaRepriseSimple() {
        chanterSurFichier(OrigineParoles.VERSION_ADAPTEE);
        horloge.avancer(Duration.ofSeconds(10));
        var pause = pilotage();
        deroulement.mettreEnPause(pause);
        horloge.avancer(Duration.ofSeconds(5));
        accepter(signaler(Signal.ERREUR, PASSAGE, ETAPE, lecture().lectureId(), LECTEUR), LECTURE_MODIFIEE, true);
        assertThat(karaoke().instantane().cycle().pauseDepuis()).isEqualTo(pause.recueLe());
        refuserSansMutation(() -> deroulement.reprendre(pilotage()), 409, "ETAT_INCOMPATIBLE", null);
    }

    @Test
    void leCalageControleLaPausePuisLesIdentifiantsPuisLeDepart() {
        ouvrirPassage();
        designer(LECTEUR);
        declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0);
        UUID lectureId = lecture().lectureId();
        deroulement.mettreEnPause(pilotage());
        verifierRefusCalage(PASSAGE, INCONNU, INCONNU, "ETAT_INCOMPATIBLE");
        verifierPassageIntrouvablePourLaRepriseEtLeCalage();
        deroulement.reprendre(pilotage());
        verifierPassageIntrouvablePourLaRepriseEtLeCalage();
        verifierRefusCalage(PASSAGE, INCONNU, lectureId, "COMMANDE_PERIMEE");
        verifierRefusCalage(PASSAGE, ETAPE, INCONNU, "COMMANDE_PERIMEE");
        verifierRefusCalage(PASSAGE, ETAPE, lectureId, "ETAT_INCOMPATIBLE");
    }

    @Test
    void refuseUnCompteurDeCalagePerimeAvantLEtatDeLaLecturePreparee() {
        ouvrirPassage();
        designer(LECTEUR);
        declarerLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0);
        UUID lectureId = lecture().lectureId();
        refuserSansMutation(() -> corriger(PASSAGE, ETAPE, lectureId, 1, SensCorrection.RETARDER),
                409, "COMMANDE_PERIMEE", null);
    }

    @Test
    void lePassageIntrouvablePrimeAussiSurLaFinDeManche() {
        ouvrirPassage();
        deroulement.terminer(pilotage());
        verifierPassageIntrouvablePourLaRepriseEtLeCalage();
    }

    private void verifierPassageIntrouvablePourLaRepriseEtLeCalage() {
        refuserSansMutation(() -> reprendreEnParoles(INCONNU, ETAPE, INCONNU), 404, "RESSOURCE_INTROUVABLE", null);
        refuserSansMutation(() -> marquerDebut(INCONNU, ETAPE, INCONNU), 404, "RESSOURCE_INTROUVABLE", null);
        refuserSansMutation(() -> corriger(INCONNU, ETAPE, INCONNU, 1, SensCorrection.RETARDER),
                404, "RESSOURCE_INTROUVABLE", null);
    }

    private void verifierRefusCalage(UUID passageId, UUID etapeId, UUID lectureId, String code) {
        refuserSansMutation(() -> marquerDebut(passageId, etapeId, lectureId), 409, code, null);
        refuserSansMutation(() -> corriger(passageId, etapeId, lectureId, 0, SensCorrection.RETARDER), 409, code, null);
    }

    @Test
    void marqueLeDebutEnParolesSeulesALInstantDeReceptionEtSansDemande() {
        ouvrirPassage();
        confirmerCourant();
        horloge.avancer(Duration.ofSeconds(14));
        Instant recueLe = horloge.maintenant().minusMillis(500);
        UUID lectureId = lecture().lectureId();
        var avant = projection();
        var geste = new MarquerDebutParoles(pilotage().acteur(), recueLe, ENVOI, PASSAGE, ETAPE, lectureId);
        var recu = accepterLecture(deroulement.commander(geste), CALAGE_MODIFIE, SALLE_ET_CONSOLE);
        assertThat(recu.accuse()).isEqualTo(new AccuseCalage(ENVOI, SOIREE, MANCHE, PASSAGE, lectureId,
                "MARQUER_DEBUT", recueLe, null, 1));
        assertThat(lecture().decalageMs()).isEqualTo(1_500);
        assertThat(lecture().demandeId()).isNull();
        assertThat(projection()).isEqualTo(avant);
        var capture = karaoke().instantane();
        accepterLecture(deroulement.commander(geste), CALAGE_MODIFIE, Set.of());
        assertThat(karaoke().instantane()).isEqualTo(capture);
    }

    @Test
    void remplaceLaDemandeEtRecaleALaPositionDuLecteurMemeEnPause() {
        chanterSurFichier(OrigineParoles.VERSION_ADAPTEE);
        UUID lectureId = lecture().lectureId();
        var avant = projection();
        var premierGeste = accepterLecture(marquerDebut(PASSAGE, ETAPE, lectureId), CALAGE_MODIFIE, SALLE_ET_CONSOLE);
        UUID premiereDemande = lecture().demandeId();
        assertThat(premiereDemande).isEqualTo(new UUID(0, 103));
        assertThat(premierGeste.accuse()).isEqualTo(new AccuseCalage(ENVOI, SOIREE, MANCHE, PASSAGE, lectureId,
                "MARQUER_DEBUT", horloge.maintenant(), premiereDemande, 0));
        assertThat(lecture().decalageMs()).isEqualTo(25);
        assertThat(lecture().demandeeLe()).isEqualTo(horloge.maintenant());
        assertThat(projection()).isEqualTo(avant);
        horloge.avancer(Duration.ofSeconds(1));
        marquerDebut(PASSAGE, ETAPE, lectureId);
        UUID demande = lecture().demandeId();
        assertThat(demande).isEqualTo(new UUID(0, 104));
        assertThat(lecture().demandeeLe()).isEqualTo(horloge.maintenant());
        refuserSansMutation(() -> relever(lectureId, premiereDemande, 13_500), 409, "COMMANDE_PERIMEE", null);
        deroulement.mettreEnPause(pilotage());
        avant = projection();
        var releve = accepterLecture(relever(lectureId, demande, 13_500), CALAGE_MODIFIE, SALLE_ET_CONSOLE);
        assertThat(releve.accuse()).isEqualTo(new AccuseLectureChanson(ENVOI, SOIREE, MANCHE, LECTEUR,
                PASSAGE, lectureId, "RELEVER_POSITION", horloge.maintenant()));
        assertThat(lecture().decalageMs()).isEqualTo(1_500);
        assertThat(lecture().numeroModificationCalage()).isEqualTo(1);
        assertThat(lecture().demandeId()).isNull();
        assertThat(lecture().demandeeLe()).isNull();
        assertThat(projection()).isEqualTo(avant);
        refuserSansMutation(() -> relever(lectureId, demande, 13_500), 409, "COMMANDE_PERIMEE", null);
        deroulement.reprendre(pilotage());
        marquerDebut(PASSAGE, ETAPE, lectureId);
        accepterLecture(relever(lectureId, lecture().demandeId(), 13_500), CALAGE_MODIFIE, SALLE_ET_CONSOLE);
        assertThat(lecture().numeroModificationCalage()).isEqualTo(1);
        assertThat(lecture().demandeId()).isNull();
    }

    @Test
    void corrigeParPasDeCentEtGardeLaDemandeJusquAuReleve() {
        chanterSurFichier(OrigineParoles.VERSION_ADAPTEE);
        UUID lectureId = lecture().lectureId();
        marquerDebut(PASSAGE, ETAPE, lectureId);
        UUID demande = lecture().demandeId();
        var avant = projection();
        var correction = accepterLecture(corriger(PASSAGE, ETAPE, lectureId, 0, SensCorrection.RETARDER),
                CALAGE_MODIFIE, SALLE_ET_CONSOLE);
        assertThat(correction.accuse()).isEqualTo(new AccuseCalage(ENVOI, SOIREE, MANCHE, PASSAGE, lectureId,
                "CORRIGER", horloge.maintenant(), null, 1));
        assertThat(lecture().decalageMs()).isEqualTo(125);
        assertThat(lecture().demandeId()).isEqualTo(demande);
        refuserSansMutation(() -> corriger(PASSAGE, ETAPE, lectureId, 0, SensCorrection.AVANCER),
                409, "COMMANDE_PERIMEE", null);
        accepterLecture(corriger(PASSAGE, ETAPE, lectureId, 1, SensCorrection.AVANCER), CALAGE_MODIFIE, SALLE_ET_CONSOLE);
        assertThat(lecture().decalageMs()).isEqualTo(25);
        assertThat(lecture().numeroModificationCalage()).isEqualTo(2);
        assertThat(projection()).isEqualTo(avant);
        relever(lectureId, demande, 13_500);
        assertThat(lecture().decalageMs()).isEqualTo(1_500);
        assertThat(lecture().numeroModificationCalage()).isEqualTo(3);
        assertThat(lecture().versionSourceId()).isEqualTo(43);
    }

    private enum Signal { FIN, ERREUR, POSITION }

    @Test
    void uneErreurSurLePassageSuivantFigeLeVotePrecedentPuisLesParolesSeulesLeReprennent() {
        chanterSurFichier(OrigineParoles.VERSION_ADAPTEE);
        signaler(Signal.FIN, PASSAGE, ETAPE, lecture().lectureId(), LECTEUR);
        horloge.avancer(Duration.ofSeconds(10));
        inscrire(SUIVANT);
        appeler(cible(SUIVANT));
        UUID etape = karaoke().instantane().cycle().etapeId();
        declarer(cible(SUIVANT), etape, LECTEUR, preparationLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0));
        confirmer(INTERPRETE, cible(SUIVANT), etape);
        UUID lecture = karaoke().instantane().lectures().get(SUIVANT).lectureId();
        signaler(Signal.ERREUR, SUIVANT, etape, lecture, LECTEUR);
        Instant pause = horloge.maintenant();
        horloge.avancer(Duration.ofSeconds(40));
        var vote = karaoke().instantane().voteDuPublic().votes().getFirst();
        assertThat(vote.pauseDepuis()).isEqualTo(pause);
        assertThat(VoteDuPublic.projeter(vote, horloge.maintenant()).delaiRestantMs()).isEqualTo(20_000);
        reprendreEnParoles(SUIVANT, etape, lecture);
        horloge.avancer(Duration.ofSeconds(20));
        assertThat(karaoke().instantane().voteDuPublic().votes().getFirst().clotureLe()).isEqualTo(horloge.maintenant());
    }

    @Test
    void terminerLaMancheEnPauseClotDeuxVotesEtConserveLaNoteDejaRecue() {
        chanterSurFichier(OrigineParoles.VERSION_ADAPTEE);
        signaler(Signal.FIN, PASSAGE, ETAPE, lecture().lectureId(), LECTEUR);
        var votant = new Acteur(Acteur.Type.JOUEUR, INCONNU, SOIREE);
        var recueLe = horloge.maintenant();
        deroulement.recevoir(new VoterPassage(votant, recueLe, ENVOI, cible(PASSAGE), 5));
        horloge.avancer(Duration.ofSeconds(5));
        inscrire(SUIVANT);
        appeler(cible(SUIVANT));
        var etape = karaoke().instantane().cycle().etapeId();
        declarer(cible(SUIVANT), etape, LECTEUR, preparationLocale(300_000L, OrigineParoles.VERSION_ADAPTEE, 0));
        confirmer(INTERPRETE, cible(SUIVANT), etape);
        signaler(Signal.FIN, SUIVANT, etape, karaoke().instantane().lectures().get(SUIVANT).lectureId(), LECTEUR);
        assertThat(karaoke().instantane().voteDuPublic().votes()).hasSize(2).allMatch(EtatVotePassage::ouvert);
        deroulement.mettreEnPause(pilotage());
        assertThat(karaoke().instantane().voteDuPublic().votes()).allMatch(vote -> vote.pauseDepuis() != null);
        accepter(deroulement.terminer(pilotage()), "manche-terminee", true);
        var votes = karaoke().instantane().voteDuPublic().votes();
        assertThat(votes).hasSize(2).allMatch(vote -> vote.pauseDepuis() == null && vote.clotureLe().equals(horloge.maintenant()));
        assertThat(votes.getFirst().votes()).containsExactly(new VoteRecu(INCONNU, 5, recueLe));
        assertThat(horloge.minuteursEnAttente()).isZero();
    }

    private ResultatDeCommande signaler(Signal signal, UUID passage, UUID etape, UUID lecture, UUID lecteur) {
        Instant recueLe = horloge.maintenant();
        return deroulement.commander(switch (signal) {
            case FIN -> new SignalerFinLectureChanson(ECRAN, recueLe, ENVOI, passage, etape, lecture, lecteur, 10_000);
            case ERREUR -> new SignalerErreurLectureChanson(ECRAN, recueLe, ENVOI, passage, etape, lecture, lecteur);
            case POSITION -> new ReleverPositionLecture(ECRAN, recueLe, ENVOI, passage, etape, lecture, lecteur,
                    INCONNU, 13_500);
        });
    }

    private ResultatDeCommande designer(UUID lecteur) {
        return deroulement.commander(new DesignerLecteurChanson(ECRAN, horloge.maintenant(), ENVOI, lecteur));
    }

    private ResultatDeCommande declarer(CiblePassage cible, UUID etape, UUID lecteur, LecturePreparee preparation) {
        return deroulement.commander(new DeclarerLectureChanson(ECRAN, horloge.maintenant(), ENVOI,
                cible, etape, lecteur, preparation));
    }

    private void declarerLocale(Long dureeFichier, OrigineParoles origine, long decalage) {
        accepterLecture(declarer(cible(PASSAGE), ETAPE, LECTEUR, preparationLocale(dureeFichier, origine, decalage)),
                LECTURE_MODIFIEE, SALLE_ET_CONSOLE);
    }

    private LecturePreparee preparationLocale(Long dureeFichier, OrigineParoles origine, long decalage) {
        return new LecturePreparee(SourceLecture.FICHIER_LOCAL, new FichierAudioLocal("titre.mp3", 5, dureeFichier),
                null, origine, 290_000, LIGNES_ADAPTEES, 43, decalage);
    }

    private void chanterSurFichier(OrigineParoles origine) {
        ouvrirPassage();
        designer(LECTEUR);
        declarerLocale(300_000L, origine, 25);
        confirmerCourant();
    }

    private void confirmerCourant() {
        accepter(confirmer(INTERPRETE, cible(PASSAGE), ETAPE), "passage-demarre", true);
    }

    private LecturePassage lecture() { return karaoke().instantane().lectures().get(PASSAGE); }

    private ProjectionCycle projection() {
        var capture = karaoke().instantane();
        return CycleDePassage.projeter(capture.file(), capture.cycle(),
                capture.etatManche() == EtatManche.EN_PAUSE, horloge.maintenant());
    }

    private ResultatDeCommande reprendreEnParoles(UUID passageId, UUID etapeId, UUID lectureId) {
        return deroulement.commander(new ReprendreEnParolesSeules(pilotage().acteur(), horloge.maintenant(),
                ENVOI, passageId, etapeId, lectureId));
    }

    private ResultatDeCommande marquerDebut(UUID passageId, UUID etapeId, UUID lectureId) {
        return deroulement.commander(new MarquerDebutParoles(pilotage().acteur(), horloge.maintenant(), ENVOI,
                passageId, etapeId, lectureId));
    }

    private ResultatDeCommande corriger(UUID passageId, UUID etapeId, UUID lectureId, long numero, SensCorrection sens) {
        return deroulement.commander(new CorrigerCalage(pilotage().acteur(), horloge.maintenant(), ENVOI,
                passageId, etapeId, lectureId, numero, sens));
    }

    private ResultatDeCommande relever(UUID lectureId, UUID demandeId, long position) {
        return deroulement.commander(new ReleverPositionLecture(ECRAN, horloge.maintenant(), ENVOI,
                PASSAGE, ETAPE, lectureId, LECTEUR, demandeId, position));
    }

    private Accepte accepterLecture(ResultatDeCommande resultat, String cause, Set<Destination> destinations) {
        assertThat(resultat).isInstanceOf(Accepte.class);
        var accepte = (Accepte) resultat;
        assertThat(accepte.statutHttp()).isEqualTo(200);
        assertThat(accepte.typeEvenement()).isEqualTo(cause);
        assertThat(accepte.aRepublier()).isEqualTo(destinations);
        return accepte;
    }
}
