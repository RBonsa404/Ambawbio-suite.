# Keycloak pour Railway : royaume de démonstration et thème Ambawbio intégrés à l'image.
# Le royaume est dérivé de infra/keycloak/realm-ambawbio.json : les adresses locales sont remplacées par des
# variables résolues par Keycloak à l'import (${AMBAWBIO_URL_APPLICATION}, ${AMBAWBIO_KEYCLOAK_SECRET}).
FROM alpine:3.22 AS royaume
COPY infra/keycloak/realm-ambawbio.json /tmp/realm.json
RUN sed -e 's#http://localhost:4200#${AMBAWBIO_URL_APPLICATION}#g' \
        -e 's#"ambawbio-serveur-dev"#"${AMBAWBIO_KEYCLOAK_SECRET}"#' \
        /tmp/realm.json > /tmp/realm-ambawbio.json

FROM keycloak/keycloak:26.8 AS construction
ENV KC_DB=postgres KC_HEALTH_ENABLED=true
COPY infra/keycloak/themes/ambawbio /opt/keycloak/themes/ambawbio
RUN /opt/keycloak/bin/kc.sh build

FROM keycloak/keycloak:26.8
COPY --from=construction /opt/keycloak/ /opt/keycloak/
COPY --from=royaume --chown=keycloak:keycloak /tmp/realm-ambawbio.json /opt/keycloak/data/import/realm-ambawbio.json
ENV KC_HTTP_ENABLED=true KC_PROXY_HEADERS=xforwarded KC_HTTP_PORT=8080
EXPOSE 8080
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized", "--import-realm"]
