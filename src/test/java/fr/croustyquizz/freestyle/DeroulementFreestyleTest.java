package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeroulementFreestyleTest extends ContratDeroulementDeManche {
    private static final UUID PUBLIC = UUID.fromString("70000000-0000-4000-8000-000000000006");
    private static final UUID PASSAGE = UUID.fromString("70000000-0000-4000-8000-000000000003");
    private static final UUID ETAPE = UUID.fromString("70000000-0000-4000-8000-000000000007");
    private static final Instrumentale INSTRUMENTALE = new Instrumentale(
            UUID.fromString("70000000-0000-4000-8000-000000000004"), "À la lueur", 120, 250);
    private static final ReglagesFreestyle REGLAGES = new ReglagesFreestyle(ThemeDeRimes.VILLE, SchemaDeRimes.ABAB,
            DifficulteFreestyle.PREMIER, 8, List.of(INSTRUMENTALE.instrumentaleId()));
    private static final Set<Destination> ECRANS = Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);

    private final FabriqueFreestyle fabrique = new FabriqueFreestyle(new DictionnaireDeRimesFactice(),
            1, Duration.ofSeconds(30), Duration.ofSeconds(30), () -> ETAPE, new java.util.Random(70));

    @Override
    protected DeroulementDeManche creer(ContexteDeManche contexte) {
        return fabrique.creer(contexte, REGLAGES);
    }

    @Override
    protected ActionJoueur uneAction(UUID joueurId, Instant recueLe) {
        return inscrire(joueurId, recueLe, PASSAGE, 1, INSTRUMENTALE.instrumentaleId(), List.of(INSTRUMENTALE));
    }

    @Test
    void appelleDemarreEtTermineLePassagePuisOuvreLeVoteSansPoints() {
        demarrerUnPassage();
        var enCours = courant();
        assertThat(enCours.etat()).isEqualTo(EtatPassage.EN_COURS);
        assertThat(enCours.duree()).isEqualTo(Duration.ofMillis(16_250));
        assertThat(instantane().etat().contenus().get(PASSAGE).grille().mots()).hasSize(8);

        horloge.avancer(Duration.ofMillis(16_249));
        assertThat(courant().etat()).isEqualTo(EtatPassage.EN_COURS);
        horloge.avancer(Duration.ofMillis(1));
        assertThat(courant().etat()).isEqualTo(EtatPassage.TERMINE);
        assertThat(fabrique.typeEvenementMinuteur(deroulement)).isEqualTo("passage-termine");
        assertThat(finsSignalees).isZero();
        assertThat(deroulement.estTerminee()).isFalse();

        var avantVoteInterdit = deroulement.instantane();
        assertThat(deroulement.recevoir(voter(JOUEUR))).isInstanceOfSatisfying(Refuse.class,
                refus -> assertThat(refus.code()).isEqualTo("VOTE_INTERDIT"));
        assertThat(deroulement.instantane()).isEqualTo(avantVoteInterdit);

        Accepte vote = (Accepte) deroulement.recevoir(voter(PUBLIC));
        assertThat(vote.typeEvenement()).isEqualTo("vote-enregistre");
        assertThat(vote.aRepublier()).containsExactlyInAnyOrder(Destination.SALLE, Destination.CONSOLE, Destination.joueur(PUBLIC));

        horloge.avancer(Duration.ofSeconds(30));
        assertThat(fabrique.typeEvenementMinuteur(deroulement)).isEqualTo("vote-clos");
        assertThat(instantane().etat().voteDuPublic().votes()).singleElement().satisfies(voteClos -> {
            assertThat(voteClos.ouvert()).isFalse();
            assertThat(voteClos.votes()).extracting(VoteRecu::note).containsExactly(4);
        });
        assertThat(deroulement.points()).isEmpty();
    }

    @Test
    void projetteLaGrillePourLesTroisDestinationsAvecLesMotsMasquesCotePublic() throws IOException {
        demarrerUnPassage();

        var salle = vue(Destination.SALLE);
        var manette = vue(Destination.joueur(PUBLIC));
        var console = vue(Destination.CONSOLE);

        assertThat(salle).containsEntry("difficulte", DifficulteFreestyle.PREMIER);
        assertThat(manette).containsEntry("grille", salle.get("grille"));
        assertThat(motsDe(salle)).containsExactly("lumière", "nuit", null, null, null, null, null, null);
        assertThat(motsDe(console)).containsExactly("lumière", "nuit", "pierre", "bruit",
                "rivière", "luit", "poussière", "minuit");
        verifierContratDesCaptures(salle, console, manette);

        deroulement.mettreEnPause(pilotage());
        assertThat(vue(Destination.SALLE)).containsEntry("grille", salle.get("grille"));

        deroulement.passerEtape(pilotage());
        assertThat(vue(Destination.SALLE)).containsEntry("grille", null).containsEntry("votes", List.of());
        deroulement.terminer(pilotage());
        assertThat(vue(Destination.SALLE)).isNull();
    }

    @Test
    void restaureUneMancheEnPauseEtRearmeLaFinAvecLeTempsRestant() {
        demarrerUnPassage();
        horloge.avancer(Duration.ofSeconds(5));
        deroulement.mettreEnPause(pilotage());
        var persiste = instantane();
        horloge.avancer(Duration.ofMinutes(1));
        deroulement = fabrique.restaurer(contexte(false), REGLAGES, persiste.etat(), persiste.etatManche());

        assertThat(deroulement.instantane()).isEqualTo(persiste);
        assertThat(horloge.minuteursEnAttente()).isZero();
        assertThat(deroulement.reprendre(pilotage())).isInstanceOf(Accepte.class);
        horloge.avancer(Duration.ofMillis(11_249));
        assertThat(courant().etat()).isEqualTo(EtatPassage.EN_COURS);
        horloge.avancer(Duration.ofMillis(1));
        assertThat(courant().etat()).isEqualTo(EtatPassage.TERMINE);
    }

    @Test
    void tireUneInstrumentaleDisponibleEtAppelleLePassageSuivantAlaFinPrevue() {
        deroulement = creer(contexte(true));
        deroulement.demarrer();
        UUID suivant = UUID.randomUUID();
        var horsSelection = new Instrumentale(UUID.randomUUID(), "Hors sélection", 100, 0);

        var inscription = (Accepte) deroulement.recevoir(inscrire(JOUEUR, horloge.maintenant(), PASSAGE, 1,
                null, List.of(horsSelection, INSTRUMENTALE)));
        assertThat(inscription.typeEvenement()).isEqualTo("passage-appele");
        assertThat(instantane().etat().contenus().get(PASSAGE).instrumentale()).isEqualTo(INSTRUMENTALE);
        assertThat(deroulement.recevoir(inscrire(PUBLIC, horloge.maintenant(), suivant, 2,
                INSTRUMENTALE.instrumentaleId(), List.of(INSTRUMENTALE)))).isInstanceOf(Accepte.class);
        assertThat(deroulement.recevoir(presence(JOUEUR, 1))).isInstanceOf(Accepte.class);

        horloge.avancer(Duration.ofMillis(16_250));

        assertThat(courant().etat()).isEqualTo(EtatPassage.TERMINE);
        assertThat(instantane().etat().cycle().passageId()).isEqualTo(suivant);
        assertThat(fabrique.typeEvenementMinuteur(deroulement)).isEqualTo("passage-appele");
        assertThat(instantane().etat().voteDuPublic().votes()).hasSize(1);
    }

    @Test
    void refuseSansMutationUneInstrumentaleNonDeclareeOuUneGrilleImpossible() {
        deroulement.demarrer();
        var avant = instantane();
        var instrumentaleAbsente = inscrire(JOUEUR, horloge.maintenant(), PASSAGE, 1,
                INSTRUMENTALE.instrumentaleId(), List.of());
        assertThat(deroulement.recevoir(instrumentaleAbsente)).isInstanceOfSatisfying(Refuse.class,
                refus -> assertThat(refus.code()).isEqualTo("INSTRUMENTALE_INDISPONIBLE"));
        assertThat(instantane()).isEqualTo(avant);

        var verre = new ReglagesFreestyle(ThemeDeRimes.SPORT, SchemaDeRimes.AAAA, DifficulteFreestyle.TOUS, 32,
                REGLAGES.instrumentaleIds());
        deroulement = fabrique.creer(contexte(false), verre);
        deroulement.demarrer();
        avant = instantane();
        assertThat(deroulement.recevoir(uneAction(JOUEUR, horloge.maintenant()))).isInstanceOfSatisfying(Refuse.class,
                refus -> assertThat(refus.code()).isEqualTo("GRILLE_INDISPONIBLE"));
        assertThat(instantane()).isEqualTo(avant);
    }

    private ContexteDeManche contexte(boolean automatique) {
        return new ContexteDeManche(SOIREE, MANCHE, fabrique.reglagesCommuns(REGLAGES), horloge,
                () -> finsSignalees++, automatique);
    }

    private void verifierContratDesCaptures(Map<String, Object> salle, Map<String, Object> console,
            Map<String, Object> manette) throws IOException {
        var manche = Map.of("id", MANCHE, "ordre", 0, "mode", "FREESTYLE", "etat", "EN_COURS", "reglages", REGLAGES);
        var commune = new LinkedHashMap<String, Object>();
        commune.put("soiree", Map.of("id", SOIREE, "titre", "À la tombée de la nuit", "etat", "EN_COURS",
                "format", "SOLO", "pilotage", "MANUEL", "codeAcces", "RIMES7"));
        commune.put("mancheActive", manche);
        commune.put("modeEnCours", "FREESTYLE");
        commune.put("joueurs", List.of(Map.of("id", JOUEUR, "pseudo", "Inès", "connecte", true),
                Map.of("id", PUBLIC, "pseudo", "Maël", "connecte", true)));
        commune.put("classement", List.of());
        commune.put("dernierBuzz", null);
        var vueConsole = new LinkedHashMap<>(commune);
        vueConsole.put("programme", List.of(manche));
        var vueManette = new LinkedHashMap<>(commune);
        vueManette.put("joueurId", PUBLIC);
        vueManette.put("eligiblePourMancheActive", true);
        vueManette.put("derniereReponse", null);
        vueManette.put("resultatPersonnel", null);

        verifierCapture("retrouverEtatSalle", commune, salle);
        verifierCapture("retrouverEtatConsole", vueConsole, console);
        verifierCapture("retrouverEtatManette", vueManette, manette);
    }

    private void verifierCapture(String operation, Map<String, Object> vue, Map<String, Object> bloc) throws IOException {
        vue.put("freestyle", bloc);
        var capture = Map.of("vue", vue, "revision", 1, "heureServeur", horloge.maintenant(),
                "abonnementId", UUID.randomUUID());
        ContratApi.verifier("docs/api/openapi.yaml", operation, 200, "application/json",
                JsonMapper.builder().build().writeValueAsString(capture));
    }

    private Map<String, Object> vue(Destination destination) {
        return VuesFreestyle.projeter(instantane(), destination, Map.of(JOUEUR, "Inès", PUBLIC, "Maël"),
                List.of(INSTRUMENTALE), horloge.maintenant()).orElse(null);
    }

    private static List<String> motsDe(Map<String, Object> vue) {
        return ((GrilleDeRimes) vue.get("grille")).mots().stream().map(GrilleDeRimes.Mot::mot).toList();
    }

    private void demarrerUnPassage() {
        deroulement.demarrer();
        Accepte inscription = (Accepte) deroulement.recevoir(uneAction(JOUEUR, horloge.maintenant()));
        assertThat(inscription.statutHttp()).isEqualTo(201);
        assertThat(inscription.aRepublier()).isEqualTo(ECRANS);
        Accepte appel = (Accepte) deroulement.commander(new AppelerPassage(pilotage().acteur(), horloge.maintenant(),
                UUID.randomUUID(), cible(0)));
        assertThat(appel.typeEvenement()).isEqualTo("passage-appele");
        Accepte presence = (Accepte) deroulement.recevoir(presence(JOUEUR, 1));
        assertThat(presence.typeEvenement()).isEqualTo("passage-demarre");
        assertThat(presence.aRepublier()).isEqualTo(ECRANS);
    }

    private static InscrireFreestyle inscrire(UUID joueur, Instant recueLe, UUID passage, long numero, UUID instrumentale,
            List<Instrumentale> disponibles) {
        return new InscrireFreestyle(joueur(joueur), recueLe, UUID.randomUUID(), passage, numero, instrumentale, disponibles,
                DictionnaireDeRimesFactice.MOTS_DE_VILLE);
    }

    private ConfirmerPresencePassage presence(UUID joueur, long version) {
        return new ConfirmerPresencePassage(joueur(joueur), horloge.maintenant(), UUID.randomUUID(), cible(version), ETAPE);
    }

    private VoterPassage voter(UUID joueur) {
        return new VoterPassage(joueur(joueur), horloge.maintenant(), UUID.randomUUID(), cible(3), 4);
    }

    private static Acteur joueur(UUID id) {
        return new Acteur(Acteur.Type.JOUEUR, id, SOIREE);
    }

    private static CiblePassage cible(long version) {
        return new CiblePassage(PASSAGE, version);
    }

    private InstantaneFreestyle instantane() {
        return (InstantaneFreestyle) deroulement.instantane();
    }

    private Passage courant() {
        return FileAttente.depuis(instantane().etat().file()).trouver(PASSAGE);
    }
}
