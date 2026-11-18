package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Domaine du Karaoké, sans source externe ni publication. */
public final class DeroulementKaraoke implements DeroulementDeManche {
    private static final Set<Destination> ECRANS = Set.of(
            Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);
    private final ContexteDeManche contexte;
    private final Duration delaiAppel;
    private final FileAttente file;
    private final Map<UUID, ContenuKaraoke> contenus = new HashMap<>();
    private EtatManche etat = EtatManche.PRETE;

    public DeroulementKaraoke(ContexteDeManche contexte, ReglagesKaraoke reglages, Duration delaiAppel) {
        this.contexte = contexte;
        this.delaiAppel = delaiAppel;
        this.file = FileAttente.depuis(new EtatFileAttente(contexte.soireeId(), contexte.mancheId(),
                reglages.limitePassagesEnAttente(), 1, 1, 1, null, List.of(), Map.of()));
    }

    DeroulementKaraoke(ContexteDeManche contexte, Duration delaiAppel,
            EtatKaraokePersiste persiste, EtatManche etatManche) {
        if (etatManche == EtatManche.PRETE) {
            throw new IllegalStateException("Une Manche prête ne se restaure pas.");
        }
        this.contexte = contexte;
        this.delaiAppel = delaiAppel;
        this.file = FileAttente.depuis(persiste.file());
        this.contenus.putAll(persiste.contenus());
        this.etat = Objects.requireNonNull(etatManche);
    }

    @Override public void demarrer() {
        if (etat != EtatManche.PRETE) { throw new IllegalStateException("La Manche a déjà démarré."); }
        etat = EtatManche.EN_COURS;
    }

    private Refuse gardeCommune() {
        if (etat == EtatManche.TERMINEE) { return incompatible(); }
        if (etat == EtatManche.PRETE) { return inactive(); }
        return null;
    }

    private static Refuse incompatible() {
        return new Refuse(409, "ETAT_INCOMPATIBLE", "Cette commande n’est pas compatible avec l’état de la Manche.");
    }

    private static Refuse inactive() {
        return new Refuse(409, "ETAPE_INACTIVE", "Aucune étape active ne permet cette commande.");
    }

    public static Optional<MotifIndisponibilite> motifInscription(FileAttente file, UUID joueurId, Chanson chanson) {
        if (chanson.etatParoles() == EtatParoles.SANS_VOIX) { return Optional.of(MotifIndisponibilite.SANS_VOIX); }
        if (chanson.etatParoles() != EtatParoles.SYNCHRONISEES) {
            return Optional.of(MotifIndisponibilite.PAROLES_NON_SYNCHRONISEES);
        }
        if (chanson.dureeMs() == null || chanson.dureeMs() <= 0) { return Optional.of(MotifIndisponibilite.DUREE_INCONNUE); }
        return file.verifierInscription(joueurId, chanson.chansonId()).map(r -> MotifIndisponibilite.valueOf(r.code()));
    }

