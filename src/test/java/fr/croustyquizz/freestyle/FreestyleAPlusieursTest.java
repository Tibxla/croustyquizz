package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Passage de Freestyle à plusieurs (#74) : Invitations, Couplets, annonce du suivant et vote du meilleur Interprète. */
class FreestyleAPlusieursTest {
    private static final UUID SOIREE = UUID.fromString("74000000-0000-4000-8000-000000000001");
    private static final UUID MANCHE = UUID.fromString("74000000-0000-4000-8000-000000000002");
    private static final UUID PASSAGE = UUID.fromString("74000000-0000-4000-8000-000000000003");
    private static final UUID ETAPE = UUID.fromString("74000000-0000-4000-8000-000000000010");
    private static final UUID INES = UUID.fromString("74000000-0000-4000-8000-000000000005");
    private static final UUID MAEL = UUID.fromString("74000000-0000-4000-8000-000000000006");
    private static final UUID ZOE = UUID.fromString("74000000-0000-4000-8000-000000000007");
    private static final UUID LOU = UUID.fromString("74000000-0000-4000-8000-000000000008");
    private static final UUID NOE = UUID.fromString("74000000-0000-4000-8000-000000000009");
    private static final Map<UUID, String> PSEUDOS = Map.of(INES, "Inès", MAEL, "Maël", ZOE, "Zoé", LOU, "Lou", NOE, "Noé");
    /** 120 BPM : une mesure dure 2 000 ms, le beat part à 250 ms. */
    private static final Instrumentale INSTRUMENTALE = new Instrumentale(
            UUID.fromString("74000000-0000-4000-8000-000000000004"), "Pavés mouillés", 120, 250);
    private static final ReglagesFreestyle REGLAGES = new ReglagesFreestyle(ThemeDeRimes.VILLE, SchemaDeRimes.ABAB,
            DifficulteFreestyle.TOUS, 8, 2, List.of(INSTRUMENTALE.instrumentaleId()));

    private final HorlogeFactice horloge = new HorlogeFactice();
    private final FabriqueFreestyle fabrique = new FabriqueFreestyle(new DictionnaireDeRimesFactice(),
            1, Duration.ofSeconds(30), Duration.ofSeconds(30), () -> ETAPE, new Random(74));
    private DeroulementDeManche deroulement = fabrique.creer(contexte(), REGLAGES);

    @Test
    void lesInvitesAcceptesDeviennentInterpretesEtUneInvitationSansReponseExpireALAppel() {
        deroulement.demarrer();
        var refusSoi = deroulement.recevoir(inscrire(INES, List.of(MAEL, INES), true));
        assertThat(refusSoi).isInstanceOfSatisfying(Refuse.class, refus -> assertThat(refus.code()).isEqualTo("DONNEES_INVALIDES"));
        var refusAbsent = deroulement.recevoir(inscrire(INES, List.of(MAEL), false));
        assertThat(refusAbsent).isInstanceOfSatisfying(Refuse.class, refus -> assertThat(refus.statutHttp()).isEqualTo(404));
        assertThat(file().etat().passages()).isEmpty();

        assertThat(deroulement.recevoir(inscrire(INES, List.of(MAEL, ZOE, LOU), true))).isInstanceOf(Accepte.class);
        assertThat(manette(LOU).get("invitationsRecues")).asList().singleElement().satisfies(invitation ->
                assertThat(invitation).isInstanceOfSatisfying(VuesFreestyle.InvitationRecue.class, recue -> {
                    assertThat(recue.instrumentale()).isEqualTo(INSTRUMENTALE);
                    assertThat(recue.inscrivant().pseudo()).isEqualTo("Inès");
                }));
        assertThat(console().get("invitationsEnAttente")).asList().hasSize(3);
        assertThat(salle()).doesNotContainKeys("invitationsEnAttente", "invitationsRecues", "invitationsEmises");

        assertThat(repondre(MAEL, 0, true)).isInstanceOf(Accepte.class);
        assertThat(repondre(LOU, 1, false)).isInstanceOf(Accepte.class);
        assertThat(manette(INES).get("invitationsEmises")).asList().hasSize(3);
        appeler(1);

        assertThat(passage().interpretes()).containsExactly(INES, MAEL);
        assertThat(file().etat().invitations()).filteredOn(i -> i.inviteId().equals(ZOE)).singleElement()
                .extracting(InvitationPassage::etat).isEqualTo(InvitationPassage.Etat.EXPIREE);
        assertThat(repondre(ZOE, 2, true)).isInstanceOf(Refuse.class);
    }

    @Test
    void lOrdreDesCoupletsEstTireAuDemarrageIdentiquePartoutEtTourneSurLaGrille() {
        demarrerUnTrio();
        var couplets = couplets();
        assertThat(couplets.ordre()).containsExactlyInAnyOrder(INES, MAEL, ZOE);
        var vus = coupletsVus(salle());
        assertThat(vus.ordre()).extracting(VuesFreestyle.Interprete::joueurId).containsExactlyElementsOf(couplets.ordre());
        assertThat(coupletsVus(console())).isEqualTo(vus);
        assertThat(coupletsVus(manette(NOE))).isEqualTo(vus);
        assertThat(vus.courant().interprete().joueurId()).isEqualTo(couplets.ordre().getFirst());
        assertThat(vus.suivant().premiereMesure()).isEqualTo(3);
        assertThat(couplets.nombreCouplets()).isEqualTo(4);
        assertThat(couplets.courant()).isEqualTo(1);
        assertThat(couplets.interpreteDu(4)).as("l’ordre tourne").isEqualTo(couplets.ordre().getFirst());

        var persiste = instantane();
        deroulement = fabrique.restaurer(contexte(), REGLAGES, persiste.etat(), persiste.etatManche());
        assertThat(couplets().ordre()).as("restauré sans nouveau tirage").isEqualTo(couplets.ordre());
    }

    @Test
    void lAnnonceTombeExactementUneMesureAvantLeChangementEtNeVibreQueChezLeSuivant() {
        demarrerUnTrio();
        var ordre = couplets().ordre();
        UUID deuxieme = ordre.get(1);
        UUID autre = ordre.get(2);

        horloge.avancer(Duration.ofMillis(2_249));
        assertThat(couplets().suivantAnnonce()).isFalse();
        horloge.avancer(Duration.ofMillis(1));
        assertThat(couplets().suivantAnnonce()).isTrue();
        assertThat(fabrique.typeEvenementMinuteur(deroulement)).isEqualTo("couplet-annonce");
        assertThat(manette(deuxieme)).containsEntry("monCoupletAnnonce", true);
        assertThat(manette(autre)).containsEntry("monCoupletAnnonce", false);
        assertThat(manette(ordre.getFirst())).containsEntry("monCoupletAnnonce", false);
        assertThat(manette(NOE)).containsEntry("monCoupletAnnonce", false);

        horloge.avancer(Duration.ofMillis(1_999));
        assertThat(couplets().courant()).isEqualTo(1);
        horloge.avancer(Duration.ofMillis(1));
        assertThat(couplets().courant()).as("une mesure après l’annonce").isEqualTo(2);
        assertThat(couplets().suivantAnnonce()).isFalse();
        assertThat(fabrique.typeEvenementMinuteur(deroulement)).isEqualTo("couplet-commence");
        assertThat(manette(deuxieme)).containsEntry("monCoupletAnnonce", false);
    }

    @Test
    void laPauseSuspendLAnnonceEtLaRepriseGardeLeTempsRestant() {
        demarrerUnTrio();
        horloge.avancer(Duration.ofMillis(5_000));
        assertThat(couplets().courant()).isEqualTo(2);
        deroulement.mettreEnPause(pilotage());
        horloge.avancer(Duration.ofMinutes(1));
        assertThat(couplets().suivantAnnonce()).isFalse();
        deroulement.reprendre(pilotage());

        horloge.avancer(Duration.ofMillis(1_249));
        assertThat(couplets().suivantAnnonce()).isFalse();
        horloge.avancer(Duration.ofMillis(1));
        assertThat(couplets().suivantAnnonce()).as("mesure 5 annoncée à 6 250 ms de lecture").isTrue();
        assertThat(couplets().courant()).isEqualTo(2);
    }

    @Test
    void lePassageClosAnnuleLesCoupletsRestants() {
        demarrerUnTrio();
        horloge.avancer(Duration.ofMillis(1_000));
        deroulement.passerEtape(pilotage());
        horloge.avancer(Duration.ofSeconds(20));
        assertThat(couplets().courant()).isEqualTo(1);
        assertThat(couplets().suivantAnnonce()).isFalse();
    }

    @Test
    void unPassageResteSeulGardeLaNoteEtNAPasDeCouplets() {
        deroulement.demarrer();
        deroulement.recevoir(inscrire(INES, List.of(MAEL), true));
        appeler(0);
        assertThat(deroulement.recevoir(presence(INES, 1))).isInstanceOf(Accepte.class);
        assertThat(((Map<?, ?>) salle().get("courant")).get("couplets")).isNull();
        horloge.avancer(Duration.ofMillis(16_250));
        assertThat(deroulement.recevoir(new VoterPassage(joueur(NOE), horloge.maintenant(), UUID.randomUUID(),
                new CiblePassage(PASSAGE, 3), 4))).isInstanceOf(Accepte.class);
    }

    @Test
    void lePublicDesigneUnSeulInterpreteDuTrioALaPlaceDeLaNote() {
        demarrerUnTrio();
        horloge.avancer(Duration.ofMillis(16_250));
        var cible = new CiblePassage(PASSAGE, 5);
        var avant = instantane();
        assertThat(deroulement.recevoir(new VoterPassage(joueur(NOE), horloge.maintenant(), UUID.randomUUID(), cible, 4)))
                .isInstanceOfSatisfying(Refuse.class, refus -> assertThat(refus.code()).isEqualTo("DONNEES_INVALIDES"));
        assertThat(deroulement.recevoir(designer(NOE, cible, NOE)))
                .isInstanceOfSatisfying(Refuse.class, refus -> assertThat(refus.code()).isEqualTo("DONNEES_INVALIDES"));
        assertThat(deroulement.recevoir(designer(MAEL, cible, ZOE)))
                .isInstanceOfSatisfying(Refuse.class, refus -> assertThat(refus.code()).isEqualTo("VOTE_INTERDIT"));
        assertThat(instantane()).isEqualTo(avant);

        Accepte vote = (Accepte) deroulement.recevoir(designer(NOE, cible, ZOE));
        assertThat(vote.typeEvenement()).isEqualTo("vote-enregistre");
        assertThat(vote.aRepublier()).containsExactlyInAnyOrder(Destination.SALLE, Destination.CONSOLE, Destination.joueur(NOE));
        assertThat(voteDe(manette(NOE))).containsEntry("scrutin", "MEILLEUR_INTERPRETE")
                .containsEntry("monChoix", ZOE).containsEntry("maNote", null).containsEntry("peutVoter", false);
        assertThat(voteDe(salle())).containsEntry("nombreVotes", 1L).containsEntry("voix", null);

        horloge.avancer(Duration.ofSeconds(30));
        assertThat(voteDe(salle())).containsEntry("etat", "CLOS").extractingByKey("voix").asList()
                .extracting("nombreVoix").containsExactly(0L, 0L, 1L);
        assertThat(deroulement.points()).isEmpty();
    }

    private void demarrerUnTrio() {
        deroulement.demarrer();
        assertThat(deroulement.recevoir(inscrire(INES, List.of(MAEL, ZOE), true))).isInstanceOf(Accepte.class);
        repondre(MAEL, 0, true);
        repondre(ZOE, 1, true);
        appeler(2);
        Accepte presence = (Accepte) deroulement.recevoir(presence(ZOE, 3));
        assertThat(presence.typeEvenement()).isEqualTo("passage-demarre");
    }

    private ResultatDeCommande repondre(UUID invite, long version, boolean accepte) {
        return deroulement.recevoir(new RepondreInvitation(joueur(invite), horloge.maintenant(), UUID.randomUUID(),
                new CiblePassage(PASSAGE, version), accepte));
    }

    private void appeler(long version) {
        assertThat(deroulement.commander(new AppelerPassage(pilotage().acteur(), horloge.maintenant(), UUID.randomUUID(),
                new CiblePassage(PASSAGE, version)))).isInstanceOf(Accepte.class);
    }

    private InscrireFreestyle inscrire(UUID joueur, List<UUID> invites, boolean presents) {
        return new InscrireFreestyle(joueur(joueur), horloge.maintenant(), UUID.randomUUID(), PASSAGE, 1,
                INSTRUMENTALE.instrumentaleId(), List.of(INSTRUMENTALE), DictionnaireDeRimesFactice.MOTS_DE_VILLE,
                invites, presents);
    }

    private ConfirmerPresencePassage presence(UUID joueur, long version) {
        return new ConfirmerPresencePassage(joueur(joueur), horloge.maintenant(), UUID.randomUUID(),
                new CiblePassage(PASSAGE, version), ETAPE);
    }

    private DesignerMeilleurInterprete designer(UUID joueur, CiblePassage cible, UUID interprete) {
        return new DesignerMeilleurInterprete(joueur(joueur), horloge.maintenant(), UUID.randomUUID(), cible, interprete);
    }

    private CommandePilotage pilotage() {
        var animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), SOIREE);
        return new CommandePilotage(animateur, UUID.randomUUID(), horloge.maintenant());
    }

    private ContexteDeManche contexte() {
        return new ContexteDeManche(SOIREE, MANCHE, fabrique.reglagesCommuns(REGLAGES), horloge, () -> { }, false);
    }

    private static Acteur joueur(UUID id) { return new Acteur(Acteur.Type.JOUEUR, id, SOIREE); }

    private InstantaneFreestyle instantane() { return (InstantaneFreestyle) deroulement.instantane(); }

    private FileAttente file() { return FileAttente.depuis(instantane().etat().file()); }

    private Passage passage() { return file().trouver(PASSAGE); }

    private Map<String, Object> vue(Destination destination) {
        return VuesFreestyle.projeter(instantane(), destination, PSEUDOS, List.of(INSTRUMENTALE), horloge.maintenant())
                .orElseThrow();
    }

    private Map<String, Object> salle() { return vue(Destination.SALLE); }

    private Map<String, Object> console() { return vue(Destination.CONSOLE); }

    private Map<String, Object> manette(UUID joueur) { return vue(Destination.joueur(joueur)); }

    private CoupletsDuPassage couplets() { return instantane().etat().contenus().get(PASSAGE).couplets(); }

    private static VuesFreestyle.Couplets coupletsVus(Map<String, Object> vue) {
        return (VuesFreestyle.Couplets) ((Map<?, ?>) vue.get("courant")).get("couplets");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> voteDe(Map<String, Object> vue) {
        return (Map<String, Object>) ((List<?>) vue.get("votes")).getFirst();
    }
}
