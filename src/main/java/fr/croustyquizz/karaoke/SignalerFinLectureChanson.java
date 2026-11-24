package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.CommandeDeManche;
import java.time.Instant;
import java.util.UUID;

public record SignalerFinLectureChanson(Acteur acteur, Instant recueLe, UUID envoiId,
        UUID passageId, UUID etapeId, UUID lectureId, UUID lecteurId, long positionMs) implements CommandeDeManche {}
