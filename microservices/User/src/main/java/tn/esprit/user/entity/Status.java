package tn.esprit.user.entity;

public enum Status {
 ACTIVE, EN_ATTENTE, NON_ACTIVE,
 /** Compte gelé (ex. vol de session) — connexion refusée, jetons invalidés. */
 FROZEN
}
