#!/usr/bin/env python3
"""Produire des badges et une synthèse à partir des rapports JaCoCo et PMD."""

from html import escape
from pathlib import Path
import json
import os
import subprocess
import xml.etree.ElementTree as ET


def couverture(rapport, type_compteur):
    compteur = rapport.find(f"counter[@type='{type_compteur}']")
    if compteur is None:
        raise ValueError(f"Compteur JaCoCo manquant : {type_compteur}")
    couverts = int(compteur.attrib["covered"])
    manques = int(compteur.attrib["missed"])
    total = couverts + manques
    return {"couverts": couverts, "total": total,
            "pourcentage": round(100 * couverts / total, 1) if total else None}


def badge(libelle, valeur, couleur):
    gauche, droite = len(libelle) * 7 + 14, len(valeur) * 7 + 14
    largeur = gauche + droite
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{largeur}" height="20" '
        f'role="img" aria-label="{escape(libelle)} : {escape(valeur)}">'
        f'<title>{escape(libelle)} : {escape(valeur)}</title>'
        f'<rect width="{gauche}" height="20" fill="#555"/>'
        f'<rect x="{gauche}" width="{droite}" height="20" fill="{couleur}"/>'
        '<g fill="#fff" text-anchor="middle" font-family="Verdana,Arial,sans-serif" font-size="11">'
        f'<text x="{gauche / 2}" y="14">{escape(libelle)}</text>'
        f'<text x="{gauche + droite / 2}" y="14">{escape(valeur)}</text>'
        '</g></svg>\n'
    )


def main():
    racine = Path(__file__).resolve().parents[1]
    jacoco = ET.parse(racine / "target/site/jacoco/jacoco.xml").getroot()
    pmd = ET.parse(racine / "target/pmd.xml").getroot()
    if pmd.findall(".//{*}error") or pmd.findall(".//{*}configerror"):
        raise ValueError("PMD a rencontré une erreur : aucun badge de qualité ne peut être publié.")
    lignes, branches = couverture(jacoco, "LINE"), couverture(jacoco, "BRANCH")
    if lignes["total"] == 0:
        raise ValueError("Aucune ligne analysée par JaCoCo.")
    alertes = len(pmd.findall(".//{*}violation"))
    commit = os.environ.get("GITHUB_SHA") or subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=racine, text=True,
    ).strip()
    execution = os.environ.get("GITHUB_RUN_ID")
    mesures = {"commit": commit, "execution_ci": execution,
               "lignes": lignes, "branches": branches, "alertes_pmd": alertes}
    sortie = racine / "target/qualite"
    sortie.mkdir(parents=True, exist_ok=True)
    (sortie / "mesures.json").write_text(
        json.dumps(mesures, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    pourcentage = lignes["pourcentage"]
    couleur = "#4c1" if pourcentage >= 80 else "#dfb317" if pourcentage >= 60 else "#e05d44"
    (sortie / "couverture.svg").write_text(
        badge("couverture lignes", f"{pourcentage:.1f} %", couleur), encoding="utf-8",
    )
    (sortie / "qualite.svg").write_text(
        badge("PMD", f"{alertes} alerte(s)", "#4c1" if alertes == 0 else "#e05d44"), encoding="utf-8",
    )
    resume = (
        f"Couverture : {pourcentage:.1f} % des lignes ({lignes['couverts']}/{lignes['total']}).\n"
        f"Branches : {branches['pourcentage']} % ({branches['couverts']}/{branches['total']}).\n"
        f"PMD : {alertes} alerte(s), règles Error Prone et Best Practices, code de production.\n"
        f"Commit analysé : {commit}.\n"
    )
    print(resume)
    if chemin := os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(chemin).open("a", encoding="utf-8") as fichier:
            fichier.write("### Mesures de couverture et de qualité\n\n" + resume.replace("\n", "  \n"))


if __name__ == "__main__":
    main()
