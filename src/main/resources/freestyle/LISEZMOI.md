# Dictionnaire de rimes du Freestyle

Ce dossier réunit deux sources, sous deux licences :

- les listes **Soirée**, **Ville** et **Sport** et le filtre de vulgarité (`mots-filtres.txt`), écrits pour CroustyQuizz et distribués sous la [licence MIT du dépôt](../../../../LICENSE) ;
- `lexique383-extrait.tsv`, un extrait de **Lexique 3.83**, distribué sous licence **CC BY-SA 4.0** (voir plus bas).

`DictionnaireDeRimesFourni.partage()` charge les trois listes, l'extrait et le filtre au premier appel, une seule fois, puis partage le même dictionnaire immuable entre toutes les Manches. Le chargement prend environ 0,2 s et le dictionnaire retient environ 13 Mo de mémoire (mesure du tas après ramasse-miettes, le 25 novembre 2026).

## Écrire une liste

Les fichiers TSV sont en UTF-8. Chaque ligne associe `mot`, `terminaison` et `fréquence`, séparés par des tabulations.

- Le mot garde son orthographe française et ses accents.
- La terminaison note les derniers sons prononcés en alphabet phonétique international simplifié, séparés par des espaces. Inscrire la dernière voyelle et tous les sons qui la suivent, ainsi que les sons qui la précèdent pour avoir au moins trois sons si le mot le permet. Un son nasal comme `ɔ̃` forme un seul élément. Le `e` final muet est omis.
- La fréquence de 1 à 100 est une appréciation de l'usage courant, estimée à la rédaction. Plus elle est élevée, plus le mot est courant.

Chaque thème a deux Familles de rimes. Chacune fournit au moins 32 suggestions distinctes pour une Grille de rimes en AAAA, même après retrait du mot de départ.

## Classement des rimes

`lumière` et `pierre` ont la terminaison `j ɛ ʁ`. Avec `serre` (`s ɛ ʁ`), ils partagent la Famille de rimes `ɛ ʁ`. Pour `lumière`, `pierre` passe en tête grâce aux trois sons communs : c'est une rime riche. Dans chaque groupe, la fréquence décroissante détermine l'ordre, puis l'orthographe départage les égalités.

Les listes et Lexique n'ont pas la même échelle de fréquence. Leurs suggestions se suivent sans se mélanger :

- avec un thème, ses mots viennent d'abord. Si la Famille de rimes du thème donne moins de 32 suggestions, de quoi remplir une Grille, Lexique la complète ;
- sans thème, Lexique vient d'abord, puis les mots des listes qu'il ne contient pas.

Une flexion du mot de départ qui se prononce comme lui n'est jamais proposée, même quand une liste thématique la contient : `maison` ne donne pas `maisons`, ni `soirées` le `soirée` du thème Soirée. Lexique fournit pour cela la prononciation complète et les lemmes des deux formes. Un homophone d'un autre mot reste une rime : `vert` donne `verre`. Pour la recherche, `cœur` et `coeur` sont un même mot.

## Filtre de vulgarité

`mots-filtres.txt` contient une forme ou un lemme par ligne. Un mot est écarté avant l'indexation, dans toutes les sources :

- s'il figure dans le fichier, quelles que soient sa casse et son accentuation ;
- ou si Lexique le rattache à un lemme du fichier : la ligne `con` écarte aussi `conne`, `cons` et `connes`, et `emmerder` toutes ses formes conjuguées.

Les listes thématiques n'ont pas de lemme : une flexion qu'elles contiennent et que Lexique ignore doit figurer dans le fichier. Le filtre écarte 178 formes de l'extrait. Il en écarte quelques-unes qui ont aussi un sens anodin, parce que Lexique les rattache aussi à un lemme filtré : `fous` (de `fou` et de `foutre`), `baiser` et `baisers`, `chatte` et `chattes` (de `chat`), `tringle`, `branlant`, `nichons` (de `nicher`). Mieux vaut perdre ces rimes qu'afficher une injure sur l'Écran de salle.

## Lexique 3.83

- **Source** : Lexique 3.83, base de données lexicales du français, https://www.lexique.org. Fichier `Lexique383.tsv` téléchargé le 25 novembre 2026.
- **Référence** : New, B., Pallier, C., Brysbaert, M., Ferrand, L. (2004). « Lexique 2 : A New French Lexical Database ». *Behavior Research Methods, Instruments, & Computers*, 36(3), 516-524.
- **Licence** : [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr). `lexique383-extrait.tsv` est une œuvre dérivée de Lexique 3.83, distribuée sous la même licence. Le reste du dépôt reste sous licence MIT.

### Changements apportés à l'extrait

L'extrait se régénère depuis la racine du dépôt, à partir du fichier d'origine, qui n'est pas versionné :

```
python3 tools/rimes/extraire-lexique.py <chemin>/Lexique383.tsv
```

Le script, en Python standard, applique ces changements :

1. Il ne garde que cinq colonnes : `ortho`, `phon` (notation de Lexique), `lemme`, `cgram` et `freqfilms2`.
2. Il écarte les entrées sans `phon` ou sans voyelle prononcée, les mots de plus de quatre syllabes (`nbsyll`), qui se placent mal dans une mesure, les expressions en plusieurs mots, les lettres isolées, les élisions (`d'`), les abréviations à point et les sigles : aucune voyelle écrite (`sncf`), ou quatre lettres au plus épelées une à une (`etc`, `ok`).
3. Il fusionne les lignes qui partagent `ortho` et `phon`, c'est-à-dire un même mot sous plusieurs catégories grammaticales. Lemmes et catégories sont réunis, séparés par des virgules, et les fréquences additionnées. Un même `ortho` garde plusieurs lignes seulement si sa prononciation diffère (`fils`).
4. Il écarte les formes rares : la fréquence additionnée doit atteindre 1 occurrence par million de mots dans les sous-titres de films (`freqfilms2`) ou dans les livres (`freqlivres`). Seule `freqfilms2`, la fréquence à l'oral, est conservée. Elle classe les rimes, et un mot présent surtout dans les livres arrive donc en fin de liste.
5. Il rétablit la ligature dans `oeu` et `oei` : `cœur`, `œil`. `coefficient` et `moelle` restent tels quels, comme `foetus` et `oesophage`, que la règle ne distingue pas.

Résultat : 34 853 lignes pour 34 831 mots, 1,28 Mo. À la lecture, le dictionnaire convertit `phon` en alphabet phonétique international, un caractère par son (`@` donne `ɑ̃`, `§` donne `ɔ̃`, `R` donne `ʁ`, etc.), et omet le `e` muet final (`°`) sauf s'il est la seule voyelle du mot (`de`).
