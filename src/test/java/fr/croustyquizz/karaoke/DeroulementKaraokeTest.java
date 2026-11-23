package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.*;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

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
    }

    @Test
    void laFinPrevueDuDernierPassageNeTerminePasLaManche() {
        ouvrirPassage();
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
        deroulement.terminer(pilotage());
        assertThat(fabrique.typeEvenementMinuteur(deroulement)).isEqualTo("manche-terminee");
    }

    private void ouvrirPassage() {
        deroulement.demarrer();
        inscrire(PASSAGE);
        accepter(appeler(cible(PASSAGE)), "passage-appele", true);
    }

    private void inscrire(UUID id) {
        var contenu = new ContenuKaraoke(new Chanson(id.toString(), 42, "Titre", "Artiste", null, 180_000L,
                EtatParoles.SYNCHRONISEES), List.of(new LigneParoles(12_000, "Des paroles")));
        assertThat(deroulement.recevoir(new InscrirePassage(INTERPRETE, horloge.maintenant(), ENVOI,
                new FaitsInscription(id, 1), id.toString(), contenu))).isInstanceOf(Accepte.class);
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
}
