#!/usr/bin/env bash
# Copie dans .agents/skills/ les skills de mattpocock/skills et d'Impeccable,
# pour que Codex et Antigravity CLI les trouvent dès le clone du dépôt.
# Claude Code ne lit pas ce dossier : il reçoit les mêmes skills en plugins
# par .claude/settings.json. Codex ne charge pas deux fois les mêmes skills :
# .codex/config.toml désactive les plugins globaux dans ce dépôt.
#
# Usage, depuis n'importe où dans le dépôt (Linux, macOS, Git Bash) :
#   tools/mettre-a-jour-skills.sh
# Pour monter de version, passer un autre commit :
#   MATTPOCOCK_REF=<sha> IMPECCABLE_REF=<sha> tools/mettre-a-jour-skills.sh
# puis relire le diff et committer .agents/.
set -euo pipefail

MATTPOCOCK_REF="${MATTPOCOCK_REF:-4ae2317ddb22b046e26455e4f9c9b3aaaf62212d}"
IMPECCABLE_REF="${IMPECCABLE_REF:-ebe9c265f416f7f9b83ed424ed5b9bd0d2684536}"

racine=$(git rev-parse --show-toplevel)
travail="$racine/target/skills-sources"
cible="$racine/.agents/skills"
licences="$racine/.agents/licences"
manifeste="$cible/.copies"

recuperer() { # <url> <commit> <dossier>
	rm -rf "$3"
	git init -q "$3"
	git -C "$3" fetch -q --depth 1 "$1" "$2"
	git -C "$3" checkout -q FETCH_HEAD
}

mkdir -p "$travail" "$cible" "$licences"
recuperer https://github.com/mattpocock/skills.git "$MATTPOCOCK_REF" "$travail/mattpocock"
recuperer https://github.com/pbakaus/impeccable.git "$IMPECCABLE_REF" "$travail/impeccable"

# Retirer seulement les copies précédentes : un skill propre au projet reste en place.
# Le manifeste entier est validé avant toute suppression : un nom simple par ligne,
# jamais un chemin, pour que rm ne sorte jamais de .agents/skills/.
if [ -f "$manifeste" ]; then
	while IFS= read -r nom; do
		case "$nom" in
			'' | . | .. | */* | *[!A-Za-z0-9._-]*)
				echo "Entrée refusée dans $manifeste : « $nom ». Corriger le manifeste à la main." >&2
				exit 1 ;;
		esac
	done < "$manifeste"
	while IFS= read -r nom; do
		rm -rf "${cible:?}/${nom:?}"
	done < "$manifeste"
fi
: > "$manifeste"

# Les skills de mattpocock sont ceux que déclare son plugin.
grep -o '"\./skills/[^"]*"' "$travail/mattpocock/.claude-plugin/plugin.json" | tr -d '"' |
	while IFS= read -r chemin; do
		nom=$(basename "$chemin")
		cp -R "$travail/mattpocock/$chemin" "$cible/$nom"
		echo "$nom" >> "$manifeste"
	done

# Impeccable publie une version prête pour le dossier .agents/.
cp -R "$travail/impeccable/.agents/skills/impeccable" "$cible/impeccable"
echo impeccable >> "$manifeste"

cp "$travail/mattpocock/LICENSE" "$licences/mattpocock-skills-MIT.txt"
cp "$travail/impeccable/LICENSE" "$licences/impeccable-Apache-2.0.txt"
cp "$travail/impeccable/NOTICE.md" "$licences/impeccable-NOTICE.md"
# licences/modern-screenshot-MIT.txt est versionné à la main : Impeccable embarque
# scripts/modern-screenshot.umd.js (MIT, qq15725/modern-screenshot) sans sa licence.

cat > "$racine/.agents/PROVENANCE.md" <<EOF
# Provenance des skills de .agents/skills/

Copies générées par \`tools/mettre-a-jour-skills.sh\`. Ne pas les modifier à la main : relancer le script.

| Source | Commit | Licence |
|---|---|---|
| [mattpocock/skills](https://github.com/mattpocock/skills) | \`$MATTPOCOCK_REF\` | MIT, \`licences/mattpocock-skills-MIT.txt\` |
| [pbakaus/impeccable](https://github.com/pbakaus/impeccable) | \`$IMPECCABLE_REF\` | Apache 2.0, \`licences/impeccable-Apache-2.0.txt\` et \`licences/impeccable-NOTICE.md\` |
| [qq15725/modern-screenshot](https://github.com/qq15725/modern-screenshot), embarqué par Impeccable | (dans la copie d'Impeccable) | MIT, \`licences/modern-screenshot-MIT.txt\` |

Skills copiés : la liste de \`skills/.copies\`.
EOF

echo "$(wc -l < "$manifeste" | tr -d ' ') skills copiés dans .agents/skills/. Relire git status puis committer .agents/."
