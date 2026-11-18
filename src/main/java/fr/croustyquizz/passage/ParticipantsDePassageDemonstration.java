package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.tempsreel.FournisseurPartieCommune;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Pseudos de démonstration, y compris après le départ d'un Interprète. */
@Component
@Profile({"dev", "recette"})
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
