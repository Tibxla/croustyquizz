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
    @Override public String pseudo(UUID soireeId, UUID joueurId) {
        Object joueurs = commun.fournir(soireeId, Destination.SALLE).get("joueurs");
        if (joueurs instanceof List<?> liste) {
            for (Object valeur : liste) {
                if (valeur instanceof Map<?, ?> joueur && joueurId.toString().equals(String.valueOf(joueur.get("id")))) {
                    Object pseudo = joueur.get("pseudo");
                    if (pseudo instanceof String texte && !texte.isBlank()) { return texte; }
                }
            }
        }
        return "Joueur parti";
    }
}
