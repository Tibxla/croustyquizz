package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.*;
import java.util.*;

/** Projections publiques, sans paroles ni identifiant de fournisseur. */
public final class VuesKaraoke {
    private VuesKaraoke() { }
    public record ResumeChanson(String chansonId, String titre, String artiste, String album, Long dureeMs, EtatParoles etatParoles) {
        public static ResumeChanson de(Chanson chanson) {
            return new ResumeChanson(chanson.chansonId(), chanson.titre(), chanson.artiste(), chanson.album(),
                    chanson.dureeMs() != null && chanson.dureeMs() > 0 ? chanson.dureeMs() : null, chanson.etatParoles());
        }
    }
    public record ResultatsChansons(UUID rechercheId, String recherche, List<ResumeChanson> chansons) {
        public ResultatsChansons { chansons = List.copyOf(chansons); }
    }
    public record FicheChanson(ResumeChanson chanson, boolean inscriptionPossible, MotifIndisponibilite motifIndisponibilite) { }
    public record InterpretePublic(UUID joueurId, String pseudo) { }
    public record ConfigurationInterfaceKaraoke(int longueurRechercheMax, long delaiConsultationNavigateurMs, long delaiConfirmationNavigateurMs) { }
    public record PassageEnAttentePublic(UUID passageId, long numeroModification, long numero, ResumeChanson chanson,
            List<InterpretePublic> interpretes, long position, long tour, boolean prioritaire, AttenteEstimee attente) { }
    public record CourantPourFile(UUID passageId, long numeroModification, long numero, ResumeChanson chanson,
            List<InterpretePublic> interpretes, UUID etapeId, String etat, Instant prochainAppelPrevuLe) { }
    public record BlocKaraokeSalle(UUID mancheId, EtatManche etat, long limitePassagesEnAttente, long nombreEnAttente, long tourOuvert,
            UUID passagePrioritaireId, CourantPourFile courant, ConfigurationInterfaceKaraoke configurationInterface,
            List<PassageEnAttentePublic> prochainsPassages, long nombreAutresPassages) { }
    public record BlocKaraokeConsole(UUID mancheId, EtatManche etat, long limitePassagesEnAttente, long nombreEnAttente, long tourOuvert,
            UUID passagePrioritaireId, CourantPourFile courant, ConfigurationInterfaceKaraoke configurationInterface,
            List<PassageEnAttentePublic> passagesEnAttente, boolean peutGererFile, boolean peutReglerLimite, boolean peutRechercher) { }
    public record BlocKaraokeManette(UUID mancheId, EtatManche etat, long limitePassagesEnAttente, long nombreEnAttente, long tourOuvert,
            UUID passagePrioritaireId, CourantPourFile courant, ConfigurationInterfaceKaraoke configurationInterface,
            List<PassageEnAttentePublic> prochainsPassages, long nombreAutresPassages,
            List<PassageEnAttentePublic> mesPassagesEnAttente, long nombrePersonnelEnAttente,
            boolean peutRechercher, boolean peutInscrire, String motifInscriptionRefusee) { }

    public static Object projeter(InstantaneKaraoke instantane, Destination destination, ParticipantsDePassage participants,
            Instant maintenant, ProprietesKaraoke proprietes, ProprietesSourceKaraoke source) {
        var etat = instantane.file();
        if (destination.type() == Destination.Type.JOUEUR && !participants.estPresent(etat.soireeId(), destination.joueurId())) {
            throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Joueur introuvable.").exception();
        }
        var file = FileAttente.depuis(etat);
        var ordre = file.ordreEffectif();
        var positions = file.positions();
        var attentes = file.estimerAttentes(maintenant, instantane.etatManche() == EtatManche.EN_PAUSE, null, false,
                Duration.ofSeconds(proprietes.passage().delaiAppelSecondes()));
        List<PassageEnAttentePublic> publics = ordre.stream().map(p -> new PassageEnAttentePublic(p.id(), p.numeroModification(),
                p.numero(), ResumeChanson.de(instantane.contenus().get(p.id()).chanson()),
                p.interpretes().stream().map(id -> new InterpretePublic(id, participants.pseudo(etat.soireeId(), id))).toList(),
                positions.get(p.id()), p.tourNormal(), p.id().equals(etat.passagePrioritaireId()), attentes.get(p.id()))).toList();
        var configuration = new ConfigurationInterfaceKaraoke(source.longueurRechercheMax(),
                proprietes.karaoke().delaiConsultationNavigateurMs(), proprietes.karaoke().delaiConfirmationNavigateurMs());
        var trois = publics.stream().limit(3).toList();
        return switch (destination.type()) {
            case SALLE -> new BlocKaraokeSalle(etat.mancheId(), instantane.etatManche(), etat.limitePassagesEnAttente(), publics.size(),
                    etat.tourOuvert(), etat.passagePrioritaireId(), null, configuration, trois, publics.size() - trois.size());
            case CONSOLE -> new BlocKaraokeConsole(etat.mancheId(), instantane.etatManche(), etat.limitePassagesEnAttente(), publics.size(),
                    etat.tourOuvert(), etat.passagePrioritaireId(), null, configuration, publics, true, true, true);
            case JOUEUR -> {
                var personnels = publics.stream().filter(p -> p.interpretes().stream().anyMatch(i -> i.joueurId().equals(destination.joueurId()))).toList();
                boolean peutInscrire = personnels.size() < etat.limitePassagesEnAttente();
                yield new BlocKaraokeManette(etat.mancheId(), instantane.etatManche(), etat.limitePassagesEnAttente(), publics.size(),
                        etat.tourOuvert(), etat.passagePrioritaireId(), null, configuration, trois, publics.size() - trois.size(),
                        personnels, personnels.size(), true, peutInscrire, peutInscrire ? null : "LIMITE_ATTEINTE");
            }
            default -> throw new IllegalArgumentException("Une destination concrète est requise.");
        };
    }
}
