import { creerInterface, creerTicket, texteAutresPassages } from './commun.mjs';

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte), { e } = ui;
	const invitation = e('h2', 'karaoke__invitation');
	const prochain = e('div', 'karaoke__prochain'); ui.principale.append(invitation, prochain);
	const titre = e('h2', '', 'Ensuite'), file = e('ol', 'rouleau'), autres = e('p');
	ui.laterale.append(titre, file, autres);
	ui.surRendu = () => {
		if (!ui.bloc) return;
		const passages = ui.bloc.prochainsPassages;
		invitation.textContent = passages.length ? 'Prépare-toi.' : 'Choisis ta Chanson sur ta Manette.';
		prochain.hidden = !passages.length;
		prochain.replaceChildren(...(passages.length ? [creerTicket(ui, passages[0])] : []));
		// Le premier Passage est déjà en grand : « Ensuite » montre seulement les suivants.
		const suivants = passages.slice(1);
		file.hidden = !suivants.length; titre.hidden = !suivants.length && !ui.bloc.nombreAutresPassages;
		file.replaceChildren(...suivants.map(p => { const li = e('li'); li.append(creerTicket(ui, p)); return li; }));
		autres.hidden = !ui.bloc.nombreAutresPassages;
		autres.textContent = texteAutresPassages(ui.bloc.nombreAutresPassages);
	};
	return { afficher: ui.afficher, demonter: ui.demonter };
}
