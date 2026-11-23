package fr.croustyquizz.karaoke;

import static java.time.temporal.ChronoUnit.MICROS;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/** Mémoire partagée entre toutes les Soirées, dans la transaction de la commande. */
@Repository
public class MemoireDeCalageJpa {
    private final EntityManager entites;

    public MemoireDeCalageJpa(EntityManager entites) { this.entites = entites; }

    public long lire(String chansonId, long versionSourceId, String nom, long tailleOctets) {
        var cle = new CalageChansonPersistant.Cle(chansonId, versionSourceId, nom, tailleOctets);
        var calage = entites.find(CalageChansonPersistant.class, cle);
        return calage == null ? 0 : calage.getDecalageMs();
    }

    public void memoriser(String chansonId, long versionSourceId, FichierAudioLocal fichier,
            long decalageMs, Instant maintenant) {
        entites.createNativeQuery("""
                INSERT INTO karaoke_calage_chanson
                    (chanson_id, version_source_id, fichier_nom, fichier_taille_octets, decalage_ms,
                     numero_modification, modifie_le)
                VALUES (:chanson, :version, :nom, :taille, :decalage, 1, :instant)
                ON CONFLICT (chanson_id, version_source_id, fichier_nom, fichier_taille_octets)
                DO UPDATE SET decalage_ms = EXCLUDED.decalage_ms,
                    numero_modification = karaoke_calage_chanson.numero_modification + 1,
                    modifie_le = EXCLUDED.modifie_le
                WHERE karaoke_calage_chanson.decalage_ms IS DISTINCT FROM EXCLUDED.decalage_ms
                """)
                .setParameter("chanson", chansonId).setParameter("version", versionSourceId)
                .setParameter("nom", fichier.nom()).setParameter("taille", fichier.tailleOctets())
                .setParameter("decalage", decalageMs).setParameter("instant", maintenant.truncatedTo(MICROS)).executeUpdate();
    }
}
