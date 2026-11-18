# Diagrammes de classes

Source : PRD (issue #2). Les noms de classes sont ceux du code Java, sans accents ; les concepts sont définis dans `GLOSSARY.md`. Les images PNG sont régénérées à partir des blocs Mermaid ci-dessous.

## Domaine

Les objets du domaine ; les captures immuables du Karaoké sont persistées par son service.

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
    class CleAssistant {
        +UUID id
        +String libelle
        +String empreinte
        +Instant creeeLe
        +Instant revoqueeLe
        +revoquer()
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
        PRETE
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
        ASSISTANT
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
        +Duration delaiAppel
    }
    class Chanson {
        <<record>>
        +String chansonId
        +long idLrclib
        +String titre
        +String artiste
        +String album
        +Long dureeMs
        +EtatParoles etatParoles
    }
    class ContenuKaraoke {
        <<record>>
        +Chanson chanson
        +List~LigneParoles~ lignes
    }
    class LigneParoles {
        <<record>>
        +long positionMs
        +String texte
    }
    namespace passage {
        class FileAttente {
            +depuis(EtatFileAttente) FileAttente
            +etat() EtatFileAttente
            +ordreEffectif() List~Passage~
            +positions() Map
            +extraireProchainPassage(boolean) Optional~Passage~
        }
        class Passage {
            <<record>>
            +UUID id
            +UUID soireeId
            +UUID mancheId
            +UUID inscripteurId
            +List~UUID~ interpretes
            +String contenuId
            +Duration duree
            +long numero
            +long numeroModification
            +EtatPassage etat
            +Instant inscriptionLe
            +long ordreInscription
            +long tourNormal
            +long cleOrdre
            +Long tourConsomme
            +TraceTerminale traceTerminale
        }
        class EtatPassage {
            <<enumeration>>
            EN_ATTENTE
            RETIRE
            ANNULE
            APPELE
            EN_COURS
            TERMINE
            SAUTE
        }
        class EtatFileAttente {
            <<record>>
            +long limitePassagesEnAttente
            +long tourOuvert
            +long prochainOrdreInscription
            +long prochaineCleOrdre
            +UUID passagePrioritaireId
            +List~Passage~ passages
            +Map derniersToursConsommes
        }
        class TraceTerminale {
            <<record>>
            +String motif
            +UUID acteurId
            +Instant instant
        }
    }
    class Vote {
        +UUID id
        +int note
        +Instant emisLe
    }

    Lieu "1" --> "1..*" Animateur : emploie
    Lieu "1" --> "*" Soiree : organise
    Lieu "1" --> "*" CleAssistant : autorise
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

    MancheKaraoke "1" *-- "1" FileAttente
    FileAttente "1" *-- "*" Passage : conserve tous les états
    FileAttente ..> EtatFileAttente : capture
    Passage "*" --> "1..4" Joueur : Interprètes ordonnés
    Passage --> EtatPassage
    Passage "1" *-- "0..1" TraceTerminale
    MancheKaraoke "1" *-- "*" ContenuKaraoke : indexé par passageId
    ContenuKaraoke --> Chanson
    ContenuKaraoke "1" *-- "*" LigneParoles
    Passage "1" *-- "*" Vote
    Vote "*" --> "1" Joueur : votant
```

![Diagramme de classes du domaine](diagramme-classes-domaine.png)

## Services du socle et des modes

Le contrat de Manche (package `manche`) : chaque Mode de jeu fournit une `FabriqueDeDeroulement`, qui crée un `DeroulementDeManche` par Manche. Les ports vers l'extérieur sont en bas.

```mermaid
classDiagram
    direction TB

    class FabriqueDeDeroulement~R,E~ {
        <<interface>>
        +ModeDeJeu mode()
        +Class~R~ typeReglages()
        +ReglagesDeManche reglagesCommuns(R)
        +DeroulementDeManche creer(ContexteDeManche, R)
        +DeroulementDeManche restaurer(ContexteDeManche, R, E, EtatManche)
    }
    class DeroulementDeManche {
        <<interface>>
        +demarrer()
        +recevoir(ActionJoueur)
        +commander(CommandeDeManche) ResultatDeCommande
        +mettreEnPause(CommandePilotage) ResultatDeCommande
        +reprendre(CommandePilotage) ResultatDeCommande
        +passerEtape(CommandePilotage) ResultatDeCommande
        +terminer(CommandePilotage) ResultatDeCommande
        +instantane() Object
        +boolean estTerminee()
        +List~AttributionDePoints~ points()
    }
    class ContexteDeManche {
        <<record>>
        +UUID soireeId
        +UUID mancheId
        +ReglagesDeManche reglages
        +Horloge horloge
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

    class OutilsAssistant {
        +List~Soiree~ listerSoirees()
        +Soiree creerSoiree(titre, Format, Pilotage)
        +ajouterManche(UUID soireeId, ModeDeJeu, ReglagesDeManche)
        +ajouterQuestions(UUID mancheId, List~Question~)
        +List~Morceau~ chercherMorceaux(String requete)
        +ajouterMorceaux(UUID mancheId, List~Long~ idsDeezer)
        +List~LigneClassement~ consulterClassement(UUID soireeId)
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
        +List~Chanson~ rechercher(String recherche)
        +Optional~ContenuKaraoke~ charger(long idLrclib)
    }
    class AdaptateurLrclib
    class ServiceKaraoke {
        +rechercherChansons(Acteur, UUID, UUID, String, UUID) ResultatsChansons
        +consulterChanson(Acteur, UUID, UUID, String) FicheChanson
        +inscrirePassage(UUID, EnvoiDeCommande, InscriptionPassage) ResultatDeCommande
        +desinscrirePassage(UUID, UUID, EnvoiDeCommande, DesinscriptionPassage) ResultatDeCommande
        +retirerPassage(UUID, UUID, EnvoiDeCommande, RetraitPassage) ResultatDeCommande
        +deplacerPassage(UUID, UUID, EnvoiDeCommande, PlacementPassage) ResultatDeCommande
        +definirPrioritePassage(UUID, EnvoiDeCommande, PrioritePassage) ResultatDeCommande
        +reglerLimitePassages(UUID, EnvoiDeCommande, LimitePassages) ResultatDeCommande
    }
    class KaraokeControleur
    class ConvertisseurCorpsKaraoke
    class FournisseurDeBlocKaraoke
    class VuesKaraoke
    class PersistanceKaraokeJpa
    class PersistancePassageJpa {
        +charger(UUID mancheId) EtatFileAttente
        +enregistrer(UUID mancheId, EtatFileAttente)
        +numeroPropose(UUID soireeId) long
        +incrementerNumero(UUID soireeId, long numeroAccepte)
    }
    class ContenuPassagePersistant
    class FileAttentePersistante
    class PassagePersistant
    class InterpretePersistant
    class TourConsommePersistant
    class NumeroSoireePersistant
    class PorteeDePassage {
        <<interface>>
        +verifier(Acteur, UUID soireeId, UUID mancheId)
        +estActive(UUID soireeId, UUID mancheId) boolean
    }
    class ParticipantsDePassage {
        <<interface>>
        +estPresent(UUID soireeId, UUID joueurId) boolean
        +pseudo(UUID soireeId, UUID joueurId) String
    }
    class CorrecteurTolerant {
        +Verdict juger(String saisie, Morceau)
    }
    class FabriqueKaraoke {
        +creer(ContexteDeManche, ReglagesKaraoke) DeroulementKaraoke
        +restaurer(ContexteDeManche, ReglagesKaraoke, EtatKaraokePersiste, EtatManche) DeroulementKaraoke
    }
    class ReglagesKaraoke {
        +long limitePassagesEnAttente
    }
    class InstantaneKaraoke {
        +EtatManche etatManche
        +EtatFileAttente file
        +Map contenus
    }
    class EtatKaraokePersiste {
        +EtatFileAttente file
        +Map contenus
    }
    class CommandeDeManche {
        <<interface>>
        +Acteur acteur()
        +Instant recueLe()
    }
    class Accepte {
        +Object accuse
        +Set~Destination~ aRepublier
        +String typeEvenement
        +int statutHttp
    }
    namespace passage {
        class FileAttente {
            +verifierInscription(UUID, String) Optional~Refuse~
            +inscrire(NouveauPassage) ResultatFile
            +desinscrire(UUID, CiblePassage, Instant) ResultatFile
            +retirer(UUID, CiblePassage, Instant) ResultatFile
            +deplacer(CiblePassage, long, CiblePassage) ResultatFile
            +definirPriorite(CiblePassage, boolean) ResultatFile
            +reglerLimite(long) ResultatFile
            +annulerAttentes(Instant, UUID) ResultatFile
            +estimerAttentes(Instant, boolean, Instant, boolean, Duration) Map
        }
        class CiblePassage {
            +UUID passageId
            +long numeroModification
        }
        class ResultatFile {
            <<interface>>
            Refus
            SansEffet
            Modifiee
        }
    }

    FabriqueDeDeroulement ..> DeroulementDeManche : crée
    FabriqueDeDeroulement ..> ContexteDeManche
    ContexteDeManche --> ReglagesDeManche
    ContexteDeManche --> Horloge
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

    OutilsAssistant --> ServiceSoiree
    OutilsAssistant --> CatalogueMusical
    OutilsAssistant --> ServiceClassement
    DeroulementQuiz --> SourceDeQuestions
    DeroulementBlindTest --> CatalogueMusical
    DeroulementBlindTest --> CorrecteurTolerant
    DeroulementKaraoke --> FileAttente
    ServiceDeManche <|-- ServiceKaraoke
    KaraokeControleur --> ServiceKaraoke
    KaraokeControleur ..> ConvertisseurCorpsKaraoke : liaison avant identité
    ServiceKaraoke --> PorteeDePassage
    ServiceKaraoke --> ParticipantsDePassage
    ServiceKaraoke --> PersistancePassageJpa : numéro sous verrou
    ServiceKaraoke --> SourceDeParoles : consulte hors verrou
    ServiceKaraoke --> PersistanceKaraokeJpa : transaction commune
    PersistanceKaraokeJpa --> PersistancePassageJpa
    PersistanceKaraokeJpa --> ContenuPassagePersistant
    PersistancePassageJpa --> FileAttentePersistante
    PersistancePassageJpa --> PassagePersistant
    PersistancePassageJpa --> InterpretePersistant
    PersistancePassageJpa --> TourConsommePersistant
    PersistancePassageJpa --> NumeroSoireePersistant
    FournisseurDeBloc <|.. FournisseurDeBlocKaraoke
    FournisseurDeBlocKaraoke --> ServiceKaraoke : instantané
    FournisseurDeBlocKaraoke --> VuesKaraoke
    VuesKaraoke --> ParticipantsDePassage : pseudos publics
    VuesKaraoke --> FileAttente : ordre et estimations
    SourceDeParoles <|.. AdaptateurLrclib
    SourceDeParoles ..> Chanson
    SourceDeParoles ..> ContenuKaraoke
    AdaptateurLrclib --> Horloge : attente Retry-After
    FabriqueDeDeroulement <|.. FabriqueKaraoke : R=ReglagesKaraoke, E=EtatKaraokePersiste
    FabriqueKaraoke ..> DeroulementKaraoke : crée ou restaure
    DeroulementKaraoke ..> InstantaneKaraoke : capture
    DeroulementKaraoke ..> Accepte : rend
    DeroulementDeManche ..> CommandeDeManche : reçoit
    FileAttente ..> CiblePassage
    FileAttente ..> ResultatFile
```

![Diagramme de classes des services](diagramme-classes-services.png)

## Régénérer les images

Depuis la racine, extraire les deux blocs Mermaid dans des fichiers temporaires :

```sh
python3 - <<'PYTHON'
from pathlib import Path
source = Path("docs/conception/diagrammes-de-classes.md").read_text()
blocs = [partie.split("```", 1)[0].strip() for partie in source.split("```mermaid\n")[1:]]
for nom, bloc in zip(("domaine", "services"), blocs):
    Path(f"/tmp/croustyquizz-{nom}.mmd").write_text(bloc + "\n")
PYTHON
npx --yes --package @mermaid-js/mermaid-cli mmdc -s 3 -i /tmp/croustyquizz-domaine.mmd -o docs/conception/diagramme-classes-domaine.png
npx --yes --package @mermaid-js/mermaid-cli mmdc -s 3 -i /tmp/croustyquizz-services.mmd -o docs/conception/diagramme-classes-services.png
```

Si le bac à sable impose un Chrome sans sandbox, ajouter `-p /tmp/croustyquizz-puppeteer.json` aux deux commandes, avec `{"args":["--no-sandbox"]}` dans ce fichier temporaire. Vérifier visuellement les deux PNG après génération.
