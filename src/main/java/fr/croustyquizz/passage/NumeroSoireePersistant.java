package fr.croustyquizz.passage;

import jakarta.persistence.*;
import java.util.UUID;

/** Séquence partagée entre les Manches d'une Soirée. */
@Entity
@Table(name = "passage_numero_soiree")
public class NumeroSoireePersistant {
    @Id private UUID soireeId;
    private long prochainNumero;
    protected NumeroSoireePersistant() { }
    public NumeroSoireePersistant(UUID soireeId, long prochainNumero) { this.soireeId = soireeId; this.prochainNumero = prochainNumero; }
    public long getProchainNumero() { return prochainNumero; }
    public void avancer() { prochainNumero = Math.incrementExact(prochainNumero); }
}
