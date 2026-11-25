package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.CommandeDeManche;
import fr.croustyquizz.passage.CiblePassage;
import java.time.Instant;
import java.util.UUID;

public record DeclarerLectureChanson(Acteur acteur, Instant recueLe, UUID envoiId,
        CiblePassage cible, UUID etapeId, UUID lecteurId, LecturePreparee preparation) implements CommandeDeManche {}
