package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.ResultatDeCommande;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reçus et publications communs aux commandes de la File des Modes de Passage. */
public final class CommandesDeFileAttente {
    private static final String FILE_MODIFIEE = "file-attente-modifiee";
    private static final Set<Destination> ECRANS = Set.of(
            Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);

    private final FileAttente file;
    private final UUID soireeId;
    private final UUID mancheId;

    public CommandesDeFileAttente(FileAttente file, UUID soireeId, UUID mancheId) {
        this.file = file;
        this.soireeId = soireeId;
        this.mancheId = mancheId;
    }

    public ResultatDeCommande inscrire(NouveauPassage nouveau, UUID envoiId) {
        return inscrire(nouveau, List.of(), true, envoiId);
    }

    /**
     * Inscrit le Passage avec ses Invitations (#66). Les gardes des invités viennent après celles du contenu et de
     * l’inscrivant, vérifiées par le Mode ; {@code invitesPresents} vient du service, sous le verrou de Soirée.
     */
    public ResultatDeCommande inscrire(NouveauPassage nouveau, List<UUID> invitesIds, boolean invitesPresents, UUID envoiId) {
        if (invitesIds.contains(nouveau.inscripteurId())) {
            return new Refuse(422, "DONNEES_INVALIDES", "Tu ne peux pas t’inviter toi-même.");
        }
        if (!invitesPresents) { return new Refuse(404, "RESSOURCE_INTROUVABLE", "Un Joueur invité est introuvable."); }
        ResultatFile resultat = file.inscrire(nouveau, invitesIds);
        if (resultat instanceof ResultatFile.Refus refus) { return refus.refus(); }
        return new Accepte(accusePassage(envoiId, nouveau.recueLe(), nouveau.id(), "INSCRIRE"),
                ECRANS, FILE_MODIFIEE, 201);
    }

    public ResultatDeCommande desinscrire(DesinscrirePassage commande) {
        return resultatPassage(file.desinscrire(commande.joueurId(), commande.cible(), commande.recueLe()),
                commande.envoiId(), commande.recueLe(), commande.cible(), "DESINSCRIRE");
    }

    public ResultatDeCommande repondreInvitation(RepondreInvitation reponse) {
        return resultatPassage(file.repondreInvitation(reponse.joueurId(), reponse.cible(), reponse.accepte()),
                reponse.envoiId(), reponse.recueLe(), reponse.cible(), reponse.action());
    }

    /** Départ signalé par le serveur, sans reçu. Un refus évite de réécrire une File inchangée. */
    public ResultatDeCommande retirerJoueurParti(UUID joueurId, Instant recueLe) {
        ResultatFile resultat = file.retirerJoueurParti(joueurId, recueLe);
        if (resultat instanceof ResultatFile.SansEffet) {
            return new Refuse(409, "ETAT_INCOMPATIBLE", "Ce Joueur n’a aucun Passage en attente ni Invitation en suspens.");
        }
        return new Accepte(null, ECRANS, FILE_MODIFIEE, 200);
    }

    public ResultatDeCommande retirer(RetirerPassage commande) {
        return resultatPassage(file.retirer(commande.acteur().identifiant(), commande.cible(), commande.recueLe()),
                commande.envoiId(), commande.recueLe(), commande.cible(), "RETIRER");
    }

    public ResultatDeCommande deplacer(DeplacerPassage commande) {
        return resultatPassage(file.deplacer(commande.cible(), commande.tour(), commande.avant()),
                commande.envoiId(), commande.recueLe(), commande.cible(), "DEPLACER");
    }

    public ResultatDeCommande definirPriorite(DefinirPrioritePassage commande) {
        ResultatFile resultat = file.definirPriorite(commande.passage(), commande.prioritaire());
        if (resultat instanceof ResultatFile.Refus refus) { return refus.refus(); }
        Passage passage = file.trouver(commande.passage().passageId());
        var cible = new CiblePassage(passage.id(), passage.numeroModification());
        return accepter(resultat, new AccuseFileAttente(commande.envoiId(), soireeId, mancheId,
                "DEFINIR_PRIORITE", commande.recueLe(), cible), FILE_MODIFIEE);
    }

    public ResultatDeCommande reglerLimite(ReglerLimitePassages commande) {
        ResultatFile resultat = file.reglerLimite(commande.limitePassagesEnAttente());
        return accepter(resultat, new AccuseFileAttente(commande.envoiId(), soireeId, mancheId,
                "REGLER_LIMITE", commande.recueLe(), null), "limite-passages-modifiee");
    }

    private ResultatDeCommande resultatPassage(ResultatFile resultat, UUID envoiId, Instant recueLe,
            CiblePassage cible, String action) {
        if (resultat instanceof ResultatFile.Refus refus) { return refus.refus(); }
        return accepter(resultat, accusePassage(envoiId, recueLe, cible.passageId(), action), FILE_MODIFIEE);
    }

    private AccusePassage accusePassage(UUID envoiId, Instant recueLe, UUID passageId, String action) {
        Passage passage = file.trouver(passageId);
        return new AccusePassage(envoiId, soireeId, mancheId, passage.id(), passage.numeroModification(), action, recueLe, passage.etat());
    }

    private static Accepte accepter(ResultatFile resultat, Object accuse, String typeEvenement) {
        return new Accepte(accuse, resultat instanceof ResultatFile.Modifiee ? ECRANS : Set.of(), typeEvenement);
    }
}
