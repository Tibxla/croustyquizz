package fr.croustyquizz.passage;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Présence, portée et activité pilotables sans accès extérieur. */
public final class PortsDePassageFactices implements PorteeDePassage, ParticipantsDePassage {
    private final List<UUID> joueurs;
    public final Set<UUID> absents = ConcurrentHashMap.newKeySet();
    public final Set<UUID> inactives = ConcurrentHashMap.newKeySet();
    public final Set<UUID> interdites = ConcurrentHashMap.newKeySet();
    public PortsDePassageFactices(List<UUID> joueurs) { this.joueurs = List.copyOf(joueurs); }
    @Override public void verifier(Acteur acteur, UUID soireeId, UUID mancheId) {
        AccesAuxVues.verifier(acteur, soireeId, null);
        if (interdites.contains(mancheId) || (acteur.type() == Acteur.Type.JOUEUR && !estPresent(soireeId, acteur.identifiant()))) {
            throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Portée ou Joueur introuvable.").exception();
        }
    }
    @Override public boolean estActive(UUID soireeId, UUID mancheId) { return !inactives.contains(mancheId); }
    @Override public boolean estPresent(UUID soireeId, UUID joueurId) { return joueurs.contains(joueurId) && !absents.contains(joueurId); }
    @Override public Map<UUID, String> pseudos(UUID soireeId) {
        return joueurs.stream().filter(id -> !absents.contains(id))
                .collect(Collectors.toUnmodifiableMap(id -> id, id -> "Joueur " + id));
    }
}
