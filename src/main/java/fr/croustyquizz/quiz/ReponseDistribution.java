package fr.croustyquizz.quiz;

import java.util.UUID;

public record ReponseDistribution(UUID propositionId, int nombreReponses) {
}
