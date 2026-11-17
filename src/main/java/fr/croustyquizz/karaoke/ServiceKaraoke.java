package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** Accès et préparation externe ; le domaine décide dans la transaction finale. */
public class ServiceKaraoke extends ServiceDeManche<ReglagesKaraoke, EtatKaraokePersiste> {
    private static final String CLE_LIMITE = "limitePassagesEnAttente";
    private final SocleDeManche socle;
    private final PorteeDePassage portee;
    private final ParticipantsDePassage participants;
    private final PersistancePassageJpa passages;
    private final SourceDeParoles source;
    private final ProprietesKaraoke proprietes;
    private final ProprietesSourceKaraoke proprietesSource;

    public ServiceKaraoke(FabriqueKaraoke fabrique, PersistanceDuMode<EtatKaraokePersiste> persistance,
            SocleDeManche socle, PorteeDePassage portee, ParticipantsDePassage participants,
            PersistancePassageJpa passages, SourceDeParoles source, ProprietesKaraoke proprietes,
            ProprietesSourceKaraoke proprietesSource) {
        super(fabrique, persistance, socle);
        this.socle = socle; this.portee = portee; this.participants = participants; this.passages = passages;
        this.source = source; this.proprietes = proprietes; this.proprietesSource = proprietesSource;
    }

    @Override public UUID creer(UUID soireeId, Object reglages) {
        JsonNode objet = socle.json().valueToTree(reglages);
        if (!objet.isObject() || objet.propertyNames().stream().anyMatch(n -> !CLE_LIMITE.equals(n))) {
            CorpsKaraoke.invalide();
        }
        Map<String, Object> resolus = new HashMap<>();
        objet.properties().forEach(e -> resolus.put(e.getKey(), e.getValue()));
        if (!resolus.containsKey(CLE_LIMITE)) {
            resolus.put(CLE_LIMITE, proprietes.passage().limitePassagesEnAttente());
        }
        JsonNode valeur = socle.json().valueToTree(resolus.get(CLE_LIMITE));
        if (!valeur.isIntegralNumber() || !valeur.canConvertToLong()) { CorpsKaraoke.invalide(); }
        CorpsKaraoke.entier(valeur.longValue(), false);
        resolus.put(CLE_LIMITE, valeur.longValue());
        return super.creer(soireeId, resolus);
    }

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

