package fr.croustyquizz.karaoke;

import java.util.Comparator;
import java.util.stream.Stream;

/** Fige la version synchronisée dont la durée se rapproche le plus du fichier. */
public final class ChoixDeVersionParoles {
    private final SourceDeParoles source;
    private final ProprietesSourceKaraoke proprietes;

    public ChoixDeVersionParoles(SourceDeParoles source, ProprietesSourceKaraoke proprietes) {
        this.source = source;
        this.proprietes = proprietes;
    }

    public record ResultatChoix(ContenuKaraoke contenu, OrigineParoles origine) { }

    public ResultatChoix choisir(ContenuKaraoke inscription, long dureeFichierMs) {
        var repli = new ResultatChoix(inscription, OrigineParoles.REPLI_INSCRIPTION);
        if (dureeFichierMs <= 0) { return repli; }

        Chanson initiale = inscription.chanson();
        try {
            String requete = RechercheKaraoke.nettoyer(initiale.titre() + " " + initiale.artiste(),
                    proprietes.longueurRechercheMax());
            String titre = RechercheKaraoke.normaliser(initiale.titre());
            String artiste = RechercheKaraoke.normaliser(initiale.artiste());
            var candidates = source.rechercher(requete).stream()
                    .filter(c -> c.idLrclib() != initiale.idLrclib() && synchronisee(c)
                            && titre.equals(RechercheKaraoke.normaliser(c.titre()))
                            && artiste.equals(RechercheKaraoke.normaliser(c.artiste())));
            Comparator<Chanson> classement = Comparator
                    .comparingLong((Chanson c) -> Math.abs(c.dureeMs() - dureeFichierMs))
                    .thenComparing(c -> c.idLrclib() != initiale.idLrclib())
                    .thenComparingLong(Chanson::idLrclib);
            Chanson retenue = Stream.concat(Stream.of(initiale), candidates).min(classement).orElseThrow();
            if (retenue.idLrclib() == initiale.idLrclib()) {
                return new ResultatChoix(inscription, OrigineParoles.VERSION_ADAPTEE);
            }
            return source.charger(retenue.idLrclib())
                    .filter(c -> c.chanson().idLrclib() == retenue.idLrclib() && synchronisee(c.chanson())
                            && c.lignes().stream().anyMatch(ParolesDuPassage::contientDuTexte))
                    .map(c -> new ResultatChoix(c, OrigineParoles.VERSION_ADAPTEE))
                    .orElse(repli);
        } catch (IllegalArgumentException | SourceParolesIndisponible erreur) {
            return repli;
        }
    }

    private static boolean synchronisee(Chanson chanson) {
        return chanson.etatParoles() == EtatParoles.SYNCHRONISEES && chanson.dureeMs() != null && chanson.dureeMs() > 0;
    }
}
