/**
 * Éléments partagés par tous les modules : objets valeur (Montant, Ifu), identifiants UUID v7,
 * entité de base, contexte multi-tenant, erreurs ProblemDetail et pagination.
 * Module ouvert : ses types sont utilisables par tous les autres modules.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Partagé", type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package bf.ambawbio.shared;
