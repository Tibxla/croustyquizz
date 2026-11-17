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
		return new DeroulementKaraoke(contexte, new ReglagesKaraoke(2), Duration.ofSeconds(30));
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
        assertThat(action.get()).isInstanceOfSatisfying(Refuse.class, r -> {
            assertThat(r.statutHttp()).isEqualTo(statut); assertThat(r.code()).isEqualTo(code);
            if (motif != null) { assertThat(r.detail()).contains(motif); }
        });
        assertThat(karaoke().instantane()).isEqualTo(avant);
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
        for (long limite : new long[] {0, -1, 9007199254740992L}) {
            assertThatThrownBy(() -> new ReglagesKaraoke(limite)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ReglerLimitePassages(pilotage().acteur(), horloge.maintenant(), UUID.randomUUID(), limite))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EtatFileAttente(SOIREE, MANCHE, limite, 1, 1, 1, null, List.of(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new ReglagesKaraoke(9007199254740991L).limitePassagesEnAttente()).isEqualTo(9007199254740991L);
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
        assertThat(karaoke().estimerAttentes(null).values()).extracting(AttenteEstimee::etat).containsOnly(EtatAttente.SUSPENDUE);
        horloge.avancer(Duration.ofSeconds(42));
        accepter(deroulement.reprendre(pilotage()), "manche-reprise", true);
        assertThat(karaoke().estimerAttentes(null).values()).containsExactly(new AttenteEstimee(horloge.maintenant(), EtatAttente.ESTIMEE, 0L));
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
}
