package fr.croustyquizz.passage;

import java.util.Objects;
import java.util.UUID;

/**
 * Invitation d’un Joueur à devenir Interprète d’un Passage, émise à l’inscription (#135). Tant qu’elle est en
 * suspens, l’invité n’est pas Interprète ; elle expire à l’appel du Passage, à son retrait et à la fin de la Manche.
 */
public record InvitationPassage(UUID passageId, UUID inviteId, Etat etat) {
    public enum Etat {
        EN_ATTENTE,
        /** L’invité est devenu Interprète. */
        ACCEPTEE,
        /** L’invité a refusé, ou a quitté le Passage après avoir accepté. */
        REFUSEE,
        /** Le Passage a quitté l’attente, ou l’invité est parti, sans réponse. */
        EXPIREE
    }

    public InvitationPassage {
        Objects.requireNonNull(passageId);
        Objects.requireNonNull(inviteId);
        Objects.requireNonNull(etat);
    }

    public boolean enSuspens() { return etat == Etat.EN_ATTENTE; }

    InvitationPassage avec(Etat nouvelEtat) { return new InvitationPassage(passageId, inviteId, nouvelEtat); }
}
