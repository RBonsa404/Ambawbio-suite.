# PostgreSQL 18 pour Railway : mêmes rôles qu'en local, mots de passe fournis par les variables du service.
# Les comptes sont créés au premier démarrage puis resynchronisés avec les variables à chaque démarrage (D-40).
FROM postgres:18-alpine
COPY infra/railway/postgres/initialiser.sh /docker-entrypoint-initdb.d/10-initialiser.sh
COPY infra/railway/postgres/demarrer.sh /usr/local/bin/demarrer.sh
RUN chmod +x /usr/local/bin/demarrer.sh
ENTRYPOINT ["demarrer.sh"]
CMD []
