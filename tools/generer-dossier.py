#!/usr/bin/env python3
"""Compiler le dossier Markdown en HTML puis en PDF A4 avec Pandoc et Chrome."""

from html import escape
from pathlib import Path
import os
import shutil
import subprocess
import tempfile


def main():
    racine = Path(__file__).resolve().parents[1]
    dossier = racine / "doc" / "rendu-1"
    pandoc = shutil.which("pandoc")
    chrome = next(
        (chemin for nom in (os.environ.get("CHROME_BIN"), "google-chrome", "chromium", "chromium-browser")
         if nom and (chemin := shutil.which(nom))),
        None,
    )
    if not pandoc or not chrome:
        raise SystemExit("Installer Pandoc et Chrome/Chromium (ou définir CHROME_BIN).")

    pages = (dossier / "dossier-lancement.md").read_text(encoding="utf-8").split("<!-- page -->")
    if len(pages) != 4:
        raise SystemExit("Le dossier doit contenir quatre sections séparées par <!-- page -->.")
    sections = []
    for numero, page in enumerate(pages, start=1):
        contenu = subprocess.run(
            [pandoc, "--from=gfm", "--to=html5"],
            input=page, text=True, encoding="utf-8", capture_output=True, check=True,
        ).stdout
        sections.append(
            '<section class="page"><div class="running">'
            '<span>CroustyQuizz · M1 MIAGE</span><span>Rendu 1 · Dossier de lancement</span>'
            f'</div><main>{contenu}</main><footer>'
            f'<span>CroustyQuizz · 10 novembre 2026</span><span>{numero} / 4</span>'
            '</footer></section>'
        )
    css = (racine / "tools" / "dossier-lancement.css").read_text(encoding="utf-8")
    sortie = racine / "target" / "dossier-lancement"
    sortie.mkdir(parents=True, exist_ok=True)
    html = sortie / "dossier-lancement.html"
    html.write_text(
        '<!doctype html><html lang="fr"><head><meta charset="utf-8">'
        f'<title>{escape("CroustyQuizz · Dossier de lancement")}</title>'
        f'<style>{css}</style></head><body>{"".join(sections)}</body></html>',
        encoding="utf-8",
    )
    pdf = dossier / "dossier-lancement.pdf"
    with tempfile.TemporaryDirectory(prefix="croustyquizz-pdf-") as profil:
        resultat = subprocess.run(
            [chrome, "--headless", "--no-sandbox", "--disable-gpu", "--disable-extensions",
             "--no-pdf-header-footer", f"--user-data-dir={profil}",
             f"--print-to-pdf={pdf}", html.as_uri()],
            text=True, capture_output=True, timeout=60,
        )
        if resultat.returncode or not pdf.is_file():
            raise SystemExit(f"Échec de génération du PDF :\n{resultat.stderr}")
    print(f"HTML : {html.relative_to(racine)}")
    print(f"PDF  : {pdf.relative_to(racine)}")
    print("Vérifier que le PDF comporte quatre pages sans débordement après chaque modification.")


if __name__ == "__main__":
    main()
