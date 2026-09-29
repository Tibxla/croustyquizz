# Diagrammes de classes

Source : PRD (issue #2). Les noms de classes sont ceux du code Java, sans accents ; les concepts sont définis dans `CONTEXT.md`. Les images PNG sont régénérées à partir des blocs Mermaid ci-dessous.

## Domaine

Les entités persistées.

```mermaid
classDiagram
    direction TB

    class Lieu {
        +UUID id
        +String nom
        +String ville
    }
    class Animateur {
        +UUID id
        +String email
        +String motDePasseHache
        +String nom
    }
    class Soiree {
        +UUID id
        +String codeAcces
        +String titre
        +Format format
        +Pilotage pilotage
        +EtatSoiree etat
        +Instant creeeLe
        +Instant debuteeLe
        +Instant termineeLe
        +ouvrir()
        +lancer()
        +terminer()
        +mancheEnCours() Manche
    }
    class Format {
        <<enumeration>>
        SOLO
        EQUIPE
    }
    class Pilotage {
        <<enumeration>>
        MANUEL
        AUTOMATIQUE
    }
    class EtatSoiree {
        <<enumeration>>
        PREPARATION
        OUVERTE
        EN_COURS
        TERMINEE
    }

    class Concurrent {
        <<interface>>
        +UUID id()
        +String nom()
    }
    class Joueur {
        +UUID id
        +String pseudo
        +String jetonReconnexion
        +Instant arriveLe
        +boolean exclu
        +exclure()
    }
    class Equipe {
        +UUID id
        +String nom
        +ajouterMembre(Joueur)
    }

    class Manche {
        <<abstract>>
        +UUID id
        +int ordre
        +ModeDeJeu mode
        +EtatManche etat
        +Duration tempsParEtape
        +Instant debuteeLe
        +Instant termineeLe
    }
    class ModeDeJeu {
        <<enumeration>>
        QUIZ
        BLIND_TEST
        KARAOKE
    }
    class EtatManche {
        <<enumeration>>
        EN_ATTENTE
        EN_COURS
        EN_PAUSE
        TERMINEE
    }
    class Points {
        +UUID id
        +int valeur
        +String motif
        +Instant attribuesLe
    }

    class MancheQuiz {
        +List~String~ categories
        +Difficulte difficulte
        +int nombreDeQuestions
    }
    class Question {
        +UUID id
        +String enonce
        +String categorie
        +Difficulte difficulte
        +SourceQuestion source
    }
    class Proposition {
        +UUID id
        +String texte
        +boolean juste
        +int position
    }
    class QuestionDeManche {
        +int ordre
        +Instant poseeLe
    }
    class Reponse {
        +UUID id
        +Instant envoyeeLe
        +boolean juste
    }
    class Difficulte {
        <<enumeration>>
        FACILE
        MOYENNE
        DIFFICILE
    }
    class SourceQuestion {
        <<enumeration>>
        BANQUE
        ANIMATEUR
        IMPORT
    }

    class MancheBlindTest {
        +String theme
        +Duration dureeExtrait
    }
    class Morceau {
        +UUID id
        +long idDeezer
        +String titre
        +String artiste
        +String urlPochette
    }
    class MorceauDeManche {
        +int ordre
        +Instant diffuseLe
    }
    class Buzz {
        +UUID id
        +Instant recuLe
        +int rang
    }
    class PropositionDeTitre {
        +UUID id
        +String texteSaisi
        +boolean titreTrouve
        +boolean artisteTrouve
        +Verdict verdict
    }
    class Verdict {
        <<enumeration>>
        ACCEPTE
        REFUSE
        RENVERSE_PAR_ANIMATEUR
    }

    class MancheKaraoke {
        +Duration delaiConfirmation
    }
    class Chanson {
        +UUID id
        +long idLrclib
        +String titre
        +String artiste
        +Duration duree
        +String parolesSynchronisees
    }
    class Passage {
        +UUID id
        +int position
        +EtatPassage etat
        +Instant appeleLe
        +Instant debuteLe
        +noteMoyenne() double
    }
    class EtatPassage {
        <<enumeration>>
        EN_ATTENTE
        APPELE
        EN_COURS
        TERMINE
        SAUTE
    }
    class Vote {
        +UUID id
        +int note
        +Instant emisLe
    }

    Lieu "1" --> "1..*" Animateur : emploie
    Lieu "1" --> "*" Soiree : organise
    Animateur "1" --> "*" Soiree : crée
    Soiree "1" *-- "*" Manche : enchaîne
    Soiree "1" *-- "*" Joueur : accueille
    Soiree "1" *-- "*" Equipe : regroupe
    Equipe "0..1" o-- "1..*" Joueur : membres
    Concurrent <|.. Joueur
    Concurrent <|.. Equipe
    Manche "1" *-- "*" Points : attribue
    Points "*" --> "1" Concurrent : bénéficiaire

    Manche <|-- MancheQuiz
    Manche <|-- MancheBlindTest
    Manche <|-- MancheKaraoke

    MancheQuiz "1" *-- "*" QuestionDeManche
    QuestionDeManche "*" --> "1" Question
    Question "1" *-- "4" Proposition
    QuestionDeManche "1" *-- "*" Reponse
    Reponse "*" --> "1" Joueur : auteur
    Reponse "*" --> "1" Proposition : choisie

    MancheBlindTest "1" *-- "*" MorceauDeManche
    MorceauDeManche "*" --> "1" Morceau
    MorceauDeManche "1" *-- "*" Buzz
    Buzz "*" --> "1" Joueur : auteur
    Buzz "1" --> "0..1" PropositionDeTitre

    MancheKaraoke "1" *-- "*" Passage : file d'attente
    Passage "*" --> "1" Joueur : chanteur
    Passage "*" --> "1" Chanson
    Passage "1" *-- "*" Vote
    Vote "*" --> "1" Joueur : votant
```

![Diagramme de classes du domaine](diagramme-classes-domaine.png)

## Services du socle et des modes

Le contrat de Manche (package `manche`) : chaque Mode de jeu fournit une `FabriqueDeDeroulement`, qui crée un `DeroulementDeManche` par Manche. Les ports vers l'extérieur sont en bas.

```mermaid
classDiagram
    direction TB

    class FabriqueDeDeroulement {
        <<interface>>
        +ModeDeJeu mode()
        +DeroulementDeManche creer(ContexteDeManche)
    }
    class DeroulementDeManche {
        <<interface>>
        +demarrer()
        +recevoir(ActionJoueur)
        +mettreEnPause()
        +reprendre()
        +passerEtape()
        +terminer()
        +boolean estTerminee()
        +List~AttributionDePoints~ points()
    }
    class ContexteDeManche {
        <<record>>
        +UUID soireeId
        +UUID mancheId
        +ReglagesDeManche reglages
        +Horloge horloge
        +DiffuseurTempsReel diffuseur
        +Runnable signalerFin
    }
    class ReglagesDeManche {
        <<record>>
        +int nombreEtapes
        +Duration tempsParEtape
    }
    class AttributionDePoints {
        <<record>>
        +UUID concurrentId
        +int valeur
        +String motif
    }
    class ActionJoueur {
        <<interface>>
        +UUID joueurId()
        +Instant recueLe()
    }
    class Horloge {
        <<interface>>
        +Instant maintenant()
        +Minuteur programmer(Duration, Runnable)
    }
    class Minuteur {
        <<interface>>
        +annuler()
    }
    class HorlogeSysteme
    class DiffuseurTempsReel {
        <<interface>>
        +diffuserSalle(UUID soireeId, Evenement)
        +envoyerJoueur(UUID joueurId, Evenement)
        +envoyerConsole(UUID soireeId, Evenement)
    }
    class Evenement {
        <<interface>>
        +String type()
    }

    class ServiceSoiree {
        +Soiree creer(Animateur, Format, Pilotage)
        +ajouterManche(UUID soireeId, ModeDeJeu, ReglagesDeManche)
        +ouvrir(UUID soireeId)
        +Joueur rejoindre(String codeAcces, String pseudo)
        +Joueur reconnecter(String jeton)
        +exclure(UUID joueurId)
    }
    class ServiceEquipe {
        +Equipe creer(UUID soireeId, String nom, Joueur)
        +rejoindre(UUID equipeId, Joueur)
        +Concurrent concurrentDe(Joueur)
    }
    class OrchestrateurDeSoiree {
        +lancer(UUID soireeId)
        +mancheSuivante(UUID soireeId)
        +mettreEnPause(UUID soireeId)
        +reprendre(UUID soireeId)
        +terminer(UUID soireeId)
    }
    class PilotageAutomatique {
        +surFinDeManche(UUID mancheId)
    }
    class RegistreDesModes {
        +FabriqueDeDeroulement pour(ModeDeJeu)
    }
    class ServiceClassement {
        +List~LigneClassement~ classementGeneral(UUID soireeId)
        +List~LigneClassement~ palmaresKaraoke(UUID soireeId)
    }
    class FiltreDePseudos {
        <<interface>>
        +boolean estAcceptable(String pseudo)
    }

    class DeroulementQuiz
    class DeroulementBlindTest
    class DeroulementKaraoke
    class SourceDeQuestions {
        <<interface>>
        +List~Question~ tirer(categories, Difficulte, int nombre)
    }
    class CatalogueMusical {
        <<interface>>
        +List~Morceau~ rechercher(String requete)
        +String lienExtrait(long idDeezer)
    }
    class SourceDeParoles {
        <<interface>>
        +List~Chanson~ rechercher(String requete)
        +Chanson charger(long idLrclib)
    }
    class CorrecteurTolerant {
        +Verdict juger(String saisie, Morceau)
    }
    class FileAttente {
        +inscrire(Joueur, Chanson)
        +desinscrire(Joueur)
        +reordonner(UUID passageId, int position)
        +Passage appelerSuivant()
    }

    FabriqueDeDeroulement ..> DeroulementDeManche : crée
    FabriqueDeDeroulement ..> ContexteDeManche
    ContexteDeManche --> ReglagesDeManche
    ContexteDeManche --> Horloge
    ContexteDeManche --> DiffuseurTempsReel
    DeroulementDeManche ..> ActionJoueur : reçoit
    DeroulementDeManche ..> AttributionDePoints : produit
    Horloge ..> Minuteur
    Horloge <|.. HorlogeSysteme
    DiffuseurTempsReel ..> Evenement

    DeroulementDeManche <|.. DeroulementQuiz
    DeroulementDeManche <|.. DeroulementBlindTest
    DeroulementDeManche <|.. DeroulementKaraoke

    OrchestrateurDeSoiree --> RegistreDesModes
    OrchestrateurDeSoiree --> DiffuseurTempsReel
    PilotageAutomatique --> OrchestrateurDeSoiree
    RegistreDesModes o-- FabriqueDeDeroulement
    ServiceSoiree --> FiltreDePseudos
    ServiceClassement ..> ServiceEquipe

    DeroulementQuiz --> SourceDeQuestions
    DeroulementBlindTest --> CatalogueMusical
    DeroulementBlindTest --> CorrecteurTolerant
    DeroulementKaraoke --> SourceDeParoles
    DeroulementKaraoke --> FileAttente
```

![Diagramme de classes des services](diagramme-classes-services.png)
