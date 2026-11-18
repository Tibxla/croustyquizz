package fr.croustyquizz.passage;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Présence, portée et activité pilotables sans accès extérieur. */
public final class PortsDePassageFactices implements PorteeDePassage, ParticipantsDePassage {
    public final Set<UUID> absents = ConcurrentHashMap.newKeySet();
    public final Set<UUID> inactives = ConcurrentHashMap.newKeySet();
    public final Set<UUID> interdites = ConcurrentHashMap.newKeySet();
    @Override public void verifier(Acteur acteur, UUID soireeId, UUID mancheId) {
        AccesAuxVues.verifier(acteur, soireeId, null);
        if (interdites.contains(mancheId) || (acteur.type() == Acteur.Type.JOUEUR && !estPresent(soireeId, acteur.identifiant()))) {
            throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Portée ou Joueur introuvable.").exception();
        }
    }
    @Override public boolean estActive(UUID soireeId, UUID mancheId) { return !inactives.contains(mancheId); }
    @Override public boolean estPresent(UUID soireeId, UUID joueurId) { return !absents.contains(joueurId); }
    @Override public String pseudo(UUID soireeId, UUID joueurId) { return estPresent(soireeId, joueurId) ? "Joueur " + joueurId : "Joueur parti"; }
}
