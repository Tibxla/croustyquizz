package fr.croustyquizz.karaoke;

import java.util.List;
import java.util.Optional;

/** Chansons de la Bibliothèque de base, en mémoire depuis le démarrage : jamais en panne. */
public final class SourceDeParolesDuCatalogue implements SourceDeParoles {
    private final CatalogueDeBase catalogue;

    public SourceDeParolesDuCatalogue(CatalogueDeBase catalogue) { this.catalogue = catalogue; }

    @Override
    public List<Chanson> rechercher(String recherche) {
        return catalogue.chansons().stream().map(ContenuKaraoke::chanson)
                .filter(chanson -> RechercheKaraoke.correspond(recherche, chanson)).toList();
    }

    /** Le numéro du catalogue tient lieu d'identifiant de version, dans son propre espace. */
    @Override
    public Optional<ContenuKaraoke> charger(long numero) { return catalogue.contenu(numero); }
}
