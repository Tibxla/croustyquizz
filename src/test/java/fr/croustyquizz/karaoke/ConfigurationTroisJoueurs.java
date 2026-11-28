package fr.croustyquizz.karaoke;

import static fr.croustyquizz.tempsreel.FournisseurCommunDemonstration.joueurId;
import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import fr.croustyquizz.tempsreel.*;
import java.util.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Trois Manettes et source locale : aucune requête Internet. */
@TestConfiguration(proxyBeanMethods = false)
public class ConfigurationTroisJoueurs {
    static List<UUID> joueursDe(UUID soireeId) {
        return List.of(joueurId(soireeId, 1), joueurId(soireeId, 2), joueurId(soireeId, 3));
    }
    @Bean @Primary PortsDePassageFactices portsFactices() {
        return new PortsDePassageFactices(ConfigurationTroisJoueurs::joueursDe);
    }
    @Bean @Primary Horloge horlogeKaraoke() { return new HorlogeFactice(); }
    @Bean @Primary SourceDeParoles sourceKaraoke() {
        return new SourceDeParoles() {
            public List<Chanson> rechercher(String recherche) {
                if ("Chanson".equals(recherche)) {
                    return List.of(ServiceKaraokeTest.contenu(1).chanson(), ServiceKaraokeTest.contenu(2).chanson());
                }
                return List.of(ServiceKaraokeTest.contenu(1).chanson());
            }
            public Optional<ContenuKaraoke> charger(long id) {
                if (id == 503) { throw new SourceParolesIndisponible("Panne factice"); }
                if (id == 404) { return Optional.empty(); }
                if (id == 422) {
                    var chanson = ServiceKaraokeTest.contenu(id).chanson();
                    return Optional.of(new ContenuKaraoke(chanson, List.of()));
                }
                return Optional.of(ServiceKaraokeTest.contenu(id));
            }
        };
    }
    @Bean @Primary FournisseurPartieCommune communKaraoke(DepotDeManches depot) { return new Commun(depot); }
    static class Commun implements FournisseurPartieCommune {
        private final DepotDeManches depot;
        Commun(DepotDeManches depot) { this.depot = depot; }
        public List<UUID> joueurs(UUID id) { return joueursDe(id); }
        public Map<String, Object> fournir(UUID id, Destination destination) {
            var vue = new LinkedHashMap<>(new FournisseurCommunDemonstration(depot, tools.jackson.databind.json.JsonMapper.builder().build()).fournir(id, destination));
            vue.put("joueurs", joueurs(id).stream().map(j -> Map.of("id", j, "pseudo", "Joueur " + j, "connecte", true)).toList());
            if (destination.type() == Destination.Type.JOUEUR) { vue.put("eligiblePourMancheActive", false); }
            return vue;
        }
    }
}
