package fr.croustyquizz.freestyle;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Une déclaration complète par Soirée, y compris pour les Soirées de démonstration, avec la fenêtre qui l’a envoyée. */
@Entity
@Table(name = "freestyle_catalogue")
class CatalogueFreestylePersistant {
    @Id private UUID soireeId;
    @JdbcTypeCode(SqlTypes.JSON) private String fiches;
    private UUID lecteurId;

    protected CatalogueFreestylePersistant() { }

    CatalogueFreestylePersistant(UUID soireeId, String fiches, UUID lecteurId) {
        this.soireeId = soireeId;
        this.fiches = fiches;
        this.lecteurId = lecteurId;
    }

    String getFiches() { return fiches; }
    UUID getLecteurId() { return lecteurId; }
}