    @Override public ResultatDeCommande recevoir(ActionJoueur action) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        if (action instanceof DesinscrirePassage desinscription) {
            return resultatPassage(file.desinscrire(desinscription.joueurId(), desinscription.cible(), desinscription.recueLe()),
                    desinscription.envoiId(), desinscription.recueLe(), desinscription.cible(), "DESINSCRIRE");
        }
        if (!(action instanceof InscrirePassage inscription)) { return incompatible(); }
        Chanson chanson = inscription.contenu().chanson();
        // Une fiche synchronisée sans ligne exploitable reste indisponible, avant la durée et le quota.
        if (chanson.etatParoles() == EtatParoles.SYNCHRONISEES && inscription.contenu().lignes().stream()
                .noneMatch(l -> l.positionMs() >= 0 && l.texte() != null && !l.texte().trim().isEmpty())) {
            chanson = new Chanson(chanson.chansonId(), chanson.idLrclib(), chanson.titre(), chanson.artiste(),
                    chanson.album(), chanson.dureeMs(), EtatParoles.NON_SYNCHRONISEES);
        }
        Optional<MotifIndisponibilite> motif = motifInscription(file, inscription.joueurId(), chanson);
        if (motif.isPresent()) {
            MotifIndisponibilite raison = motif.get();
            boolean indisponible = raison == MotifIndisponibilite.SANS_VOIX
                    || raison == MotifIndisponibilite.PAROLES_NON_SYNCHRONISEES || raison == MotifIndisponibilite.DUREE_INCONNUE;
            return new Refuse(indisponible ? 422 : 409, indisponible ? "CHANSON_INDISPONIBLE" : raison.name(), raison.name());
        }
        ResultatFile resultat = file.inscrire(new NouveauPassage(inscription.faits().passageId(),
                contexte.soireeId(), contexte.mancheId(), inscription.joueurId(), List.of(inscription.joueurId()),
                inscription.chansonId(), Duration.ofMillis(chanson.dureeMs()), inscription.faits().numeroPropose(), inscription.recueLe()));
        if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
        contenus.put(inscription.faits().passageId(), inscription.contenu());
        return new Accepte(new AccusePassage(inscription.envoiId(), contexte.soireeId(), contexte.mancheId(),
                inscription.faits().passageId(), 0, "INSCRIRE", inscription.recueLe(), EtatPassage.EN_ATTENTE),
                ECRANS, "file-attente-modifiee", 201);
    }

    @Override public ResultatDeCommande commander(CommandeDeManche commande) {
        Refuse refus = gardeCommune();
        if (refus != null) { return refus; }
        if (commande instanceof RetirerPassage retrait) {
            return resultatPassage(file.retirer(retrait.acteur().identifiant(), retrait.cible(), retrait.recueLe()),
                    retrait.envoiId(), retrait.recueLe(), retrait.cible(), "RETIRER");
        }
        if (commande instanceof DeplacerPassage placement) {
            return resultatPassage(file.deplacer(placement.cible(), placement.tour(), placement.avant()),
                    placement.envoiId(), placement.recueLe(), placement.cible(), "DEPLACER");
        }
        if (commande instanceof DefinirPrioritePassage priorite) {
            ResultatFile resultat = file.definirPriorite(priorite.passage(), priorite.prioritaire());
            if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
            Passage passage = passage(priorite.passage());
            return accepter(resultat, new AccuseFileAttente(priorite.envoiId(), contexte.soireeId(), contexte.mancheId(),
                    "DEFINIR_PRIORITE", priorite.recueLe(), new CiblePassage(passage.id(), passage.numeroModification())),
                    "file-attente-modifiee");
        }
        if (commande instanceof ReglerLimitePassages limite) {
            ResultatFile resultat = file.reglerLimite(limite.limitePassagesEnAttente());
            return accepter(resultat, new AccuseFileAttente(limite.envoiId(), contexte.soireeId(), contexte.mancheId(),
                    "REGLER_LIMITE", limite.recueLe(), null), "limite-passages-modifiee");
        }
        return incompatible();
    }

    private Passage passage(CiblePassage cible) {
        return file.etat().passages().stream().filter(p -> p.id().equals(cible.passageId())).findFirst().orElseThrow();
    }

    private ResultatDeCommande resultatPassage(ResultatFile resultat, UUID envoiId, Instant recueLe,
            CiblePassage cible, String action) {
        if (resultat instanceof ResultatFile.Refus r) { return r.refus(); }
        Passage passage = passage(cible);
        return accepter(resultat, new AccusePassage(envoiId, contexte.soireeId(), contexte.mancheId(), passage.id(),
                passage.numeroModification(), action, recueLe, passage.etat()), "file-attente-modifiee");
    }

    private Accepte accepter(ResultatFile resultat, Object accuse, String typeEvenement) {
        return new Accepte(accuse, resultat instanceof ResultatFile.Modifiee ? ECRANS : Set.of(), typeEvenement, 200);
    }

    @Override public ResultatDeCommande mettreEnPause(CommandePilotage commande) {
        Refuse refus = gardeCommune(); if (refus != null) { return refus; }
        if (etat != EtatManche.EN_COURS) { return incompatible(); }
        etat = EtatManche.EN_PAUSE;
        return new Accepte(commande.accuse(), ECRANS, "manche-en-pause");
    }

    @Override public ResultatDeCommande reprendre(CommandePilotage commande) {
        Refuse refus = gardeCommune(); if (refus != null) { return refus; }
        if (etat != EtatManche.EN_PAUSE) { return incompatible(); }
        etat = EtatManche.EN_COURS;
        return new Accepte(commande.accuse(), ECRANS, "manche-reprise");
    }

    @Override public ResultatDeCommande passerEtape(CommandePilotage commande) {
        Refuse refus = gardeCommune(); return refus == null ? inactive() : refus;
    }

    @Override public ResultatDeCommande terminer(CommandePilotage commande) {
        Refuse refus = gardeCommune(); if (refus != null) { return refus; }
        file.annulerAttentes(commande.recueLe(), commande.acteur().identifiant());
        etat = EtatManche.TERMINEE;
        return new Accepte(commande.accuse(), ECRANS, "manche-terminee");
    }

    @Override public boolean viseEtape(UUID etapeId) { return etapeId == null; }
    @Override public InstantaneKaraoke instantane() { return new InstantaneKaraoke(etat, file.etat(), contenus); }
    @Override public boolean estTerminee() { return etat == EtatManche.TERMINEE; }
    @Override public List<AttributionDePoints> points() { return List.of(); }

    public Map<UUID, AttenteEstimee> estimerAttentes(Instant prochainAppelPrevuLe) {
        return file.estimerAttentes(contexte.horloge().maintenant(), etat == EtatManche.EN_PAUSE,
                prochainAppelPrevuLe, false, delaiAppel);
    }
}
