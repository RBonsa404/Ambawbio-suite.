# Royaume Keycloak de démonstration

`realm-ambawbio.json` est importé au démarrage de `docker-compose.dev.yml`. **Développement uniquement.**

| Utilisateur | Entreprise (`tenant_id`) | Rôles | MFA |
|---|---|---|---|
| awa | Quincaillerie Wend-Panga | caissier (établissement Zogona uniquement) | non |
| moussa | Quincaillerie Wend-Panga | gerant, administrateur | **oui** |
| mariam | Quincaillerie Wend-Panga | comptable | **oui** |
| issouf | Quincaillerie Wend-Panga | magasinier | non |
| boukary | Quincaillerie Wend-Panga | commercial | non |
| aminata | Quincaillerie Wend-Panga | gerant (gère produits, clients, import) | non |
| salimata | Pharmacie du Progrès (démo) | administrateur | **oui** |
| editeur | — (plateforme) | admin-plateforme | non |

Thème de connexion aux couleurs de la marque : `themes/ambawbio` (écrans A-03, W-24).

Mot de passe de tous les comptes : `demo-ambawbio`. Les utilisateurs soumis à la MFA configurent une application d'authentification (FreeOTP, Google Authenticator…) à la première connexion.

- Client public `ambawbio-web` (code d'autorisation + PKCE S256) : navigateur (`http://localhost:4200`) et Android (`https://localhost`) ; mappeur `tenant_id` (attribut utilisateur → revendication).
- Client confidentiel `ambawbio-serveur` (compte de service, secret `ambawbio-serveur-dev`) : création des comptes et alignement des rôles par le serveur.
- Flux « navigateur ambawbio » : mot de passe, puis code à usage unique si l'utilisateur a le rôle `mfa-obligatoire` (composé dans `comptable`, `administrateur`, `dirigeant`).
- Courriels (définition du mot de passe, vérification) envoyés à Mailpit : http://localhost:8025.

Les identifiants des comptes sont fixes et correspondent aux données de démonstration créées par le serveur en profil `dev` (`DonneesDemonstration.java`).
