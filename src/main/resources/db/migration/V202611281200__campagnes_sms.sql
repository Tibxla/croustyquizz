create table campagne_sms (
	id               uuid primary key,
	lieu_id          uuid not null references lieu (id),
	cle_idempotence  uuid not null,
	texte            text not null,
	demandee_le      timestamptz not null,
	envoi_prevu_le   timestamptz not null,
	etat             text not null check (etat in ('PROGRAMMEE', 'EN_COURS', 'TERMINEE')),
	unique (lieu_id, cle_idempotence)
);

create index campagne_sms_lieu on campagne_sms (lieu_id, demandee_le desc, id desc);
create index campagne_sms_a_traiter on campagne_sms (envoi_prevu_le, id) where etat <> 'TERMINEE';

-- Une ligne sans résultat réserve un Contact du public retenu à la création.
-- À l’envoi, elle reçoit le résultat ou disparaît si le Contact n’est plus éligible.
create table campagne_sms_envoi (
	campagne_id             uuid not null references campagne_sms (id) on delete cascade,
	contact_id              uuid not null,
	statut                  text,
	envoye_le               timestamptz,
	motif                   text,
	identifiant_prestataire text,
	primary key (campagne_id, contact_id),
	check (
		(statut is null and envoye_le is null and motif is null and identifiant_prestataire is null)
		or (statut is not null and envoye_le is not null and (
			(statut = 'ENVOYE' and motif is null and identifiant_prestataire is not null)
			or (statut = 'ECHOUE' and motif is not null and identifiant_prestataire is null)
		))
	)
);

create index campagne_sms_envoi_en_attente on campagne_sms_envoi (campagne_id, contact_id) where statut is null;
