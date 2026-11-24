package fr.croustyquizz.passage;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/** Routes métier des Passages, partagées par le Karaoké et le Freestyle. */
public abstract class ServiceDePassages<R, E> extends ServiceDeManche<R, E> {
    protected final SocleDeManche socle;
    protected final ParticipantsDePassage participants;
    protected final PersistancePassageJpa passages;
    private final PorteeDePassage portee;

    protected ServiceDePassages(FabriqueDeDeroulement<R, E> fabrique, PersistanceDuMode<E> persistance,
            SocleDeManche socle, PorteeDePassage portee, ParticipantsDePassage participants, PersistancePassageJpa passages) {
        super(fabrique, persistance, socle);
        this.socle = socle;
        this.portee = portee;
        this.participants = participants;
        this.passages = passages;
    }

    public abstract ResultatDeCommande inscrire(UUID mancheId, EnvoiDeCommande envoi, CorpsPassage.Inscription corps);

    @Override public ResultatDeCommande demarrer(UUID mancheId, EnvoiDeCommande envoi) {
        verifierEntree(mancheId, envoi, Acteur.Type.ANIMATEUR);
        return super.demarrer(mancheId, envoi);
    }
    @Override public ResultatDeCommande piloter(UUID mancheId, EnvoiDeCommande envoi, InterventionDeManche intervention) {
        verifierEntree(mancheId, envoi, Acteur.Type.ANIMATEUR);
        return super.piloter(mancheId, envoi, intervention);
    }
    @Override protected Refuse verifierEtat(ManchePersistante manche, boolean demarrage) {
        if (demarrage) { return super.verifierEtat(manche, true); }
        if (manche.getEtat() == EtatManche.PRETE) { throw inactive().exception(); }
        return manche.getEtat() == EtatManche.TERMINEE
                ? new Refuse(409, "ETAT_INCOMPATIBLE", "Cette Manche est terminée.") : null;
    }
    @Override protected Refuse verifierAvantPreparation(ManchePersistante manche, EnvoiDeCommande envoi, boolean demarrage) {
        Refuse etat = verifierEtat(manche, demarrage);
        if (etat != null) { return etat; }
        verifierAcces(envoi.acteur(), manche.getSoireeId(), manche.getMancheId());
        if (!demarrage) {
            verifierActivite(manche.getSoireeId(), manche.getMancheId());
            if (passages.charger(manche.getMancheId()) == null) { return introuvable(); }
        }
        return null;
    }
    @Override protected boolean doitConserverRecu(ResultatDeCommande resultat) {
        return !(resultat instanceof Refuse refus && "ETAPE_INACTIVE".equals(refus.code()));
    }

