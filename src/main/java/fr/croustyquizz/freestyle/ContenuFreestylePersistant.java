package fr.croustyquizz.freestyle;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fiche et Grille figées à l’inscription, indépendantes du catalogue local. */
@Entity
@Table(name = "freestyle_contenu_passage")
class ContenuFreestylePersistant {
    @Id private UUID passageId;
    @JdbcTypeCode(SqlTypes.JSON) private String contenu;

    protected ContenuFreestylePersistant() { }

    ContenuFreestylePersistant(UUID passageId, String contenu) {
        this.passageId = passageId;
        this.contenu = contenu;
    }

    UUID getPassageId() { return passageId; }
    String getContenu() { return contenu; }
}
