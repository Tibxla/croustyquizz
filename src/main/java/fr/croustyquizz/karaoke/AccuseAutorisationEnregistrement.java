package fr.croustyquizz.karaoke;

import java.time.Instant;
import java.util.UUID;

public record AccuseAutorisationEnregistrement(UUID envoiId, UUID soireeId, boolean autorise, Instant recueLe) { }
