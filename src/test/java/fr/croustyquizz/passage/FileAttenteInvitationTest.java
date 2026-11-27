package fr.croustyquizz.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import fr.croustyquizz.passage.InvitationPassage.Etat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Passages à plusieurs Interprètes (#135, #138, #139) : Invitations, consentement et rotation, en Java pur. */
class FileAttenteInvitationTest {
    private static final UUID SOIREE = UUID.randomUUID();
    private static final UUID MANCHE = UUID.randomUUID();
    private static final UUID ANA = new UUID(0, 1);
    private static final UUID BORIS = new UUID(0, 2);
    private static final UUID CELIA = new UUID(0, 3);
    private static final UUID DINA = new UUID(0, 4);
    private static final UUID ELI = new UUID(0, 5);
    private static final Instant RECU = Instant.parse("2026-11-24T12:00:00Z");
    private final FileAttente file = FileAttente.depuis(new EtatFileAttente(
            SOIREE, MANCHE, 10, 1, 1, 1, null, List.of(), Map.of()));
    private long numero;

    @Test
    void lePassageEntreAvecSonSeulInscripteurEtLaLimiteNeVisaQueLui() {
        file.reglerLimite(1);
        UUID boris = inscrire("Boris", BORIS);
        UUID duo = inscrire("Duo", List.of(BORIS), ANA);

        assertThat(file.trouver(duo).interpretes()).containsExactly(ANA);
        assertThat(file.trouver(duo).tourNormal()).isEqualTo(1);
        assertThat(file.trouver(duo).numeroModification()).isZero();
        assertThat(invitations()).containsExactly(new InvitationPassage(duo, BORIS, Etat.EN_ATTENTE));
        refuserSansMutation(() -> accepter(BORIS, duo), "LIMITE_ATTEINTE");

        assertThat(file.extraireProchainPassage(false).orElseThrow().id()).isEqualTo(boris);
        assertThat(accepter(BORIS, duo)).isInstanceOf(ResultatFile.Modifiee.class);

        assertThat(file.trouver(duo).interpretes()).containsExactly(ANA, BORIS);
        assertThat(file.trouver(duo).tourNormal()).isEqualTo(2);
        assertThat(file.trouver(duo).numeroModification()).isEqualTo(1);
        assertThat(invitations()).containsExactly(new InvitationPassage(duo, BORIS, Etat.ACCEPTEE));
        refuserSansMutation(() -> file.inscrire(nouveau("Autre", BORIS)), "LIMITE_ATTEINTE");
        assertThat(file.extraireProchainPassage(false).orElseThrow().id()).isEqualTo(duo);
        assertThat(file.etat().derniersToursConsommes()).containsExactlyInAnyOrderEntriesOf(Map.of(ANA, 2L, BORIS, 2L));
    }

    @Test
    void leTourDUnPassageAPlusieursCompteChacunDeSesInterpretes() {
        UUID groupe = inscrire("Groupe", List.of(BORIS, CELIA), ANA);
        UUID dina = inscrire("Dina", DINA);
        accepter(CELIA, groupe);
        accepter(BORIS, groupe);
        UUID boris = inscrire("Boris", BORIS);
        UUID celia = inscrire("Célia", CELIA);
        UUID dina2 = inscrire("Dina 2", DINA);
        UUID ana = inscrire("Ana", ANA);

        assertThat(file.trouver(groupe).interpretes()).containsExactly(ANA, CELIA, BORIS);
        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(groupe, dina, boris, celia, dina2, ana);
        assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 1L, 2L, 2L, 2L, 2L);
        file.extraireProchainPassage(false);
        assertThat(file.etat().derniersToursConsommes())
                .containsExactlyInAnyOrderEntriesOf(Map.of(ANA, 1L, BORIS, 1L, CELIA, 1L));
        assertThat(file.trouver(inscrire("Éli", ELI)).tourNormal()).isEqualTo(1);
    }

    @Test
    void uneAcceptationReculeLePassageAuTourLePlusTardifDeSesInterpretes() {
        UUID boris = inscrire("Boris", BORIS);
        UUID groupe = inscrire("Groupe", List.of(BORIS), ANA);
        UUID celia = inscrire("Célia", CELIA);
        var ancienne = cible(groupe);
        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(boris, groupe, celia);

        accepter(BORIS, groupe);

        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(boris, celia, groupe);
        assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 1L, 2L);
        refuserSansMutation(() -> file.desinscrire(ANA, ancienne, RECU), "COMMANDE_PERIMEE");
    }

    @Test
    void lAcceptationPlaceLePassageAccepteSansDeplacerLesAutresAttentesDeLInvite() {
        inscrire("Ana 1", ANA);
        file.extraireProchainPassage(false);
        UUID groupe = inscrire("Groupe", List.of(BORIS), ANA);
        UUID boris = inscrire("Boris", BORIS);
        assertThat(file.trouver(groupe).tourNormal()).isEqualTo(2);
        assertThat(file.trouver(boris).tourNormal()).isEqualTo(1);

        accepter(BORIS, groupe);

        assertThat(file.trouver(groupe).tourNormal()).isEqualTo(2);
        assertThat(file.trouver(groupe).numeroModification()).isEqualTo(1);
        assertThat(file.trouver(boris).tourNormal()).isEqualTo(1);
        assertThat(file.trouver(boris).numeroModification()).isZero();
        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(boris, groupe);
    }

    @Test
    void lePassageAccepteReculeDerriereLesAttentesDeLInviteQuiRestentInchangeesApresLAppelSuivant() {
        UUID celia = inscrire("Célia", CELIA);
        UUID groupe = inscrire("Groupe", List.of(BORIS), ANA);
        UUID boris = inscrire("Boris", BORIS);

        accepter(BORIS, groupe);

        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(celia, boris, groupe);
        assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 1L, 2L);
        assertThat(file.trouver(groupe).numeroModification()).isEqualTo(1);
        assertThat(file.trouver(boris).numeroModification()).isZero();

        assertThat(file.extraireProchainPassage(false).orElseThrow().id()).isEqualTo(celia);

        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(boris, groupe);
        assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 2L);
        assertThat(file.ordreEffectif()).extracting(Passage::numeroModification).containsExactly(0L, 1L);
        var restauree = FileAttente.depuis(file.etat());
        restauree.retirer(UUID.randomUUID(), new CiblePassage(boris, 0), RECU);
        assertThat(restauree.trouver(groupe).tourNormal()).isEqualTo(1);
    }

    @Test
    void uneAcceptationQuiNImposeRienNeReculePasLePassageDerriereLesAutresAttentesDeLInscripteur() {
        UUID groupe = inscrire("Groupe", List.of(BORIS), ANA);
        UUID solo = inscrire("Ana", ANA);

        accepter(BORIS, groupe);

        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(groupe, solo);
        assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 2L);
        assertThat(file.ordreEffectif()).extracting(Passage::numeroModification).containsExactly(1L, 0L);
    }

    @Test
    void laPrioriteComptePourLeTourDeChacunDesInterpretesAcceptes() {
        inscrire("Ana 1", ANA);
        file.extraireProchainPassage(false);
        UUID groupe = inscrire("Groupe", List.of(BORIS), ANA);
        UUID boris = inscrire("Boris", BORIS);
        accepter(BORIS, groupe);
        assertThat(file.trouver(groupe).tourNormal()).isEqualTo(2);
        assertThat(file.trouver(boris).tourNormal()).isEqualTo(1);
        assertThat(file.trouver(boris).numeroModification()).isZero();

        file.definirPriorite(cible(groupe), true);
        Passage appele = file.extraireProchainPassage(false).orElseThrow();

        assertThat(appele.id()).isEqualTo(groupe);
        assertThat(file.etat().derniersToursConsommes()).containsExactlyInAnyOrderEntriesOf(Map.of(ANA, 2L, BORIS, 1L));
        assertThat(file.trouver(boris).tourNormal()).isEqualTo(2);
    }

    @Test
    void refuseLesInvitationsInvalidesALInscriptionSansMutation() {
        refuserSansMutation(() -> file.inscrire(nouveau("Moi", ANA), List.of(BORIS, ANA)), "DONNEES_INVALIDES");
        var avant = file.etat();
        assertThatThrownBy(() -> file.inscrire(nouveau("Doublon", ANA), List.of(BORIS, BORIS)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> file.inscrire(nouveau("Foule", ANA), List.of(BORIS, CELIA, DINA, ELI)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(file.etat()).isEqualTo(avant);
        file.reglerLimite(1);
        inscrire("Ana", ANA);
        refuserSansMutation(() -> file.inscrire(nouveau("Encore", ANA), List.of(ANA)), "LIMITE_ATTEINTE");
    }

    @Test
    void unRefusNeFaitPasDeLInviteUnInterpreteEtClotLInvitation() {
        UUID passage = inscrire("Trio", List.of(BORIS, CELIA), ANA);
        refuserSansMutation(() -> file.repondreInvitation(DINA, cible(passage), true), "RESSOURCE_INTROUVABLE");
        refuserSansMutation(() -> file.repondreInvitation(BORIS, new CiblePassage(UUID.randomUUID(), 0), true),
                "RESSOURCE_INTROUVABLE");
        refuserSansMutation(() -> file.repondreInvitation(BORIS, new CiblePassage(passage, 4), true), "COMMANDE_PERIMEE");

        assertThat(file.repondreInvitation(BORIS, cible(passage), false)).isInstanceOf(ResultatFile.Modifiee.class);

        assertThat(file.trouver(passage).interpretes()).containsExactly(ANA);
        assertThat(file.trouver(passage).numeroModification()).isZero();
        refuserSansMutation(() -> accepter(BORIS, passage), "COMMANDE_PERIMEE");
        refuserSansMutation(() -> file.repondreInvitation(BORIS, cible(passage), false), "COMMANDE_PERIMEE");
        accepter(CELIA, passage);
        refuserSansMutation(() -> accepter(CELIA, passage), "COMMANDE_PERIMEE");
        assertThat(invitations()).extracting(InvitationPassage::inviteId, InvitationPassage::etat)
                .containsExactly(tuple(BORIS, Etat.REFUSEE), tuple(CELIA, Etat.ACCEPTEE));
    }

    @Test
    void lInviteQuiSeDesinscritQuitteLePassageQuiResteEtPeutAvancer() {
        UUID boris = inscrire("Boris", BORIS);
        UUID groupe = inscrire("Groupe", List.of(BORIS, CELIA), ANA);
        UUID dina = inscrire("Dina", DINA);
        accepter(BORIS, groupe);
        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(boris, dina, groupe);

        assertThat(file.desinscrire(BORIS, cible(groupe), RECU)).isInstanceOf(ResultatFile.Modifiee.class);

        assertThat(file.trouver(groupe).etat()).isEqualTo(EtatPassage.EN_ATTENTE);
        assertThat(file.trouver(groupe).interpretes()).containsExactly(ANA);
        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(boris, dina, groupe);
        assertThat(file.trouver(groupe).tourNormal()).isEqualTo(1);
        assertThat(invitations()).extracting(InvitationPassage::inviteId, InvitationPassage::etat)
                .containsExactly(tuple(BORIS, Etat.REFUSEE), tuple(CELIA, Etat.EN_ATTENTE));
        refuserSansMutation(() -> file.desinscrire(BORIS, cible(groupe), RECU), "COMMANDE_PERIMEE");
        refuserSansMutation(() -> file.desinscrire(CELIA, cible(groupe), RECU), "COMMANDE_PERIMEE");
        refuserSansMutation(() -> file.desinscrire(ELI, cible(groupe), RECU), "RESSOURCE_INTROUVABLE");
    }

    @Test
    void lInscripteurQuiSeDesinscritRetireToutLePassageEtFaitExpirerLesInvitationsOuvertes() {
        UUID groupe = inscrire("Groupe", List.of(BORIS, CELIA), ANA);
        accepter(BORIS, groupe);

        assertThat(file.desinscrire(ANA, cible(groupe), RECU)).isInstanceOf(ResultatFile.Modifiee.class);

        assertThat(file.trouver(groupe).etat()).isEqualTo(EtatPassage.RETIRE);
        assertThat(invitations()).extracting(InvitationPassage::inviteId, InvitationPassage::etat)
                .containsExactly(tuple(BORIS, Etat.ACCEPTEE), tuple(CELIA, Etat.EXPIREE));
        refuserSansMutation(() -> file.desinscrire(BORIS, cible(groupe), RECU), "COMMANDE_PERIMEE");
        refuserSansMutation(() -> accepter(CELIA, groupe), "COMMANDE_PERIMEE");
    }

    @Test
    void lAppelEtLaFinDeMancheFontExpirerLesInvitationsEnSuspens() {
        UUID groupe = inscrire("Groupe", List.of(BORIS, CELIA), ANA);
        UUID autre = inscrire("Autre", List.of(DINA), ELI);
        accepter(CELIA, groupe);

        Passage appele = file.extraireProchainPassage(false).orElseThrow();

        assertThat(appele.interpretes()).containsExactly(ANA, CELIA);
        assertThat(invitations()).extracting(InvitationPassage::inviteId, InvitationPassage::etat).containsExactly(
                tuple(BORIS, Etat.EXPIREE), tuple(CELIA, Etat.ACCEPTEE), tuple(DINA, Etat.EN_ATTENTE));
        refuserSansMutation(() -> accepter(BORIS, groupe), "COMMANDE_PERIMEE");
        file.annulerAttentes(RECU, UUID.randomUUID());
        assertThat(file.etat().invitations().getLast()).isEqualTo(new InvitationPassage(autre, DINA, Etat.EXPIREE));
    }

    @Test
    void leRetraitParLAnimateurFaitExpirerLesInvitations() {
        UUID groupe = inscrire("Groupe", List.of(BORIS), ANA);
        file.retirer(UUID.randomUUID(), cible(groupe), RECU);
        assertThat(invitations()).containsExactly(new InvitationPassage(groupe, BORIS, Etat.EXPIREE));
    }

    @Test
    void leDepartDeLInscripteurDonneLePassageAuPremierAccepte() {
        UUID groupe = inscrire("Groupe", List.of(BORIS, CELIA, DINA), ANA);
        UUID seul = inscrire("Seul", List.of(ELI), BORIS);
        accepter(DINA, groupe);
        accepter(CELIA, groupe);

        assertThat(file.retirerJoueurParti(ANA, RECU)).isInstanceOf(ResultatFile.Modifiee.class);

        assertThat(file.trouver(groupe).inscripteurId()).isEqualTo(DINA);
        assertThat(file.trouver(groupe).interpretes()).containsExactly(DINA, CELIA);
        assertThat(file.retirerJoueurParti(ELI, RECU)).isInstanceOf(ResultatFile.Modifiee.class);
        assertThat(file.etat().invitations()).contains(new InvitationPassage(seul, ELI, Etat.EXPIREE));
        assertThat(file.retirerJoueurParti(ELI, RECU)).isInstanceOf(ResultatFile.SansEffet.class);
        file.retirerJoueurParti(DINA, RECU);
        file.retirerJoueurParti(CELIA, RECU);
        assertThat(file.trouver(groupe).etat()).isEqualTo(EtatPassage.RETIRE);
        assertThat(file.etat().invitations()).contains(new InvitationPassage(groupe, BORIS, Etat.EXPIREE));
    }

    /** L’Invitation disait « Ana t’invite » : sans Ana, elle n’a plus d’auteur et expire (#205). */
    @Test
    void leDepartDeLInscripteurFaitExpirerSesInvitationsOuvertes() {
        UUID groupe = inscrire("Groupe", List.of(BORIS, CELIA, DINA), ANA);
        UUID autre = inscrire("Autre", List.of(ELI), BORIS);
        accepter(CELIA, groupe);
        long compteur = file.trouver(groupe).numeroModification();

        assertThat(file.retirerJoueurParti(ANA, RECU)).isInstanceOf(ResultatFile.Modifiee.class);

        assertThat(file.trouver(groupe).inscripteurId()).isEqualTo(CELIA);
        assertThat(file.trouver(groupe).interpretes()).containsExactly(CELIA);
        assertThat(file.trouver(groupe).numeroModification()).isGreaterThan(compteur);
        assertThat(invitations()).extracting(InvitationPassage::passageId, InvitationPassage::inviteId, InvitationPassage::etat)
                .containsExactly(tuple(groupe, BORIS, Etat.EXPIREE), tuple(groupe, CELIA, Etat.ACCEPTEE),
                        tuple(groupe, DINA, Etat.EXPIREE), tuple(autre, ELI, Etat.EN_ATTENTE));
        refuserSansMutation(() -> accepter(DINA, groupe), "COMMANDE_PERIMEE");
    }

    @Test
    void lInterpreteAccepteQuiPartFermeSonInvitationCommeUnDepartVolontaire() {
        UUID groupe = inscrire("Groupe", List.of(BORIS, CELIA, DINA), ANA);
        accepter(BORIS, groupe);
        accepter(CELIA, groupe);

        assertThat(file.retirerJoueurParti(BORIS, RECU)).isInstanceOf(ResultatFile.Modifiee.class);

        assertThat(file.trouver(groupe).interpretes()).containsExactly(ANA, CELIA);
        assertThat(invitations()).extracting(InvitationPassage::inviteId, InvitationPassage::etat).containsExactly(
                tuple(BORIS, Etat.REFUSEE), tuple(CELIA, Etat.ACCEPTEE), tuple(DINA, Etat.EN_ATTENTE));
        file.retirerJoueurParti(ANA, RECU);
        assertThat(file.trouver(groupe).inscripteurId()).isEqualTo(CELIA);
        assertThat(file.retirerJoueurParti(CELIA, RECU)).isInstanceOf(ResultatFile.Modifiee.class);
        assertThat(file.trouver(groupe).etat()).isEqualTo(EtatPassage.RETIRE);
        assertThat(invitations()).extracting(InvitationPassage::inviteId, InvitationPassage::etat).containsExactly(
                tuple(BORIS, Etat.REFUSEE), tuple(CELIA, Etat.REFUSEE), tuple(DINA, Etat.EXPIREE));
    }

    @Test
    void laCaptureRestaureLesInvitationsDansLeurOrdre() {
        UUID passage = inscrire("Groupe", List.of(DINA, BORIS, CELIA), ANA);
        accepter(CELIA, passage);
        file.repondreInvitation(DINA, cible(passage), false);
        var etat = file.etat();
        assertThat(FileAttente.depuis(etat).etat()).isEqualTo(etat);
        assertThat(etat.invitations()).extracting(InvitationPassage::inviteId).containsExactly(DINA, BORIS, CELIA);
    }

    private ResultatFile accepter(UUID invite, UUID passage) {
        return file.repondreInvitation(invite, cible(passage), true);
    }

    private List<InvitationPassage> invitations() { return file.etat().invitations(); }

    private CiblePassage cible(UUID id) {
        return new CiblePassage(id, file.trouver(id).numeroModification());
    }

    private void refuserSansMutation(Supplier<ResultatFile> commande, String code) {
        var avant = file.etat();
        assertThat(commande.get()).isInstanceOfSatisfying(ResultatFile.Refus.class,
                refus -> assertThat(refus.refus().code()).isEqualTo(code));
        assertThat(file.etat()).isEqualTo(avant);
    }

    private UUID inscrire(String contenu, UUID inscrivant) {
        return inscrire(contenu, List.of(), inscrivant);
    }

    private UUID inscrire(String contenu, List<UUID> invites, UUID inscrivant) {
        var nouveau = nouveau(contenu, inscrivant);
        assertThat(file.inscrire(nouveau, invites)).isInstanceOf(ResultatFile.Modifiee.class);
        return nouveau.id();
    }

    private NouveauPassage nouveau(String contenu, UUID inscrivant) {
        return new NouveauPassage(UUID.randomUUID(), SOIREE, MANCHE, inscrivant, List.of(inscrivant),
                contenu, Duration.ofMinutes(3), ++numero, RECU);
    }
}
