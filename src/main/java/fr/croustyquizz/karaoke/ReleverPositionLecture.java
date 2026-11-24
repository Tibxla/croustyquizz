package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.CommandeDeManche;
import java.time.Instant;
import java.util.UUID;

public record ReleverPositionLecture(Acteur acteur, Instant recueLe, UUID envoiId,
        UUID passageId, UUID etapeId, UUID lectureId, UUID lecteurId, UUID demandeId, long positionMs) implements CommandeDeManche {}
