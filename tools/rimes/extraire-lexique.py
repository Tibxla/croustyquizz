#!/usr/bin/env python3
"""Extrait de Lexique 3.83 les mots utiles au dictionnaire de rimes du Freestyle.

Usage, depuis la racine du dépôt :

    python3 tools/rimes/extraire-lexique.py <chemin>/Lexique383.tsv

Le fichier d'origine se télécharge sur https://www.lexique.org (Lexique 3.83,
CC BY-SA 4.0). Il n'est jamais versionné. Le script écrit
src/main/resources/freestyle/lexique383-extrait.tsv, qui reste sous CC BY-SA 4.0
(voir src/main/resources/freestyle/LISEZMOI.md). Python standard seulement : la
même source donne toujours le même extrait, octet pour octet.

Règles appliquées, dans l'ordre :

1. Lignes écartées : `phon` vide ; plus de SYLLABES_MAX syllabes (`nbsyll`) ;
   expression en plusieurs mots (espace) ; lettre isolée ; élision (`d'`, `l'`) ;
   abréviation à point ; sigle (aucune voyelle écrite, comme `sncf`, ou mot de
   quatre lettres au plus épelé lettre par lettre, comme `etc`) ; prononciation
   sans voyelle (`pff`).
2. Les lignes qui partagent `ortho` et `phon` (un même mot sous plusieurs
   catégories grammaticales) fusionnent : lemmes et catégories réunis, fréquences
   additionnées. Deux prononciations d'un même `ortho` restent deux lignes.
3. Formes rares écartées : la fréquence additionnée doit atteindre FREQUENCE_MIN
   occurrence par million de mots dans les sous-titres de films (`freqfilms2`) ou
   dans les livres (`freqlivres`).
4. Lexique écrit `coeur` et `oeil` : la ligature est rétablie dans `oeu` et `oei`
   (`cœur`, `œil`), où elle ne se discute pas. `coefficient` et `moelle` restent
   tels quels, comme `foetus` et `oesophage`, que la règle ne distingue pas.

Colonnes produites : `ortho`, `phon` (notation de Lexique), `lemme` et `cgram`
(valeurs distinctes séparées par des virgules) et `freqfilms2` (fréquence orale
additionnée, deux décimales), qui classe les rimes.
"""

import csv
import sys
from collections import defaultdict
from pathlib import Path

SYLLABES_MAX = 4
FREQUENCE_MIN = 1.0
VOYELLES_ECRITES = set("aeiouyàâäéèêëîïôöùûüœæ")
VOYELLES_PRONONCEES = set("aiyuoOeE°2951@§")
DESTINATION = Path(__file__).resolve().parents[2] / "src/main/resources/freestyle/lexique383-extrait.tsv"
COLONNES = ("ortho", "phon", "lemme", "cgram", "freqfilms2")


def est_sigle(ortho, nbsyll):
    lettres = [c for c in ortho if c.isalpha()]
    if not VOYELLES_ECRITES.intersection(ortho):
        return True
    return len(lettres) <= 4 and nbsyll >= len(lettres) >= 2


def ligne_admise(ligne):
    ortho, phon = ligne["ortho"], ligne["phon"]
    nbsyll = int(ligne["nbsyll"] or 0)
    return (bool(phon)
            and nbsyll <= SYLLABES_MAX
            and " " not in ortho
            and len(ortho) > 1
            and not ortho.endswith("'")
            and "." not in ortho
            and not est_sigle(ortho, nbsyll)
            and bool(VOYELLES_PRONONCEES.intersection(phon)))


def ligaturer(texte):
    return texte.replace("oeu", "œu").replace("oei", "œi")


def extraire(source):
    fusion = defaultdict(lambda: {"lemme": set(), "cgram": set(), "films": 0.0, "livres": 0.0})
    with open(source, encoding="utf-8", newline="") as fichier:
        for ligne in csv.DictReader(fichier, delimiter="\t", quoting=csv.QUOTE_NONE):
            if not ligne_admise(ligne):
                continue
            entree = fusion[(ligaturer(ligne["ortho"]), ligne["phon"])]
            entree["lemme"].add(ligaturer(ligne["lemme"]))
            if ligne["cgram"]:
                entree["cgram"].add(ligne["cgram"])
            entree["films"] += float(ligne["freqfilms2"])
            entree["livres"] += float(ligne["freqlivres"])
    for (ortho, phon), entree in sorted(fusion.items()):
        if max(entree["films"], entree["livres"]) >= FREQUENCE_MIN:
            yield (ortho, phon, ",".join(sorted(entree["lemme"])), ",".join(sorted(entree["cgram"])),
                   f"{entree['films']:.2f}")


def main(arguments):
    if len(arguments) != 2:
        sys.exit("Usage : python3 tools/rimes/extraire-lexique.py <chemin>/Lexique383.tsv")
    lignes = list(extraire(arguments[1]))
    with open(DESTINATION, "w", encoding="utf-8", newline="\n") as sortie:
        sortie.write("\t".join(COLONNES) + "\n")
        for ligne in lignes:
            sortie.write("\t".join(ligne) + "\n")
    mots = len({ligne[0] for ligne in lignes})
    print(f"{DESTINATION} : {len(lignes)} prononciations, {mots} mots, {DESTINATION.stat().st_size} octets")


if __name__ == "__main__":
    main(sys.argv)
