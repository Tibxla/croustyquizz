package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.tempsreel.FournisseurPartieCommune;
import java.util.*;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Pseudos des Soirées de démonstration, absentes de la base. {@code @Fallback} : les
 * participants de la Soirée enregistrée ({@code soiree}, #111) l'emportent et lui confient ces Soirées.
 */
@Component
@Profile({"dev", "recette"})
@Fallback
public final class ParticipantsDePassageDemonstration implements ParticipantsDePassage {
    private final FournisseurPartieCommune commun;
    public ParticipantsDePassageDemonstration(FournisseurPartieCommune commun) { this.commun = commun; }
    @Override public boolean estPresent(UUID soireeId, UUID joueurId) { return commun.joueurs(soireeId).contains(joueurId); }
    @Override public Map<UUID, String> pseudos(UUID soireeId) {
        Object joueurs = commun.fournir(soireeId, Destination.SALLE).get("joueurs");
        Map<UUID, String> pseudos = new HashMap<>();
        if (joueurs instanceof List<?> liste) {
            for (Object valeur : liste) {
                if (valeur instanceof Map<?, ?> joueur && joueur.get("id") instanceof UUID id) {
                    Object pseudo = joueur.get("pseudo");
                    pseudos.put(id, pseudo instanceof String texte && !texte.isBlank() ? texte : JOUEUR_PARTI);
                }
            }
        }
        return Map.copyOf(pseudos);
    }
}
