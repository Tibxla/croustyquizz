package fr.croustyquizz.karaoke;

/** Métadonnées du fichier disponible sur l’Écran de salle. */
public record FichierAudioLocal(String nom, long tailleOctets, Long dureeMs) {
    public FichierAudioLocal {
        if (nom == null || nom.isEmpty() || nom.contains("/") || nom.contains("\\")
                || tailleOctets <= 0 || (dureeMs != null && dureeMs < 0)) {
            throw new IllegalArgumentException("Le fichier audio local est invalide.");
        }
    }
}
