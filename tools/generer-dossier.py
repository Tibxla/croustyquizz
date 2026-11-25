#!/usr/bin/env python3
"""Compiler un dossier de rendu Markdown en HTML puis en PDF A4 avec Pandoc et Chrome.

Usage, depuis la racine du dépôt :
    python3 tools/generer-dossier.py            # Rendu 1, quatre pages fixes
    python3 tools/generer-dossier.py rendu-2    # Rendu 2, sans limite de pages
    python3 tools/generer-dossier.py rendu-3    # Rendu 3, sans limite de pages

Le Rendu 1 garde sa mise en page d'origine : quatre feuilles séparées par
<!-- page -->, chacune avec son en-tête et son pied de page. Le Rendu 2 s'écoule
sur autant de pages qu'il faut, comme le Rendu 3 ; <!-- page --> y force seulement un saut de page,
et l'en-tête, le pied et la numérotation viennent des marges de @page
(tools/dossier-continu.css), que Chrome répète sur chaque page imprimée.
"""

from html import escape
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile

DOSSIERS = {
    "rendu-1": {
        "source": "doc/rendu-1/dossier-lancement.md",
        "pdf": "doc/rendu-1/dossier-lancement.pdf",
        "sortie": "target/dossier-lancement",
        "titre": "CroustyQuizz · Dossier de lancement",
        "en_tete": "Rendu 1 · Dossier de lancement",
        "date": "10 novembre 2026",
        "pages": 4,
    },
    "rendu-2": {
        "source": "doc/rendu-2/dossier-v0.1.0.md",
        "pdf": "doc/rendu-2/dossier-v0.1.0.pdf",
        "sortie": "target/dossier-v0.1.0",
        "titre": "CroustyQuizz · Version v0.1.0",
        "en_tete": "Rendu 2 · Version intermédiaire v0.1.0",
        "date": "18 novembre 2026",
        "pages": None,
    },
    "rendu-3": {
        "source": "doc/rendu-3/dossier-v0.2.0.md",
        "pdf": "doc/rendu-3/dossier-v0.2.0.pdf",
        "sortie": "target/dossier-v0.2.0",
        "titre": "CroustyQuizz · Version v0.2.0",
        "en_tete": "Rendu 3 · Version intermédiaire v0.2.0",
        "date": "25 novembre 2026",
        "pages": None,
    },
}


def en_html(pandoc, markdown):
    return subprocess.run(
        [pandoc, "--from=gfm", "--to=html5"],
        input=markdown, text=True, encoding="utf-8", capture_output=True, check=True,
    ).stdout


def pages_fixes(pandoc, texte, dossier):
    """Une feuille A4 par section, avec en-tête et pied dessinés dans la page."""
    pages = texte.split("<!-- page -->")
    attendu = dossier["pages"]
    if len(pages) != attendu:
        raise SystemExit(f"Le dossier doit contenir {attendu} sections séparées par <!-- page -->.")
    sections = []
    for numero, page in enumerate(pages, start=1):
        sections.append(
            '<section class="page"><div class="running">'
            f'<span>CroustyQuizz · M1 MIAGE</span><span>{dossier["en_tete"]}</span>'
            f'</div><main>{en_html(pandoc, page)}</main><footer>'
            f'<span>CroustyQuizz · {dossier["date"]}</span><span>{numero} / {attendu}</span>'
            '</footer></section>'
        )
    return "".join(sections), ""


def texte_css(valeur):
    return '"' + valeur.replace("\\", "\\\\").replace('"', '\\"') + '"'


def pages_continues(pandoc, texte, dossier, racine):
    """Un flux unique ; en-tête, pied et numéros dans les marges de chaque page."""
    parties = "".join(
        f'<section class="partie">{en_html(pandoc, partie)}</section>'
        for partie in texte.split("<!-- page -->")
    )
    marges = (
        "@page {"
        f" @top-left {{ content: {texte_css('CroustyQuizz · M1 MIAGE')}; }}"
        f" @top-right {{ content: {texte_css(dossier['en_tete'])}; }}"
        f" @bottom-left {{ content: {texte_css('CroustyQuizz · ' + dossier['date'])}; }}"
        ' @bottom-right { content: counter(page) " / " counter(pages); }'
        " }"
    )
    css = (racine / "tools" / "dossier-continu.css").read_text(encoding="utf-8") + marges
    return f'<main class="continu">{parties}</main>', css


def main():
    racine = Path(__file__).resolve().parents[1]
    nom = sys.argv[1] if len(sys.argv) > 1 else "rendu-1"
    if nom not in DOSSIERS:
        raise SystemExit(f"Dossier inconnu : {nom}. Choisir parmi : {', '.join(DOSSIERS)}.")
    dossier = DOSSIERS[nom]
    pandoc = shutil.which("pandoc")
    chrome = next(
        (chemin for nom_chrome in (os.environ.get("CHROME_BIN"), "google-chrome", "chromium", "chromium-browser")
         if nom_chrome and (chemin := shutil.which(nom_chrome))),
        None,
    )
    if not pandoc or not chrome:
        raise SystemExit("Installer Pandoc et Chrome/Chromium (ou définir CHROME_BIN).")

    texte = (racine / dossier["source"]).read_text(encoding="utf-8")
    if dossier["pages"]:
        corps, css_en_plus = pages_fixes(pandoc, texte, dossier)
    else:
        corps, css_en_plus = pages_continues(pandoc, texte, dossier, racine)
    css = (racine / "tools" / "dossier-lancement.css").read_text(encoding="utf-8") + css_en_plus
    sortie = racine / dossier["sortie"]
    sortie.mkdir(parents=True, exist_ok=True)
    html = sortie / (Path(dossier["source"]).stem + ".html")
    html.write_text(
        '<!doctype html><html lang="fr"><head><meta charset="utf-8">'
        f'<title>{escape(dossier["titre"])}</title>'
        f'<style>{css}</style></head><body>{corps}</body></html>',
        encoding="utf-8",
    )
    pdf = racine / dossier["pdf"]
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
    if dossier["pages"]:
        print(f"Vérifier que le PDF comporte {dossier['pages']} pages sans débordement après chaque modification.")
    else:
        print("Vérifier la mise en page du PDF (tableaux, sauts de page) après chaque modification.")


if __name__ == "__main__":
    main()
