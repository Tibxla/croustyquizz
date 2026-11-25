import { creerTicket, texteEnvoi } from '../karaoke/commun.mjs';
import { peutReessayer } from '../karaoke/envois.mjs';
import { formaterTempsRestant } from '../karaoke/presentation.mjs';
import { delaiAppelRestant } from '../karaoke/paroles.mjs';

export function installerScene(ui, invitation) {
	const { e, bouton, contexte } = ui;
	const titreScene = e('h2', '', 'En scène'); titreScene.tabIndex = -1;
	const scene = e('div', 'karaoke__scene-manette'); scene.hidden = true;
	ui.principale.append(scene);
	let dernierCourant, descriptionTicket, ticketCourant, etiquette, minuterie, confirmerPresence, statutPresence, reessai;
	function decompter() {
		const p = ui.bloc?.courant;
		if (p?.etat !== 'APPELE' || !minuterie) return;
		const enPause = ui.bloc.etat === 'EN_PAUSE';
		const restant = delaiAppelRestant({ ...p, maintenant: contexte.maintenantServeur(), enPause });
		minuterie.textContent = formaterTempsRestant(restant);
		etiquette.classList.toggle('mon-ticket__etiquette--texte', enPause || restant === 0);
		etiquette.textContent = enPause ? 'Pause : attends la reprise.'
			: restant === 0 ? 'Le temps est écoulé, tu peux encore confirmer.' : "C'est ton tour";
	}
	function rendre(origine, titre) {
		const p = ui.bloc.courant;
		const personnel = (p?.interpretes ?? (p ? [p.interprete] : [])).some(i => i.joueurId === contexte.acteur.identifiant);
		const description = p && JSON.stringify([p.passageId, p.etat, p.numero, p.chanson ?? p.instrumentale, p.interpretes ?? p.interprete, personnel]);
		const actif = ui.document.activeElement;
		const avaitFocus = scene.contains(actif);
		const boutonCible = actif === reessai ? 'reessai' : 'confirmer';
		if (description !== descriptionTicket) {
			descriptionTicket = description;
			minuterie = confirmerPresence = statutPresence = reessai = undefined;
			scene.hidden = !p;
			scene.replaceChildren();
			if (p) {
				ticketCourant = creerTicket(ui, p, { personnel, courant: true });
				if (personnel) {
					etiquette = ticketCourant.querySelector('.mon-ticket__etiquette');
					const bas = ticketCourant.querySelector('.mon-ticket__bas');
					statutPresence = e('p'); statutPresence.setAttribute('role', 'status');
					if (p.etat === 'APPELE') {
						ticketCourant.classList.add('mon-ticket--appele');
						if (dernierCourant !== p.passageId && origine?.source === 'evenement' && origine.type === 'passage-appele') {
							ticketCourant.classList.add('mon-ticket--detachement');
						}
						minuterie = e('p', 'chiffres');
						confirmerPresence = bouton('Je suis prêt', () => {
							const cible = ui.bloc.courant;
							ui.commande({ type: 'CONFIRMER_PRESENCE', methode: 'POST', chemin: `${ui.racine()}/passages/${encodeURIComponent(cible.passageId)}/presence`,
								corps: { etapeId: cible.etapeId, numeroModification: cible.numeroModification }, mancheId: ui.bloc.mancheId,
								acteurType: 'JOUEUR', passageId: cible.passageId });
						}, 'bouton bouton--action');
						reessai = bouton('Réessayer le même envoi', () => ui.reessayer(envoiPresence()?.cle));
						bas.append(minuterie, confirmerPresence, reessai);
					} else {
						etiquette.classList.toggle('mon-ticket__etiquette--texte', ['EN_COURS', 'EN_PAUSE'].includes(p.etat));
						etiquette.textContent = {
							EN_COURS: invitation, EN_PAUSE: 'Pause : attends la reprise.',
							TERMINE: 'Passage terminé', SAUTE: 'Passage sauté',
						}[p.etat] ?? '';
					}
					bas.append(statutPresence);
				}
				scene.append(titreScene, ticketCourant);
			}
		}
		dernierCourant = p?.passageId;
		if (origine?.source === 'capture') ticketCourant?.classList.remove('mon-ticket--detachement');
		const envoi = envoiPresence();
		const texte = texteEnvoi(envoi);
		if (statutPresence && statutPresence.textContent !== texte) statutPresence.textContent = texte;
		if (confirmerPresence) {
			confirmerPresence.hidden = !ui.bloc.peutConfirmerPresence || !ui.synchronisee || ui.bloc.etat === 'EN_PAUSE';
			confirmerPresence.disabled = !ui.mutationsPermises || ['en-cours', 'accepte'].includes(envoi?.etat) || Boolean(peutReessayer(envoi));
			reessai.hidden = !ui.reessaiPossible(envoi);
		}
		if (avaitFocus && (!actif.isConnected || actif.hidden || actif.disabled || ui.document.activeElement !== actif)) {
			const cible = boutonCible === 'reessai' ? reessai : confirmerPresence;
			(actif !== titreScene && cible && !cible.hidden && !cible.disabled ? cible : p ? titreScene : titre).focus();
		}
		decompter();
	}
	function envoiPresence() {
		return ui.dernierEnvoi(e => e.commande.type === 'CONFIRMER_PRESENCE' && e.commande.passageId === ui.bloc.courant?.passageId);
	}
	return { rendre, decompter, reinitialiser() { dernierCourant = undefined; descriptionTicket = undefined; } };
}
