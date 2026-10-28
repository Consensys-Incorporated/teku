ARG BASE_IMAGE_PREFIX=mirror.gcr.io/library/
FROM ${BASE_IMAGE_PREFIX}eclipse-temurin:25 AS jre-build

# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base \
         --add-modules java.se \
         --add-modules java.management \
         --add-modules java.prefs \
         --add-modules jdk.management \
         --add-modules jdk.httpserver \
         --add-modules jdk.unsupported \
         --add-modules jdk.jdwp.agent \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

# Stop trusting the roots OpenJDK removed after the eMudhra intermediate CA compromise of
# October 2026 (https://openjdk.org/emudhra): emSign ECC Root CA - G3, emSign Root CA - G1 and
# G2, and AAA Certificate Services. Roots are matched by SHA-256 fingerprint, so this works
# whatever their alias, and the build fails if any is still trusted. Temurin's trust store follows
# Mozilla, which still trusts emSign Root CA - G1, so this keeps removing G1 until we choose to
# trust it again; the others become no-ops once the base image drops them.
RUN set -eu; \
    DISTRUSTED_ROOTS="86:A1:EC:BA:08:9C:4A:8D:3B:BE:27:34:C6:12:BA:34:1D:81:3E:04:3C:F9:E8:A8:62:CD:5C:57:A3:6B:BE:6B|40:F6:AF:03:46:A9:9A:A1:CD:1D:55:5A:4E:9C:CE:62:C7:F9:63:46:03:EE:40:66:15:83:3D:C8:C8:D0:03:67|1A:A0:C2:70:9E:83:1B:D6:E3:B5:12:9A:00:BA:41:F7:EE:EF:02:08:72:F1:E6:50:4B:F0:F6:C3:F2:4F:3A:F3|D7:A7:A0:FB:5D:7E:27:31:D7:71:E9:48:4E:BC:DE:F7:1D:5F:0C:3E:0A:29:48:78:2B:C8:3E:E0:EA:69:9E:F4"; \
    KEYTOOL="$JAVA_HOME/bin/keytool"; \
    STORE="-keystore /javaruntime/lib/security/cacerts -storepass changeit"; \
    $KEYTOOL -list $STORE > /dev/null; \
    $KEYTOOL -J-Duser.language=en -list -v $STORE \
      | awk -v roots="$DISTRUSTED_ROOTS" '/^Alias name: /{alias=substr($0,13)} $1 == "SHA256:" && $2 ~ "^(" roots ")$" {print alias}' \
      | while IFS= read -r alias; do echo "Removing $alias"; $KEYTOOL -delete $STORE -alias "$alias"; done; \
    if $KEYTOOL -list $STORE | grep -qE "$DISTRUSTED_ROOTS"; then echo "A distrusted root is still in cacerts" >&2; exit 1; fi

FROM ${BASE_IMAGE_PREFIX}ubuntu:24.04
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

RUN apt-get -y update && apt-get -y upgrade && apt-get -y install curl libc-bin libc6 adduser libjemalloc-dev && \
    # Clean apt cache
    apt-get clean && \
    rm -rf /var/cache/apt/archives/* && \
    rm -rf /var/lib/apt/lists/*

# Ubuntu 23.10 and above comes with an "ubuntu" user with uid 1000. We need 1000 for teku.
RUN userdel ubuntu 2>/dev/null || true && rm -rf /home/ubuntu  && \
    adduser --uid 1000 --disabled-password --gecos "" --home /opt/teku teku && \
    chown teku:teku /opt/teku && \
    chmod 0755 /opt/teku

USER teku
WORKDIR /opt/teku

# copy application (with libraries inside)
COPY --chown=teku:teku teku /opt/teku/

# Default to UTF-8 locale
ENV LANG=C.UTF-8

ENV TEKU_REST_API_INTERFACE="0.0.0.0"
ENV TEKU_VALIDATOR_API_INTERFACE="0.0.0.0"
ENV TEKU_METRICS_INTERFACE="0.0.0.0"
ENV PATH="/opt/teku/bin:${PATH}"

ENV MALLOC_CONF="background_thread:true,dirty_decay_ms:1000,muzzy_decay_ms:1000"

# List Exposed Ports
# Metrics, Rest API, LibP2P (TCP and QUIC), Discv5
EXPOSE 8008 5051 9000 9001/udp 9000/udp

# specify default command
ENTRYPOINT ["/opt/teku/bin/teku"]


# Build-time metadata as defined at http://label-schema.org
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.opencontainers.image.title="Teku" \
      org.opencontainers.image.description="Ethereum Consensus Client" \
      org.opencontainers.image.version=$VERSION \
      org.opencontainers.image.url="https://docs.teku.consensys.io/" \
      org.opencontainers.image.source="https://github.com/Consensys-Incorporated/teku.git" \
      org.opencontainers.image.revision=$VCS_REF \
      org.opencontainers.image.vendor="Consensys" \
      org.opencontainers.image.created=$BUILD_DATE \
      org.opencontainers.image.licenses="Apache 2.0"
