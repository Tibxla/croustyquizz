alter table soiree
    add column buzz_envoi_id uuid,
    add column buzz_joueur_id uuid,
    add column buzz_pseudo varchar(30),
    add column buzz_recu_le timestamptz,
    add column buzz_rang bigint check (buzz_rang > 0),
    add constraint soiree_dernier_buzz_complet
        check (num_nonnulls(buzz_envoi_id, buzz_joueur_id, buzz_pseudo, buzz_recu_le, buzz_rang) in (0, 5));
