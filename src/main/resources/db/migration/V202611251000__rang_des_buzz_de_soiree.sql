-- Le rang des Buzz du socle se compte à part : dernier_buzz peut reprendre un Buzz de Blind test (ADR 0009).
alter table soiree
    add column buzz_rang_soiree bigint not null default 0 check (buzz_rang_soiree >= 0);

-- Avant le Blind test, le dernier Buzz retenu est toujours un Buzz du socle.
update soiree set buzz_rang_soiree = buzz_rang where buzz_rang is not null;
