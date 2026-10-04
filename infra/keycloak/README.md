# Royaume Keycloak de démonstration

`realm-ambawbio.json` est importé au démarrage de `docker-compose.dev.yml`. **Développement uniquement.**

| Utilisateur | Rôles | Mot de passe |
|---|---|---|
| awa | caissier | demo-ambawbio |
| moussa | gerant, administrateur | demo-ambawbio |
| mariam | comptable | demo-ambawbio |
| issouf | magasinier | demo-ambawbio |
| boukary | commercial | demo-ambawbio |
| editeur | admin-plateforme | demo-ambawbio |

Client public `ambawbio-web` (code d'autorisation + PKCE S256), utilisé par le navigateur (`http://localhost:4200`) et l'application Android Capacitor (`https://localhost`).
Les rôles, permissions fines, MFA du comptable et thème aux couleurs de la marque arrivent aux LOT 1 et LOT 3.
