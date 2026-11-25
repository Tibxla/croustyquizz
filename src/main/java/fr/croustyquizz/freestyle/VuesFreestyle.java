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

    /** Fiches déclarées pour la Soirée et fenêtre d’Écran de salle désignée pour les jouer. */
    public record Catalogue(List<Instrumentale> disponibles, UUID lecteurDesigneId) {
        public Catalogue { disponibles = List.copyOf(disponibles); }
    }

    public static Optional<Map<String, Object>> projeter(InstantaneFreestyle instantane, Destination destination,
            Map<UUID, String> pseudos, Catalogue catalogue, Instant maintenant) {
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
        return Optional.of(projection.bloc(destination, catalogue));
    }

    static LignePalmares lignePalmares(RangPassage ligne, Map<UUID, EtatFreestyle.Contenu> contenus, Map<UUID, String> pseudos) {
        var resultat = ligne.resultat();
        return new LignePalmares(resultat.passageId(), resultat.numero(), ligne.rang(),
                contenus.get(resultat.passageId()).instrumentale(), interprete(resultat.interpretes().getFirst(), pseudos),
                resultat.nombreVotes(), resultat.moyenneDixiemes());
    }

    static Interprete interprete(UUID joueur, Map<UUID, String> pseudos) {
        return new Interprete(joueur, pseudos.getOrDefault(joueur, ParticipantsDePassage.JOUEUR_PARTI));
    }

    private static List<Interprete> interpretes(List<UUID> joueurs, Map<UUID, String> pseudos) {
        return joueurs.stream().map(joueur -> interprete(joueur, pseudos)).toList();
    }

    record Interprete(UUID joueurId, String pseudo) { }

    record LignePalmares(UUID passageId, long numero, Long rang, Instrumentale instrumentale,
            Interprete interprete, long nombreVotes, Integer moyenneDixiemes) { }

    /** Invitation en suspens, vue par la seule Manette de l’invité, avec la cible à renvoyer (#74). */
    record InvitationRecue(CiblePassage passage, long numero, Instrumentale instrumentale, Interprete inscrivant,
            List<Interprete> interpretes) { }

    record InvitationEmise(UUID passageId, Interprete invite, InvitationPassage.Etat etat) { }

    record InvitationEnAttente(UUID passageId, Interprete invite) { }

    record Couplet(int numero, Interprete interprete, int premiereMesure, int derniereMesure) { }

    record Couplets(int mesuresParCouplet, int nombreCouplets, List<Interprete> ordre, Couplet courant, Couplet suivant,
            boolean suivantAnnonce) { }

    record VoixInterprete(Interprete interprete, long nombreVoix) { }

    static Couplets couplets(CoupletsDuPassage etat, Map<UUID, String> pseudos) {
        int suivant = etat.courant() + 1;
        return new Couplets(etat.mesuresParCouplet(), etat.nombreCouplets(), interpretes(etat.ordre(), pseudos),
                couplet(etat, etat.courant(), pseudos), suivant > etat.nombreCouplets() ? null : couplet(etat, suivant, pseudos),
                etat.suivantAnnonce());
    }

    private static Couplet couplet(CoupletsDuPassage etat, int numero, Map<UUID, String> pseudos) {
        int premiere = etat.premiereMesure(numero);
        return new Couplet(numero, interprete(etat.interpreteDu(numero), pseudos), premiere, premiere + etat.mesuresParCouplet() - 1);
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

        private Map<String, Object> bloc(Destination destination, Catalogue catalogue) {
            var disponibles = catalogue.disponibles();
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
                bloc.put("instrumentales", disponibles);
                bloc.put("peutGererFile", true);
                bloc.put("peutAppeler", !enPause && !instantane.pilotageAutomatique() && !cycle.actif() && !ordre.isEmpty());
                bloc.put("peutPasser", cycle.actif());
                bloc.put("invitationsEnAttente", invitations().stream().filter(InvitationPassage::enSuspens)
                        .map(i -> new InvitationEnAttente(i.passageId(), interprete(i.inviteId(), pseudos))).toList());
            } else if (destination.type() == Destination.Type.JOUEUR) {
                completerManette(bloc, destination.joueurId(), disponibles, ordre, positions);
            } else {
                bloc.put("lecteurDesigneId", catalogue.lecteurDesigneId());
            }
            return Collections.unmodifiableMap(bloc);
        }

        private void completerManette(Map<String, Object> bloc, UUID joueur, List<Instrumentale> disponibles,
                List<Passage> ordre, Map<UUID, Long> positions) {
            var selection = instantane.reglages().selectionDisponible(disponibles);
            var personnels = ordre.stream().filter(passage -> passage.interpretes().contains(joueur)).toList();
            bloc.put("instrumentales", selection);
            bloc.put("mesPassagesEnAttente", personnels.stream().map(p -> enAttente(p, positions)).toList());
            // Un invité devenu inscripteur après un départ ne voit pas sa propre Invitation.
            bloc.put("invitationsEmises", invitations().stream().filter(i -> file.trouver(i.passageId()).inscripteurId().equals(joueur)
                    && !i.inviteId().equals(joueur)).map(i -> new InvitationEmise(i.passageId(), interprete(i.inviteId(), pseudos), i.etat()))
                    .toList());
            bloc.put("invitationsRecues", invitations().stream().filter(i -> i.enSuspens() && i.inviteId().equals(joueur)).map(i -> {
                Passage p = file.trouver(i.passageId());
                return new InvitationRecue(new CiblePassage(p.id(), p.numeroModification()), p.numero(),
                        instantane.etat().contenus().get(p.id()).instrumentale(), interprete(p.inscripteurId(), pseudos),
                        interpretes(p.interpretes(), pseudos));
            }).toList());
            bloc.put("peutInscrire", selection.stream()
                    .anyMatch(fiche -> file.verifierInscription(joueur, fiche.instrumentaleId().toString()).isEmpty()));
            bloc.put("peutConfirmerPresence", !enPause && cycle.actif() && cycle.courant().etat() == EtatPassage.APPELE
                    && cycle.courant().interpretes().contains(joueur));
            var couplets = cycle.courant() == null ? null : instantane.etat().contenus().get(cycle.courant().id()).couplets();
            bloc.put("monCoupletAnnonce", couplets != null && couplets.suivantAnnonce()
                    && couplets.interpreteDu(couplets.courant() + 1).equals(joueur));
        }

        /** Invitations des Passages en attente dont l’invité est présent, dans l’ordre de la File puis d’émission. */
        private List<InvitationPassage> invitations() {
            var invitations = instantane.etat().file().invitations();
            return file.ordreEffectif().stream().flatMap(p -> invitations.stream()
                    .filter(i -> i.passageId().equals(p.id()) && pseudos.containsKey(i.inviteId()))).toList();
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
            var couplets = instantane.etat().contenus().get(passage.id()).couplets();
            donnees.put("couplets", couplets == null ? null : couplets(couplets, pseudos));
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
            boolean designation = passage.interpretes().size() > 1;
            donnees.put("scrutin", designation ? "MEILLEUR_INTERPRETE" : "NOTE");
            if (joueur == null) {
                donnees.put("nombreVotes", projection.nombreVotes());
                donnees.put("voix", !designation || projection.ouvert() ? null : VoteDuPublic.voix(vote, passage).stream()
                        .map(voix -> new VoixInterprete(interprete(voix.interpreteId(), pseudos), voix.nombre())).toList());
            } else {
                var recu = vote.votes().stream().filter(avis -> joueur.equals(avis.joueurId())).findFirst();
                donnees.put("maNote", recu.map(VoteRecu::note).orElse(null));
                donnees.put("monChoix", recu.map(VoteRecu::interpreteDesigneId).orElse(null));
                donnees.put("peutVoter", !enPause && projection.ouvert() && recu.isEmpty() && !passage.interpretes().contains(joueur));
            }
            return Collections.unmodifiableMap(donnees);
        }

        private Map<String, Object> passage(Passage passage) {
            var donnees = new LinkedHashMap<String, Object>();
            donnees.put("passageId", passage.id());
            donnees.put("numeroModification", passage.numeroModification());
            donnees.put("numero", passage.numero());
            donnees.put("instrumentale", instantane.etat().contenus().get(passage.id()).instrumentale());
            donnees.put("interpretes", interpretes(passage.interpretes(), pseudos));
            return donnees;
        }
    }
}
