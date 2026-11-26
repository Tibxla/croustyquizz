package fr.croustyquizz.karaoke;

/** Usage d'une entrée de la Bibliothèque de base, et dossier qui range son fichier. */
public enum TypeDEntree {
    ATTENTE("attente", "attente/"), INSTRUMENTALE("instrumentale", "instrumentales/"), CHANSON("chanson", "chansons/");

    private final String nomAuCatalogue;
    private final String dossierDesFichiers;

    TypeDEntree(String nom, String dossier) { this.nomAuCatalogue = nom; this.dossierDesFichiers = dossier; }

    public String nom() { return nomAuCatalogue; }
    public String dossier() { return dossierDesFichiers; }
}
