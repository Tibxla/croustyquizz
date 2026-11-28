package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.passage.*;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bloc Freestyle projeté à partir d’une capture de la Manche et du catalogue de la Soirée. */
public final class VuesFreestyle {
    private VuesFreestyle() { }

    public static Optional<Map<String, Object>> projeter(InstantaneFreestyle instantane, Destination destination,
            Map<UUID, String> pseudos, List<Instrumentale> disponibles, Instant maintenant) {
        EtatManche etatManche = instantane.etatManche();
        if (etatManche == EtatManche.PRETE || etatManche == EtatManche.TERMINEE) { return Optional.empty(); }
        if (destination.type() == Destination.Type.TOUTES_LES_MANETTES) {
            throw new IllegalArgumentException("Une destination concrète est requise.");
        }
        UUID joueur = destination.joueurId();
        if (joueur != null && !pseudos.containsKey(joueur)) {
            throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Joueur introuvable.").exception();
        }

        var projection = new Projection(instantane, pseudos, maintenant);
        return Optional.of(projection.bloc(destination, disponibles));
    }

    private static final class Projection {
        private final InstantaneFreestyle instantane;
        private final Map<UUID, String> pseudos;
        private final Instant maintenant;
        private final boolean enPause;
        private final FileAttente file;
        private final ProjectionCycle cycle;

        private Projection(InstantaneFreestyle instantane, Map<UUID, String> pseudos, Instant maintenant) {
            this.instantane = instantane;
            this.pseudos = pseudos;
            this.maintenant = maintenant;
            this.enPause = instantane.etatManche() == EtatManche.EN_PAUSE;
            this.file = FileAttente.depuis(instantane.etat().file());
            this.cycle = CycleDePassage.projeter(instantane.etat().file(), instantane.etat().cycle(), enPause, maintenant);
        }

        private Map<String, Object> bloc(Destination destination, List<Instrumentale> disponibles) {
            var etat = instantane.etat().file();
            var difficulte = instantane.reglages().difficulte();
            var ordre = file.ordreEffectif();
            var positions = file.positions();
            boolean pourConsole = destination.type() == Destination.Type.CONSOLE;
            var visibles = pourConsole ? ordre : ordre.stream().limit(3).toList();
            var bloc = new LinkedHashMap<String, Object>();
            bloc.put("mancheId", etat.mancheId());
            bloc.put("etat", instantane.etatManche());
            bloc.put("difficulte", difficulte);
            bloc.put("courant", cycle.courant() == null ? null : courant(cycle.courant()));
            bloc.put("grille", grille(pourConsole));
            bloc.put("passagesEnAttente", visibles.stream().map(p -> enAttente(p, positions)).toList());
            bloc.put("nombreEnAttente", ordre.size());
            bloc.put("limitePassagesEnAttente", etat.limitePassagesEnAttente());
            bloc.put("tourOuvert", etat.tourOuvert());
            bloc.put("passagePrioritaireId", etat.passagePrioritaireId());
            bloc.put("votes", VoteDuPublic.visibles(instantane.etat().voteDuPublic(), file).stream()
                    .map(vote -> vote(vote, destination.joueurId())).toList());
            if (pourConsole) {
                bloc.put("instrumentales", List.copyOf(disponibles));
                bloc.put("peutAppeler", !enPause && !instantane.pilotageAutomatique() && !cycle.actif() && !ordre.isEmpty());
                bloc.put("peutPasser", cycle.actif());
            } else if (destination.type() == Destination.Type.JOUEUR) {
                completerManette(bloc, destination.joueurId(), disponibles, ordre, positions);
            }
            return Collections.unmodifiableMap(bloc);
        }

        private void completerManette(Map<String, Object> bloc, UUID joueur, List<Instrumentale> disponibles,
                List<Passage> ordre, Map<UUID, Long> positions) {
            var selection = instantane.reglages().selectionDisponible(disponibles);
            var personnels = ordre.stream().filter(passage -> passage.interpretes().contains(joueur)).toList();
            bloc.put("instrumentales", selection);
            bloc.put("mesPassagesEnAttente", personnels.stream().map(p -> enAttente(p, positions)).toList());
            bloc.put("peutInscrire", selection.stream()
                    .anyMatch(fiche -> file.verifierInscription(joueur, fiche.instrumentaleId().toString()).isEmpty()));
            bloc.put("peutConfirmerPresence", !enPause && cycle.actif() && cycle.courant().etat() == EtatPassage.APPELE
                    && cycle.courant().interpretes().contains(joueur));
        }

        private GrilleDeRimes grille(boolean pourConsole) {
            if (!cycle.actif() || cycle.courant().etat() != EtatPassage.EN_COURS) { return null; }
            var grille = instantane.etat().contenus().get(cycle.courant().id()).grille();
            return pourConsole ? grille : instantane.reglages().difficulte().appliquer(grille);
        }

        private Map<String, Object> courant(Passage passage) {
            var donnees = passage(passage);
            donnees.put("etapeId", cycle.etapeId());
            donnees.put("etat", cycle.etat());
            donnees.put("delaiAppelRestantMs", cycle.delaiAppelRestantMs());
            donnees.put("demarreLe", cycle.demarreLe());
            donnees.put("finPrevueLe", cycle.finPrevueLe());
            donnees.put("positionChronologiqueMs", cycle.positionChronologiqueMs());
            donnees.put("calculeLe", cycle.calculeLe());
            return Collections.unmodifiableMap(donnees);
        }

        private Map<String, Object> enAttente(Passage passage, Map<UUID, Long> positions) {
            var donnees = passage(passage);
            donnees.put("position", positions.get(passage.id()));
            return Collections.unmodifiableMap(donnees);
        }

        private Map<String, Object> vote(EtatVotePassage vote, UUID joueur) {
            Passage passage = file.trouver(vote.passageId());
            var projection = VoteDuPublic.projeter(vote, maintenant);
            var donnees = passage(passage);
            donnees.put("etat", projection.ouvert() ? "OUVERT" : "CLOS");
            donnees.put("delaiRestantMs", projection.delaiRestantMs());
            donnees.put("calculeLe", projection.calculeLe());
            donnees.put("clotureLe", projection.clotureLe());
            if (joueur == null) {
                donnees.put("nombreVotes", projection.nombreVotes());
            } else {
                Integer maNote = vote.votes().stream().filter(recu -> joueur.equals(recu.joueurId()))
                        .map(VoteRecu::note).findFirst().orElse(null);
                donnees.put("maNote", maNote);
                donnees.put("peutVoter", !enPause && projection.ouvert() && maNote == null && !passage.interpretes().contains(joueur));
            }
            return Collections.unmodifiableMap(donnees);
        }

        private Map<String, Object> passage(Passage passage) {
            var donnees = new LinkedHashMap<String, Object>();
            donnees.put("passageId", passage.id());
            donnees.put("numeroModification", passage.numeroModification());
            donnees.put("numero", passage.numero());
            donnees.put("instrumentale", instantane.etat().contenus().get(passage.id()).instrumentale());
            UUID interprete = passage.interpretes().getFirst();
            donnees.put("interprete", Map.of("joueurId", interprete,
                    "pseudo", pseudos.getOrDefault(interprete, ParticipantsDePassage.JOUEUR_PARTI)));
            return donnees;
        }
    }
}
