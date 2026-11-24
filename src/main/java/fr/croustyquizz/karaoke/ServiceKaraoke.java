package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;

/** Accès et préparation externe ; le domaine décide dans la transaction finale. */
public class ServiceKaraoke extends ServiceDePassages<ReglagesKaraoke, EtatKaraokePersiste> {
    private static final String CLE_LIMITE = "limitePassagesEnAttente";
    private final PersistanceKaraokeJpa persistance;
    private final SourceDeParoles source;
    private final ProprietesKaraoke proprietes;
    private final ProprietesSourceKaraoke proprietesSource;
    private final ChoixDeVersionParoles choixParoles;
    private final MemoireDeCalageJpa calages;

    public ServiceKaraoke(FabriqueKaraoke fabrique, PersistanceKaraokeJpa persistance,
            SocleDeManche socle, PorteeDePassage portee, ParticipantsDePassage participants,
            PersistancePassageJpa passages, SourceDeParoles source, ProprietesKaraoke proprietes,
            ProprietesSourceKaraoke proprietesSource, ChoixDeVersionParoles choixParoles, MemoireDeCalageJpa calages) {
        super(fabrique, persistance, socle, portee, participants, passages);
        this.persistance = persistance;
        this.source = source; this.proprietes = proprietes; this.proprietesSource = proprietesSource;
        this.choixParoles = choixParoles; this.calages = calages;
    }

    @Override protected ReglagesKaraoke resoudreReglages(Object reglages) {
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
        CorpsKaraoke.limite(valeur.longValue());
        resolus.put(CLE_LIMITE, valeur.longValue());
        return super.resoudreReglages(resolus);
    }

    @Override protected EtatManche etatApresAcceptation(EtatManche etatParDefaut, Object instantane) {
        return ((InstantaneKaraoke) instantane).etatManche();
    }
    @Override public ResultatDeCommande inscrire(UUID mancheId, EnvoiDeCommande envoi, CorpsPassage.Inscription corps) {
        return inscrirePassage(mancheId, envoi, new CorpsKaraoke.InscriptionPassage(corps.envoiId(), corps.chansonId(), corps.invitesIds()));
    }