    public ResultatDeCommande desinscrirePassage(UUID mancheId, UUID passageId, EnvoiDeCommande envoi, CorpsPassage.DesinscriptionPassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.JOUEUR);
        socle.verrous().executer(envoi.acteur().soireeId(), () -> { verifierInscripteur(mancheId, passageId, envoi.acteur()); return null; });
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) -> {
            verifierInscripteur(mancheId, passageId, envoi.acteur());
            return d.recevoir(new DesinscrirePassage(envoi.acteur(), recueLe, envoi.envoiId(),
                    new CiblePassage(passageId, corps.numeroModification())));
        }));
    }
    public ResultatDeCommande voterPassage(UUID mancheId, UUID passageId, EnvoiDeCommande envoi, CorpsPassage.VotePassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.JOUEUR);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) ->
                d.recevoir(new VoterPassage(envoi.acteur(), recueLe, envoi.envoiId(),
                        new CiblePassage(passageId, corps.numeroModification()), corps.note().intValue()))));
    }
    public ResultatDeCommande retirerPassage(UUID mancheId, UUID passageId, EnvoiDeCommande envoi, CorpsPassage.RetraitPassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.ANIMATEUR);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) -> d.commander(new RetirerPassage(envoi.acteur(),
                recueLe, envoi.envoiId(), new CiblePassage(passageId, corps.numeroModification())))));
    }
    public ResultatDeCommande deplacerPassage(UUID mancheId, UUID passageId, EnvoiDeCommande envoi, CorpsPassage.PlacementPassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.ANIMATEUR);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) -> d.commander(new DeplacerPassage(envoi.acteur(),
                recueLe, envoi.envoiId(), new CiblePassage(passageId, corps.numeroModification()),
                corps.tour(), corps.avant() == null ? null : corps.avant().cible()))));
    }
    public ResultatDeCommande definirPrioritePassage(UUID mancheId, EnvoiDeCommande envoi, CorpsPassage.PrioritePassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.ANIMATEUR);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) -> d.commander(new DefinirPrioritePassage(envoi.acteur(),
                recueLe, envoi.envoiId(), corps.passage().cible(), corps.prioritaire()))));
    }
    public ResultatDeCommande reglerLimitePassages(UUID mancheId, EnvoiDeCommande envoi, CorpsPassage.LimitePassages corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.ANIMATEUR);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) -> d.commander(new ReglerLimitePassages(envoi.acteur(),
                recueLe, envoi.envoiId(), corps.limitePassagesEnAttente()))));
    }

    public ResultatDeCommande appelerPassage(UUID mancheId, UUID passageId, EnvoiDeCommande envoi, CorpsPassage.AppelPassage corps) {
        return commander(mancheId, envoi, Acteur.Type.ANIMATEUR, maintenant -> new AppelerPassage(envoi.acteur(),
                maintenant, envoi.envoiId(), new CiblePassage(passageId, corps.numeroModification())));
    }

    public ResultatDeCommande confirmerPresencePassage(UUID mancheId, UUID passageId, EnvoiDeCommande envoi,
            CorpsPassage.ConfirmationPresencePassage corps) {
        Acteur acteur = envoi.acteur();
        verifierEntree(mancheId, envoi, Acteur.Type.JOUEUR);
        lecture(acteur.soireeId(), () -> {
            verifierInterprete(mancheId, passageId, acteur);
            return null;
        });
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) -> {
            verifierInterprete(mancheId, passageId, acteur);
            return d.recevoir(new ConfirmerPresencePassage(acteur, recueLe, envoi.envoiId(),
                    new CiblePassage(passageId, corps.numeroModification()), corps.etapeId()));
        }));
    }

    private void verifierInterprete(UUID mancheId, UUID passageId, Acteur acteur) {
        var file = passages.charger(mancheId);
        if (file != null && file.passages().stream().anyMatch(p -> p.id().equals(passageId)
                && !p.interpretes().contains(acteur.identifiant()))) {
            throw introuvable().exception();
        }
    }

    protected ResultatDeCommande commander(UUID mancheId, EnvoiDeCommande envoi, Acteur.Type role,
            Function<Instant, CommandeDeManche> commande) {
        verifierEntree(mancheId, envoi, role);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete(
                (d, recueLe) -> d.commander(commande.apply(recueLe))));
    }

    protected void verifierEntree(UUID mancheId, EnvoiDeCommande envoi, Acteur.Type role) {
        AccesAuxVues.verifier(envoi.acteur(), envoi.acteur().soireeId(), role);
        verifierAcces(envoi.acteur(), envoi.acteur().soireeId(), mancheId);
    }
    protected void verifierAcces(Acteur acteur, UUID soireeId, UUID mancheId) {
        portee.verifier(acteur, soireeId, mancheId);
        if (acteur.type() == Acteur.Type.JOUEUR && !participants.estPresent(soireeId, acteur.identifiant())) {
            throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Manche ou Joueur introuvable.").exception();
        }
    }
    private void verifierInscripteur(UUID mancheId, UUID passageId, Acteur acteur) {
        EtatFileAttente file = passages.charger(mancheId);
        if (file != null && file.passages().stream().anyMatch(p -> p.id().equals(passageId) && !p.inscripteurId().equals(acteur.identifiant()))) {
            throw introuvable().exception();
        }
    }
    protected void verifierActivite(UUID soireeId, UUID mancheId) {
        if (!portee.estActive(soireeId, mancheId)) { throw inactive().exception(); }
    }
    public <T> T lecture(UUID soireeId, Supplier<T> lire) {
        return socle.verrous().executer(soireeId, () -> new TransactionTemplate(socle.transactions()).execute(s -> lire.get()));
    }
    protected static Refuse introuvable() { return new Refuse(404, "RESSOURCE_INTROUVABLE", "Ressource introuvable."); }
    protected static Refuse inactive() { return new Refuse(409, "ETAPE_INACTIVE", "La Manche n’est pas active."); }
}
