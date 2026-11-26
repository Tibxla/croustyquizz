package fr.croustyquizz.karaoke;

/** Usage d'une entrée de la Bibliothèque de base, et dossier qui range son fichier. */
public enum TypeDeMorceau {
    ATTENTE("attente", "attente/"), INSTRUMENTALE("instrumentale", "instrumentales/"), CHANSON("chanson", "chansons/");

    private final String nom;
    private final String dossier;

    TypeDeMorceau(String nom, String dossier) { this.nom = nom; this.dossier = dossier; }

    public String nom() { return nom; }
    public String dossier() { return dossier; }
}