    public ResultatDeCommande inscrirePassage(UUID mancheId, EnvoiDeCommande envoi, CorpsKaraoke.InscriptionPassage corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.JOUEUR);
        return executer(mancheId, envoi, () -> {
            Optional<ContenuKaraoke> contenu = chargerContenu(corps.chansonId());
            return PreparationDeCommande.prete((d, recueLe) -> {
                if (contenu.isEmpty()) { return introuvable(); }
                long numero = passages.numeroPropose(envoi.acteur().soireeId());
                boolean invitesPresents = invitesPresents(envoi.acteur().soireeId(), corps.invites());
                ResultatDeCommande resultat = d.recevoir(new InscrirePassage(envoi.acteur(), recueLe,
                        envoi.envoiId(), new FaitsInscription(UUID.randomUUID(), numero), corps.chansonId(), contenu.get(),
                        corps.invites(), invitesPresents));
                if (resultat instanceof Accepte) { passages.incrementerNumero(envoi.acteur().soireeId(), numero); }
                return resultat;
            });
        });
    }
    public ResultatDeCommande designerLecteurChanson(UUID mancheId, EnvoiDeCommande envoi, CorpsKaraoke.DesignationLecteur corps) {
        return commander(mancheId, envoi, Acteur.Type.ECRAN, maintenant -> new DesignerLecteurChanson(
                envoi.acteur(), maintenant, envoi.envoiId(), corps.lecteurId()));
    }

    public ResultatDeCommande declarerLectureChanson(UUID mancheId, UUID passageId, EnvoiDeCommande envoi,
            CorpsKaraoke.DeclarationLectureChanson corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.ECRAN);
        var acteur = envoi.acteur();
        return executer(mancheId, envoi, () -> {
            var instantane = (InstantaneKaraoke) instantane(acteur.soireeId(), mancheId);
            ContenuKaraoke inscription = instantane.contenus().get(passageId);
            if (inscription == null) { return PreparationDeCommande.prete(d -> introuvable()); }
            SourceLecture sourceLecture = SourceLecture.valueOf(corps.source());
            var fichier = corps.fichier() == null ? null : corps.fichier().metadonnees();
            boolean vise = viseAppelDuLecteur(instantane, passageId, corps);
            var choix = choisirParoles(vise, fichier, inscription);
            return PreparationDeCommande.prete((d, recueLe) -> {
                var courant = (InstantaneKaraoke) d.instantane();
                boolean fichierPourLAppelCourant = fichier != null && viseAppelDuLecteur(courant, passageId, corps);
                if (fichierPourLAppelCourant && !vise) {
                    return new Refuse(409, "COMMANDE_PERIMEE", "La désignation du lecteur a changé pendant la préparation.");
                }
                long decalage = fichierPourLAppelCourant
                        ? calages.lire(inscription.chanson().chansonId(), choix.contenu().chanson().idLrclib(),
                                fichier.nom(), fichier.tailleOctets()) : 0;
                return d.commander(new DeclarerLectureChanson(acteur, recueLe, envoi.envoiId(),
                        new CiblePassage(passageId, corps.numeroModification()), corps.etapeId(), corps.lecteurId(),
                        preparerLecture(corps, sourceLecture, fichier, choix, decalage)));
            });
        });
    }

    private ChoixDeVersionParoles.ResultatChoix choisirParoles(boolean vise, FichierAudioLocal fichier, ContenuKaraoke inscription) {
        if (fichier == null) { return new ChoixDeVersionParoles.ResultatChoix(inscription, OrigineParoles.INSCRIPTION); }
        if (fichier.dureeMs() == null || !vise) {
            return new ChoixDeVersionParoles.ResultatChoix(inscription, OrigineParoles.REPLI_INSCRIPTION);
        }
        return choixParoles.choisir(inscription, fichier.dureeMs());
    }

    private static boolean viseAppelDuLecteur(InstantaneKaraoke instantane, UUID passageId,
            CorpsKaraoke.DeclarationLectureChanson corps) {
        return corps.lecteurId().equals(instantane.lecteurDesigneId()) && passageId.equals(instantane.cycle().passageId())
                && corps.etapeId().equals(instantane.cycle().etapeId())
                && instantane.file().passages().stream().anyMatch(p -> p.id().equals(passageId)
                        && p.etat() == EtatPassage.APPELE && p.numeroModification() == corps.numeroModification());
    }

    private static LecturePreparee preparerLecture(CorpsKaraoke.DeclarationLectureChanson corps, SourceLecture sourceLecture,
            FichierAudioLocal fichier, ChoixDeVersionParoles.ResultatChoix choix, long decalage) {
        Chanson version = choix.contenu().chanson();
        var motif = corps.motifParolesSeules() == null ? null : MotifParolesSeules.valueOf(corps.motifParolesSeules());
        return new LecturePreparee(sourceLecture, fichier, motif, choix.origine(), version.dureeMs(),
                choix.contenu().lignes(), version.idLrclib(), decalage);
    }

    public ResultatDeCommande signalerFinLectureChanson(UUID mancheId, UUID passageId, EnvoiDeCommande envoi,
            CorpsKaraoke.SignalFinLectureChanson corps) {
        return commander(mancheId, envoi, Acteur.Type.ECRAN, maintenant -> new SignalerFinLectureChanson(envoi.acteur(),
                maintenant, envoi.envoiId(), passageId, corps.etapeId(), corps.lectureId(), corps.lecteurId(), corps.positionMs()));
    }

    public ResultatDeCommande signalerErreurLectureChanson(UUID mancheId, UUID passageId, EnvoiDeCommande envoi,
            CorpsKaraoke.SignalErreurLectureChanson corps) {
        return commander(mancheId, envoi, Acteur.Type.ECRAN, recueLe -> new SignalerErreurLectureChanson(envoi.acteur(),
                recueLe, envoi.envoiId(), passageId, corps.etapeId(), corps.lectureId(), corps.lecteurId()));
    }

    public ResultatDeCommande reprendreEnParolesSeules(UUID mancheId, UUID passageId, EnvoiDeCommande envoi,
            CorpsKaraoke.RepriseParolesSeules corps) {
        return commander(mancheId, envoi, Acteur.Type.ANIMATEUR, recueLe -> new ReprendreEnParolesSeules(envoi.acteur(),
                recueLe, envoi.envoiId(), passageId, corps.etapeId(), corps.lectureId()));
    }

    public ResultatDeCommande marquerDebutParoles(UUID mancheId, UUID passageId, EnvoiDeCommande envoi,
            CorpsKaraoke.MarqueDebutParoles corps) {
        return commander(mancheId, envoi, Acteur.Type.ANIMATEUR, maintenant -> new MarquerDebutParoles(envoi.acteur(),
                maintenant, envoi.envoiId(), passageId, corps.etapeId(), corps.lectureId()));
    }

    public ResultatDeCommande releverPositionLecture(UUID mancheId, UUID passageId, EnvoiDeCommande envoi,
            CorpsKaraoke.RelevePositionLecture corps) {
        return caler(mancheId, passageId, envoi, Acteur.Type.ECRAN,
                maintenant -> new ReleverPositionLecture(envoi.acteur(), maintenant, envoi.envoiId(), passageId,
                        corps.etapeId(), corps.lectureId(), corps.lecteurId(), corps.demandeId(), corps.positionMs()));
    }

    public ResultatDeCommande corrigerCalage(UUID mancheId, UUID passageId, EnvoiDeCommande envoi,
            CorpsKaraoke.CorrectionCalage corps) {
        return caler(mancheId, passageId, envoi, Acteur.Type.ANIMATEUR,
                maintenant -> new CorrigerCalage(envoi.acteur(), maintenant, envoi.envoiId(), passageId,
                        corps.etapeId(), corps.lectureId(), corps.numeroModificationCalage(), SensCorrection.valueOf(corps.sens())));
    }

    private ResultatDeCommande caler(UUID mancheId, UUID passageId, EnvoiDeCommande envoi, Acteur.Type role,
            Function<Instant, CommandeDeManche> commande) {
        verifierEntree(mancheId, envoi, role);
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) -> {
            var lectureAvant = ((InstantaneKaraoke) d.instantane()).lectures().get(passageId);
            var resultat = d.commander(commande.apply(recueLe));
            if (resultat instanceof Accepte) {
                var instantane = (InstantaneKaraoke) d.instantane();
                var lecture = instantane.lectures().get(passageId);
                if (lecture.source() == SourceLecture.FICHIER_LOCAL && lecture.decalageMs() != lectureAvant.decalageMs()) {
                    String chansonId = instantane.contenus().get(passageId).chanson().chansonId();
                    calages.memoriser(chansonId, lecture.versionSourceId(), lecture.fichier(), lecture.decalageMs(), recueLe);
                }
            }
            return resultat;
        }));
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
        CorpsKaraoke.identifiantChanson(chansonId);
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

    private void verifierLecture(Acteur acteur, UUID soireeId, UUID mancheId) {
        AccesAuxVues.verifier(acteur, soireeId, null);
        if (acteur.type() == Acteur.Type.ECRAN) { throw new Refuse(403, "ACCES_REFUSE", "Cet accès est réservé aux Manettes et à la Console.").exception(); }
        verifierAcces(acteur, soireeId, mancheId);
        var manche = socle.depot().charger(mancheId);
        if (manche == null || !manche.getSoireeId().equals(soireeId) || manche.getMode() != mode()) { throw introuvable().exception(); }
        if (manche.getEtat() != EtatManche.EN_COURS && manche.getEtat() != EtatManche.EN_PAUSE) { throw inactive().exception(); }
        verifierActivite(soireeId, mancheId);
    }
    public List<VuesKaraoke.LignePalmaresKaraoke> palmaresKaraoke(UUID soireeId) {
        return lecture(soireeId, () -> composerPalmares(soireeId, socle.depot().deLaSoiree(soireeId)));
    }

    List<VuesKaraoke.LignePalmaresKaraoke> composerPalmares(UUID soireeId, List<ManchePersistante> manches) {
        var manchesKaraoke = manches.stream().filter(manche -> manche.getMode() == mode())
                .map(ManchePersistante::getMancheId).toList();
        var resultats = passages.resultatsDeVote(manchesKaraoke);
        if (resultats.isEmpty()) { return List.of(); }
        var contenus = persistance.contenus(resultats.stream().map(ResultatVote::passageId).toList());
        var pseudos = participants.pseudos(soireeId);
        return ClassementParVote.classer(resultats).stream()
                .map(ligne -> VuesKaraoke.lignePalmares(ligne, contenus.get(ligne.resultat().passageId()), pseudos))
                .toList();
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
}
