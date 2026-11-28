package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Minuteur;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.ResultatDeCommande;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Votes indépendants des Passages d’une Manche, appelés sous le verrou de Soirée. */
public final class VoteDuPublic {
    private final FileAttente file;
    private final Horloge horloge;
    private final Duration dureeVote;
    private final Consumer<ClotureVote> surClotureVote;
    private final Map<UUID, EtatVotePassage> votes = new LinkedHashMap<>();
    private final Map<UUID, Rappel> rappels = new HashMap<>();
    private long generation;

    public VoteDuPublic(FileAttente file, Horloge horloge, Duration dureeVote, EtatVoteDuPublic etat,
            Consumer<ClotureVote> surClotureVote) {
        if (!dureeVote.isPositive()) { throw new IllegalArgumentException("La durée du vote doit être positive."); }
        this.file = file;
        this.horloge = horloge;
        this.dureeVote = dureeVote;
        this.surClotureVote = surClotureVote;
        etat.votes().forEach(vote -> votes.put(vote.passageId(), vote));
    }

    public void ouvrir(UUID passageId, Instant instant, boolean enPause) {
        var vote = new EtatVotePassage(passageId, instant, instant.plus(dureeVote),
                enPause ? instant : null, null, List.of());
        if (votes.putIfAbsent(passageId, vote) == null && !enPause) {
            programmer(vote, horloge.maintenant());
        }
    }

    public ResultatDeCommande recevoir(VoterPassage action, boolean enPause) {
        var refus = voter(action.joueurId(), action.cible(), action.note(), action.recueLe(), enPause);
        if (refus.isPresent()) { return refus.get(); }
        Passage passage = file.trouver(action.cible().passageId());
        var accuse = new AccuseVote(action.envoiId(), passage.soireeId(), passage.mancheId(),
                passage.id(), action.note(), action.recueLe());
        return new Accepte(accuse, Set.of(Destination.SALLE, Destination.CONSOLE, Destination.joueur(action.joueurId())),
                "vote-enregistre");
    }

    public Optional<Refuse> voter(UUID joueurId, CiblePassage cible, int note, Instant recueLe, boolean enPause) {
        Passage passage = file.trouver(cible.passageId());
        if (passage == null) {
            return Optional.of(new Refuse(404, "RESSOURCE_INTROUVABLE", "Ce Passage est introuvable."));
        }
        if (enPause) {
            return refuser("ETAT_INCOMPATIBLE", "La Manche est en pause : tu pourras voter à la reprise.");
        }
        if (passage.interpretes().contains(joueurId)) {
            return refuser("VOTE_INTERDIT", "Tu ne peux pas voter pour un Passage dont tu es Interprète.");
        }
        if (passage.numeroModification() != cible.numeroModification()) { return Optional.of(FileAttente.perimee()); }
        EtatVotePassage vote = votes.get(passage.id());
        if (vote == null || !vote.ouvert() || !recueLe.isBefore(vote.echeanceLe())) {
            return refuser("VOTE_CLOS", "Le vote de ce Passage est clos.");
        }
        if (vote.votes().stream().anyMatch(recu -> recu.joueurId().equals(joueurId))) {
            return refuser("VOTE_DEJA_ENREGISTRE", "Tu as déjà voté pour ce Passage.");
        }
        List<VoteRecu> notes = new ArrayList<>(vote.votes());
        notes.add(new VoteRecu(joueurId, note, recueLe));
        votes.put(passage.id(), new EtatVotePassage(passage.id(), vote.ouvertLe(), vote.echeanceLe(),
                vote.pauseDepuis(), null, notes, vote.suivantDemarre()));
        return Optional.empty();
    }

    private static Optional<Refuse> refuser(String code, String detail) {
        return Optional.of(new Refuse(409, code, detail));
    }

    public void signalerDemarrage(UUID passageId) {
        votes.replaceAll((id, vote) -> id.equals(passageId) || vote.suivantDemarre() ? vote
                : new EtatVotePassage(id, vote.ouvertLe(), vote.echeanceLe(), vote.pauseDepuis(), vote.clotureLe(),
                        vote.votes(), true));
    }

    public void suspendre(Instant maintenant) {
        votesOuverts().stream().filter(vote -> vote.pauseDepuis() == null).forEach(vote -> {
            votes.put(vote.passageId(), new EtatVotePassage(vote.passageId(), vote.ouvertLe(), vote.echeanceLe(),
                    maintenant, null, vote.votes(), vote.suivantDemarre()));
            annulerRappel(vote.passageId());
        });
    }

