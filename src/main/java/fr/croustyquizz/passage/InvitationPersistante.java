package fr.croustyquizz.passage;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

/** Dernier état durable d’une invitation, dans l’ordre d’émission de la File. */
@Entity
@Table(name = "passage_invitation")
@IdClass(InvitationPersistante.Cle.class)
public class InvitationPersistante {
    @Id private UUID passageId;
    @Id private UUID inviteId;
    private UUID mancheId;
    @Enumerated(EnumType.STRING) private InvitationPassage.Etat etat;
    private int ordre;
    protected InvitationPersistante() { }
    public InvitationPersistante(UUID mancheId, InvitationPassage invitation, int ordre) {
        this.passageId = invitation.passageId();
        this.inviteId = invitation.inviteId();
        this.mancheId = mancheId;
        this.etat = invitation.etat();
        this.ordre = ordre;
    }
    public Cle cle() { return new Cle(passageId, inviteId); }
    public InvitationPassage restaurer() { return new InvitationPassage(passageId, inviteId, etat); }
    public record Cle(UUID passageId, UUID inviteId) implements Serializable { }
}