    public ResultatDeCommande inscrirePassage(UUID mancheId, EnvoiDeCommande envoi, CorpsKaraoke.InscriptionPassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.JOUEUR);
        return executer(mancheId, envoi, () -> {
            Optional<ContenuKaraoke> contenu = chargerContenu(corps.chansonId());
            return PreparationDeCommande.prete(d -> {
                if (contenu.isEmpty()) { return introuvable(); }
                long numero = passages.numeroPropose(envoi.acteur().soireeId());
                ResultatDeCommande resultat = d.recevoir(new InscrirePassage(envoi.acteur(), socle.horloge().maintenant(),
                        envoi.envoiId(), new FaitsInscription(UUID.randomUUID(), numero), corps.chansonId(), contenu.get()));
                if (resultat instanceof Accepte) { passages.incrementerNumero(envoi.acteur().soireeId(), numero); }
                return resultat;
            });
        });
    }
    public ResultatDeCommande desinscrirePassage(UUID mancheId, UUID passageId, EnvoiDeCommande envoi, CorpsKaraoke.DesinscriptionPassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.JOUEUR);
        socle.verrous().executer(envoi.acteur().soireeId(), () -> { verifierInscripteur(mancheId, passageId, envoi.acteur()); return null; });
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete(d -> {
            verifierInscripteur(mancheId, passageId, envoi.acteur());
            return d.recevoir(new DesinscrirePassage(envoi.acteur(), socle.horloge().maintenant(), envoi.envoiId(),
                    new CiblePassage(passageId, corps.numeroModification())));
        }));
    }
    public ResultatDeCommande retirerPassage(UUID mancheId, UUID passageId, EnvoiDeCommande envoi, CorpsKaraoke.RetraitPassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.ANIMATEUR);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete(d -> d.commander(new RetirerPassage(envoi.acteur(),
                socle.horloge().maintenant(), envoi.envoiId(), new CiblePassage(passageId, corps.numeroModification())))));
    }
    public ResultatDeCommande deplacerPassage(UUID mancheId, UUID passageId, EnvoiDeCommande envoi, CorpsKaraoke.PlacementPassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.ANIMATEUR);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete(d -> d.commander(new DeplacerPassage(envoi.acteur(),
                socle.horloge().maintenant(), envoi.envoiId(), new CiblePassage(passageId, corps.numeroModification()),
                corps.tour(), corps.avant() == null ? null : corps.avant().cible()))));
    }
    public ResultatDeCommande definirPrioritePassage(UUID mancheId, EnvoiDeCommande envoi, CorpsKaraoke.PrioritePassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.ANIMATEUR);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete(d -> d.commander(new DefinirPrioritePassage(envoi.acteur(),
                socle.horloge().maintenant(), envoi.envoiId(), corps.passage().cible(), corps.prioritaire()))));
    }
    public ResultatDeCommande reglerLimitePassages(UUID mancheId, EnvoiDeCommande envoi, CorpsKaraoke.LimitePassages corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.ANIMATEUR);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete(d -> d.commander(new ReglerLimitePassages(envoi.acteur(),
                socle.horloge().maintenant(), envoi.envoiId(), corps.limitePassagesEnAttente()))));
    }

    public VuesKaraoke.ResultatsChansons rechercherChansons(Acteur acteur, UUID soireeId, UUID mancheId, String recherche, UUID rechercheId) {
        if (rechercheId == null || recherche == null || recherche.codePointCount(0, recherche.length()) < 1
                || recherche.codePointCount(0, recherche.length()) > 120) { CorpsKaraoke.invalide(); }
        lecture(soireeId, () -> { verifierLecture(acteur, soireeId, mancheId); return null; });
        String nettoyee;
        try { nettoyee = RechercheKaraoke.nettoyer(recherche, proprietesSource.longueurRechercheMax()); }
        catch (IllegalArgumentException erreur) { throw new Refuse(400, "REQUETE_INVALIDE", "La recherche est invalide.").exception(erreur); }
        var chansons = appelerSource(() -> RechercheKaraoke.classer(nettoyee, source.rechercher(nettoyee)));
        return lecture(soireeId, () -> {
            verifierLecture(acteur, soireeId, mancheId);
            return new VuesKaraoke.ResultatsChansons(rechercheId, nettoyee, chansons.stream().map(VuesKaraoke.ResumeChanson::de).toList());
        });
    }
    public VuesKaraoke.FicheChanson consulterChanson(Acteur acteur, UUID soireeId, UUID mancheId, String chansonId) {
        if (chansonId == null || chansonId.isEmpty()) { CorpsKaraoke.invalide(); }
        lecture(soireeId, () -> { verifierLecture(acteur, soireeId, mancheId); return null; });
        var contenu = chargerContenu(chansonId);
        return lecture(soireeId, () -> {
            verifierLecture(acteur, soireeId, mancheId);
            if (contenu.isEmpty()) { throw introuvable().exception(); }
            var chanson = contenu.get().chanson();
            MotifIndisponibilite motif;
            if (acteur.type() == Acteur.Type.ANIMATEUR) { motif = MotifIndisponibilite.ANIMATEUR; }
            else {
                // Même rang de refus que recevoir : les lignes priment sur la durée et le quota.
                if (chanson.etatParoles() == EtatParoles.SYNCHRONISEES && contenu.get().lignes().stream()
                        .noneMatch(l -> l.positionMs() >= 0 && l.texte() != null && !l.texte().trim().isEmpty())) {
                    chanson = new Chanson(chanson.chansonId(), chanson.idLrclib(), chanson.titre(), chanson.artiste(),
                            chanson.album(), chanson.dureeMs(), EtatParoles.NON_SYNCHRONISEES);
                }
                var instantane = (InstantaneKaraoke) instantane(soireeId, mancheId);
                motif = DeroulementKaraoke.motifInscription(FileAttente.depuis(instantane.file()), acteur.identifiant(), chanson).orElse(null);
            }
            return new VuesKaraoke.FicheChanson(VuesKaraoke.ResumeChanson.de(chanson), motif == null, motif);
        });
    }

    private void verifierEntree(UUID mancheId, EnvoiDeCommande envoi, Acteur.Type role) {
        AccesAuxVues.verifier(envoi.acteur(), envoi.acteur().soireeId(), role);
        verifierAcces(envoi.acteur(), envoi.acteur().soireeId(), mancheId);
    }
    private void verifierAcces(Acteur acteur, UUID soireeId, UUID mancheId) {
        portee.verifier(acteur, soireeId, mancheId);
        if (acteur.type() == Acteur.Type.JOUEUR && !participants.estPresent(soireeId, acteur.identifiant())) { throw introuvable().exception(); }
    }
    private void verifierInscripteur(UUID mancheId, UUID passageId, Acteur acteur) {
        EtatFileAttente file = passages.charger(mancheId);
        if (file != null && file.passages().stream().anyMatch(p -> p.id().equals(passageId) && !p.inscripteurId().equals(acteur.identifiant()))) {
            throw introuvable().exception();
        }
    }
    private void verifierLecture(Acteur acteur, UUID soireeId, UUID mancheId) {
        AccesAuxVues.verifier(acteur, soireeId, null);
        if (acteur.type() == Acteur.Type.ECRAN) { throw new Refuse(403, "ACCES_REFUSE", "Cet accès est réservé aux Manettes et à la Console.").exception(); }
        verifierAcces(acteur, soireeId, mancheId);
        var manche = socle.depot().charger(mancheId);
        if (manche == null || !manche.getSoireeId().equals(soireeId) || manche.getMode() != mode()) { throw introuvable().exception(); }
        if (manche.getEtat() != EtatManche.EN_COURS && manche.getEtat() != EtatManche.EN_PAUSE) { throw inactive().exception(); }
        verifierActivite(soireeId, mancheId);
    }
    private void verifierActivite(UUID soireeId, UUID mancheId) {
        if (!portee.estActive(soireeId, mancheId)) { throw inactive().exception(); }
    }
    private <T> T lecture(UUID soireeId, Supplier<T> lire) {
        return socle.verrous().executer(soireeId, () -> new TransactionTemplate(socle.transactions()).execute(s -> lire.get()));
    }
    private Optional<ContenuKaraoke> chargerContenu(String chansonId) {
        var id = IdentifiantsChanson.decoder(chansonId);
        return id.isEmpty() ? Optional.empty() : appelerSource(() -> source.charger(id.getAsLong()));
    }
    private <T> T appelerSource(Supplier<T> appel) {
        try { return appel.get(); }
        catch (SourceParolesIndisponible erreur) {
            throw new Refuse(503, "SOURCE_PAROLES_INDISPONIBLE", "La source de paroles est temporairement indisponible.").exception(erreur);
        }
    }
    private static Refuse introuvable() { return new Refuse(404, "RESSOURCE_INTROUVABLE", "Ressource introuvable."); }
    private static Refuse inactive() { return new Refuse(409, "ETAPE_INACTIVE", "La Manche n'est pas active."); }
}
