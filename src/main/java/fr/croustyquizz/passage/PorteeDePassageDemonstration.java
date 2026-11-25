package fr.croustyquizz.passage;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.tempsreel.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Portée des Soirées de démonstration, absentes de la base. {@code @Fallback} : la portée
 * de la Soirée enregistrée ({@code soiree}, #111) l'emporte et lui confie ces Soirées.
 */
@Component
@Profile({"dev", "recette"})
@Fallback
public final class PorteeDePassageDemonstration implements PorteeDePassage {
    private final DepotDeManches depot;
    private final FournisseurPartieCommune commun;
    public PorteeDePassageDemonstration(DepotDeManches depot, FournisseurPartieCommune commun) {
        this.depot = depot; this.commun = commun;
    }
    @Override public void verifier(Acteur acteur, UUID soireeId, UUID mancheId) {
        AccesAuxVues.verifier(acteur, soireeId, null);
        ManchePersistante manche = depot.charger(mancheId);
        if (manche == null || !soireeId.equals(manche.getSoireeId()) || manche.getMode() != ModeDeJeu.KARAOKE) {
            throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Manche ou Joueur introuvable.").exception();
        }
    }
    @Override public boolean estActive(UUID soireeId, UUID mancheId) {
        Object soiree = commun.fournir(soireeId, Destination.SALLE).get("soiree");
        return soiree instanceof Map<?, ?> valeurs && "EN_COURS".equals(String.valueOf(valeurs.get("etat")))
                && depot.deLaSoiree(soireeId).stream().anyMatch(m -> m.getMancheId().equals(mancheId)
                    && (m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE));
    }
}
