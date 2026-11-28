create table invitation_animateur (
	empreinte_cle bytea primary key check (octet_length(empreinte_cle) = 32),
	lieu_id       uuid not null references lieu (id),
	email         varchar(254) not null,
	expire_le     timestamptz not null
);