    public void reprendre(Instant maintenant) {
        votesOuverts().stream().filter(vote -> vote.pauseDepuis() != null).forEach(vote -> {
            Instant echeance = vote.echeanceLe().plus(Duration.between(vote.pauseDepuis(), maintenant));
            var repris = new EtatVotePassage(vote.passageId(), vote.ouvertLe(), echeance, null, null,
                    vote.votes(), vote.suivantDemarre());
            votes.put(vote.passageId(), repris);
            programmer(repris, maintenant);
        });
    }

    public void cloreTout(Instant maintenant) {
        votesOuverts().forEach(vote -> clore(vote, maintenant));
    }

    private List<EtatVotePassage> votesOuverts() {
        return votes.values().stream().filter(EtatVotePassage::ouvert).toList();
    }

    public boolean aUnVoteOuvert() {
        return votes.values().stream().anyMatch(EtatVotePassage::ouvert);
    }

    public EtatVoteDuPublic etat() {
        return new EtatVoteDuPublic(List.copyOf(votes.values()));
    }

    public void reprogrammer(Instant maintenant) {
        votesOuverts().stream().filter(vote -> vote.pauseDepuis() == null).forEach(vote -> programmer(vote, maintenant));
    }

    private void programmer(EtatVotePassage vote, Instant maintenant) {
        UUID passageId = vote.passageId();
        annulerRappel(passageId);
        generation++;
        long numero = generation;
        Minuteur minuteur = horloge.programmer(delaiRestant(vote, maintenant), () -> echu(passageId, numero));
        rappels.put(passageId, new Rappel(numero, minuteur));
    }

    private void echu(UUID passageId, long numero) {
        Rappel rappel = rappels.get(passageId);
        EtatVotePassage vote = votes.get(passageId);
        if (rappel != null && rappel.generation() == numero && vote.ouvert()) {
            rappels.remove(passageId);
            clore(vote, horloge.maintenant());
        }
    }

    private void annulerRappel(UUID passageId) {
        Rappel rappel = rappels.remove(passageId);
        if (rappel != null) { rappel.minuteur().annuler(); }
    }

    private void clore(EtatVotePassage vote, Instant maintenant) {
        annulerRappel(vote.passageId());
        votes.put(vote.passageId(), new EtatVotePassage(vote.passageId(), vote.ouvertLe(), vote.echeanceLe(),
                null, maintenant, vote.votes(), vote.suivantDemarre()));
        surClotureVote.accept(new ClotureVote(vote.passageId(), vote.votes().size(), sommeNotes(vote), maintenant));
    }

    public static Integer moyenneDixiemes(long nombreVotes, long sommeNotes) {
        if (nombreVotes == 0) { return null; }
        return (int) ((20 * sommeNotes + nombreVotes) / (2 * nombreVotes));
    }

    /** Les Votes ouverts et le dernier Vote clos, du plus récent au plus ancien. */
    public static List<EtatVotePassage> visibles(EtatVoteDuPublic etat, FileAttente file) {
        var votes = etat.votes();
        var visibles = new ArrayList<>(votes.stream().filter(EtatVotePassage::ouvert).toList());
        votes.stream().filter(vote -> !vote.ouvert())
                .max(Comparator.comparing(EtatVotePassage::clotureLe)
                        .thenComparingLong(vote -> file.trouver(vote.passageId()).numero()))
                .ifPresent(visibles::add);
        visibles.sort(Comparator.comparing(EtatVotePassage::ouvertLe).reversed()
                .thenComparing(Comparator.comparingLong((EtatVotePassage vote) -> file.trouver(vote.passageId()).numero()).reversed()));
        return List.copyOf(visibles);
    }

    public static ProjectionVote projeter(EtatVotePassage vote, Instant maintenant) {
        long nombreVotes = vote.votes().size();
        long somme = sommeNotes(vote);
        return new ProjectionVote(vote.passageId(), vote.ouvert(), delaiRestant(vote, maintenant).toMillis(),
                maintenant, vote.clotureLe(), nombreVotes, somme, vote.ouvert() ? null : moyenneDixiemes(nombreVotes, somme),
                vote.suivantDemarre());
    }

    private static long sommeNotes(EtatVotePassage vote) {
        return vote.votes().stream().mapToLong(VoteRecu::note).sum();
    }

    private static Duration delaiRestant(EtatVotePassage vote, Instant maintenant) {
        if (!vote.ouvert()) { return Duration.ZERO; }
        Duration delai = Duration.between(vote.pauseDepuis() == null ? maintenant : vote.pauseDepuis(), vote.echeanceLe());
        return delai.isNegative() ? Duration.ZERO : delai;
    }

    private record Rappel(long generation, Minuteur minuteur) { }
}
