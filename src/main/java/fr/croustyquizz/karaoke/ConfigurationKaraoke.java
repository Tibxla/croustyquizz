package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

/** Assemblage du mode, distinct de la configuration MVC pour les contextes partiels. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProprietesKaraoke.class)
public class ConfigurationKaraoke {
    @Bean FabriqueKaraoke fabriqueKaraoke(ProprietesKaraoke proprietes) {
        return new FabriqueKaraoke(Duration.ofSeconds(proprietes.passage().delaiAppelSecondes()),
                Duration.ofSeconds(proprietes.passage().dureeVoteSecondes()));
    }
    @Bean ServiceKaraoke serviceKaraoke(FabriqueKaraoke fabrique, PersistanceKaraokeJpa persistance, SocleDeManche socle,
            PorteeDePassage portee, ParticipantsDePassage participants, PersistancePassageJpa passages,
            SourceDeParoles source, CatalogueDeBase catalogue, ProprietesKaraoke proprietes,
            ProprietesSourceKaraoke proprietesSource, ChoixDeVersionParoles choixParoles, MemoireDeCalageJpa calages) {
        // Le catalogue passe avant LRCLIB ; le choix de version, propre à LRCLIB, garde la source externe seule.
        return new ServiceKaraoke(fabrique, persistance, socle, portee, participants, passages,
                new SourcesDeChansons(catalogue, source), proprietes, proprietesSource, choixParoles, calages);
    }
    @Bean ChoixDeVersionParoles choixDeVersionParoles(SourceDeParoles source, ProprietesSourceKaraoke proprietes) {
        return new ChoixDeVersionParoles(source, proprietes);
    }
    @Bean FournisseurDeBlocKaraoke fournisseurDeBlocKaraoke(DepotDeManches depot, ObjectProvider<ServiceKaraoke> service,
            ParticipantsDePassage participants, ProprietesKaraoke proprietes, ProprietesSourceKaraoke proprietesSource,
            CatalogueDeBase catalogue) {
        return new FournisseurDeBlocKaraoke(depot, service, participants, proprietes, proprietesSource, catalogue);
    }
}
