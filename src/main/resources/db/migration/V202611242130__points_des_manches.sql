-- Attributions déjà acquises, relues sans restaurer le déroulement de la Manche.
alter table manche_deroulement add column points jsonb not null default '[]'::jsonb;
