package fr.croustyquizz.freestyle;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Une déclaration complète par Soirée, y compris pour les Soirées de démonstration. */
@Entity
@Table(name = "freestyle_catalogue")
class CatalogueFreestylePersistant {
    @Id private UUID soireeId;
    @JdbcTypeCode(SqlTypes.JSON) private String fiches;

    protected CatalogueFreestylePersistant() { }

    CatalogueFreestylePersistant(UUID soireeId, String fiches) {
        this.soireeId = soireeId;
        this.fiches = fiches;
    }

    String getFiches() { return fiches; }
}
