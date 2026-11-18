package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import fr.croustyquizz.tempsreel.*;
import java.util.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Trois Manettes et source locale : aucune requête Internet. */
@TestConfiguration(proxyBeanMethods = false)
public class ConfigurationTroisJoueurs {
    public static final List<UUID> JOUEURS = List.of(UUID.fromString("10000000-0000-4000-8000-000000000001"),
            UUID.fromString("10000000-0000-4000-8000-000000000002"), UUID.fromString("10000000-0000-4000-8000-000000000003"));
    @Bean @Primary PortsDePassageFactices portsFactices() { return new PortsDePassageFactices(); }
    @Bean @Primary Horloge horlogeKaraoke() { return new HorlogeFactice(); }
    @Bean @Primary SourceDeParoles sourceKaraoke() {
        return new SourceDeParoles() {
            public List<Chanson> rechercher(String recherche) { return List.of(ServiceKaraokeTest.contenu(1).chanson()); }
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
        public List<UUID> joueurs(UUID id) { return JOUEURS; }
        public Map<String, Object> fournir(UUID id, Destination destination) {
            var vue = new LinkedHashMap<>(new FournisseurCommunDemonstration(depot, tools.jackson.databind.json.JsonMapper.builder().build()).fournir(id, destination));
            vue.put("joueurs", JOUEURS.stream().map(j -> Map.of("id", j, "pseudo", "Joueur " + j, "connecte", true)).toList());
            if (destination.type() == Destination.Type.JOUEUR) { vue.put("eligiblePourMancheActive", false); }
            return vue;
        }
    }
}
