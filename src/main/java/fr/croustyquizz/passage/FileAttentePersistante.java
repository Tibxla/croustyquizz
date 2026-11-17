package fr.croustyquizz.passage;

import jakarta.persistence.*;
import java.util.*;

/** En-tête durable de la File, sans historique dupliqué. */
@Entity
@Table(name = "passage_file_attente")
public class FileAttentePersistante {
    @Id private UUID mancheId;
    private UUID soireeId;
    private long limitePassagesEnAttente;
    private long tourOuvert;
    private long prochainOrdreInscription;
    private long prochaineCleOrdre;
    private UUID passagePrioritaireId;
    protected FileAttentePersistante() { }
    public FileAttentePersistante(EtatFileAttente etat) {
        mancheId = etat.mancheId(); soireeId = etat.soireeId(); limitePassagesEnAttente = etat.limitePassagesEnAttente();
        tourOuvert = etat.tourOuvert(); prochainOrdreInscription = etat.prochainOrdreInscription();
        prochaineCleOrdre = etat.prochaineCleOrdre(); passagePrioritaireId = etat.passagePrioritaireId();
    }
    public EtatFileAttente restaurer(List<Passage> passages, Map<UUID, Long> consommations) {
        return new EtatFileAttente(soireeId, mancheId, limitePassagesEnAttente, tourOuvert,
                prochainOrdreInscription, prochaineCleOrdre, passagePrioritaireId, passages, consommations);
    }
}
