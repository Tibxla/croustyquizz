#!/usr/bin/env bash
# Démarre et arrête la démo de CroustyQuizz sur cette machine : rien ne tourne
# en dehors des séances. Procédure complète : docs/deploiement.md.
#
#   tools/demo.sh demarrer            base et application, joignables sur cette machine
#                                     (et sur le réseau Tailscale par `tailscale serve`, si on le souhaite)
#   tools/demo.sh demarrer --public   ouvre en plus l'adresse HTTPS publique par Tailscale Funnel
#   tools/demo.sh arreter             ferme l'adresse publique de CroustyQuizz, puis arrête les conteneurs
#   tools/demo.sh etat                conteneurs et exposition en cours
set -euo pipefail

cd "$(dirname "$0")/.."
[ -f .env ] || { echo "Créer .env depuis .env.example (docs/deploiement.md, « Première installation »)." >&2; exit 1; }
# Fins de ligne Windows tolérées (fichier édité sous Windows).
set -a; . <(tr -d '\r' < .env); set +a

compose=(docker compose -f compose.prod.yaml)
port_local="${CROUSTYQUIZZ_PORT_LOCAL:-8080}"
port_public="${CROUSTYQUIZZ_PORT_PUBLIC:-443}"

fermer_public() {
	# Ne ferme que l'entrée HTTPS de CroustyQuizz : les autres services de
	# `tailscale serve` restent en place (jamais de `tailscale funnel reset`).
	if command -v tailscale >/dev/null; then
		tailscale funnel --https="$port_public" off >/dev/null 2>&1 || true
	fi
}

attendre_application() {
	for _ in $(seq 1 60); do
		if curl -fsS "http://127.0.0.1:$port_local/" >/dev/null 2>&1; then return 0; fi
		sleep 2
	done
	echo "L'application ne répond pas après deux minutes : ${compose[*]} logs app" >&2
	return 1
}

case "${1:-}" in
	demarrer)
		if [ "${2:-}" = "--public" ] && [ "${CROUSTYQUIZZ_PUBLIC:-}" != "autorise" ]; then
			cat >&2 <<-EOF
			Exposition publique refusée : CROUSTYQUIZZ_PUBLIC=autorise manque dans .env.
			Ne l'ajouter qu'une fois les conditions de l'issue #4 remplies :
			Console protégée (#5), routes /api/test/ coupées en profil prod, limite de requêtes.
			EOF
			exit 1
		fi
		"${compose[@]}" pull --quiet --ignore-pull-failures
		"${compose[@]}" up -d
		attendre_application
		echo "CroustyQuizz répond sur http://127.0.0.1:$port_local/"
		if [ "${2:-}" = "--public" ]; then
			tailscale funnel --bg --https="$port_public" "$port_local"
			echo "Adresse publique ouverte. À la fin de la séance : tools/demo.sh arreter"
		fi
		;;
	arreter)
		fermer_public
		"${compose[@]}" stop
		echo "CroustyQuizz est arrêté et son adresse publique fermée. Les données restent dans le volume."
		;;
	etat)
		"${compose[@]}" ps
		if command -v tailscale >/dev/null; then tailscale funnel status; fi
		;;
	*)
		sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
		exit 1
		;;
esac
