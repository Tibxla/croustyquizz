package fr.croustyquizz.passage;

import fr.croustyquizz.manche.ActionJoueur;
import fr.croustyquizz.manche.Acteur;
import java.time.Instant;
import java.util.UUID;

public record VoterPassage(Acteur acteur, Instant recueLe, UUID envoiId, CiblePassage cible, int note)
        implements ActionJoueur {
    public VoterPassage { VoteRecu.verifierNote(note); }

    @Override public UUID joueurId() { return acteur.identifiant(); }
}
