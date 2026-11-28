package fr.croustyquizz.karaoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Catalogue de la Bibliothèque de base d'abord, LRCLIB ensuite (ADR 0011). Le préfixe du `chansonId`
 * désigne la source avant toute consultation : une Chanson du catalogue ne consulte jamais LRCLIB.
 */
public final class SourcesDeChansons {
    private final CatalogueDeBase catalogueDeBase;
    private final SourceDeParoles bibliotheque;
    private final SourceDeParoles lrclib;

    public SourcesDeChansons(CatalogueDeBase catalogue, SourceDeParoles lrclib) {
        this.catalogueDeBase = catalogue;
        this.bibliotheque = new SourceDeParolesDuCatalogue(catalogue);
        this.lrclib = lrclib;
    }

    public CatalogueDeBase catalogue() { return catalogueDeBase; }

    /** LRCLIB en panne : les Chansons du catalogue suffisent, sauf s'il n'en a aucune. */
    public List<Chanson> rechercher(String rechercheNettoyee) {
        List<Chanson> chansons = new ArrayList<>(bibliotheque.rechercher(rechercheNettoyee));
        try {
            chansons.addAll(lrclib.rechercher(rechercheNettoyee));
        } catch (SourceParolesIndisponible panne) {
            if (chansons.isEmpty()) { throw panne; }
        }
        return List.copyOf(chansons);
    }

    public Optional<ContenuKaraoke> charger(String chansonId) {
        var numero = IdentifiantsChanson.decoderBibliotheque(chansonId);
        if (numero.isPresent()) { return bibliotheque.charger(numero.getAsLong()); }
        var id = IdentifiantsChanson.decoder(chansonId);
        return id.isPresent() ? lrclib.charger(id.getAsLong()) : Optional.empty();
    }
}
