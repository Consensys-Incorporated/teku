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

# leanvm-sidecar proves and verifies PQ attestation proofs (--Xpq-leanvm-sidecar-binary). It must be
# the leanVM release of the lean-pq bindings in gradle/versions.gradle: a sidecar of another release
# makes proofs other clients reject. It is cross-compiled on the build platform, as compiling under
# emulation takes too long. On amd64 it is built for several CPU profiles, and
# /usr/local/bin/leanvm-sidecar runs the fastest one the CPU supports.
FROM --platform=$BUILDPLATFORM ${BASE_IMAGE_PREFIX}rust:1.99.1-bookworm AS leanvm-build
ARG TARGETARCH
ARG LEANVM_TAG=bindings-v0.16.0
ARG LEANVM_COMMIT=241b009a61ba5fde797d1213b5bf76310f0d5060
RUN git clone --quiet --depth 1 --branch "$LEANVM_TAG" https://github.com/leanEthereum/leanVM /leanvm && \
    test "$(git -C /leanvm rev-parse HEAD)" = "$LEANVM_COMMIT"
WORKDIR /leanvm
RUN <<'EOF'
set -eu
case "$TARGETARCH" in
  amd64) target=x86_64-unknown-linux-gnu; gnu=x86_64-linux-gnu ;;
  arm64) target=aarch64-unknown-linux-gnu; gnu=aarch64-linux-gnu ;;
  *) echo "Unsupported architecture: $TARGETARCH" >&2; exit 1 ;;
esac
if [ "$(rustc -vV | sed -n 's/^host: //p')" != "$target" ]; then
  apt-get -y update
  apt-get -y install --no-install-recommends "gcc-$(echo "$gnu" | tr _ -)" "libc6-dev-$TARGETARCH-cross"
  rustup target add "$target"
  upper=$(echo "$target" | tr a-z- A-Z_)
  export "CARGO_TARGET_${upper}_LINKER=$gnu-gcc" "CC_$(echo "$target" | tr - _)=$gnu-gcc" \
    "AR_$(echo "$target" | tr - _)=$gnu-ar"
fi
out=/out/lib/leanvm
mkdir -p "$out"
: > "$out/profiles.tsv"
# profile, RUSTFLAGS: in increasing order of speed, as in Lighthouse's pq-devnet-0 image
if [ "$TARGETARCH" = arm64 ]; then
  # 64 KiB pages work on kernels with 4 KiB pages too
  export JEMALLOC_SYS_WITH_LG_PAGE=16
  profiles="portable|-C target-cpu=generic -C target-feature=+aes"
else
  portable="-C target-cpu=x86-64 -C target-feature=+ssse3,+pclmulqdq"
  avx2="$portable,+avx2,+bmi2"
  avx512="$avx2,+gfni,+vpclmulqdq,+avx512f,+avx512bw,+avx512vbmi"
  profiles="portable|$portable
avx2|$avx2
avx512|$avx512"
fi
echo "$profiles" | while IFS='|' read -r profile flags; do
  CARGO_TARGET_DIR="/leanvm/target/$profile" RUSTFLAGS="$flags" \
    cargo build --quiet --locked --release --target "$target" -p leanvm-sidecar --features experimental_prover
  install -D "/leanvm/target/$profile/$target/release/leanvm-sidecar" "$out/leanvm-sidecar-$profile"
  # The CPU flags a profile needs, as /proc/cpuinfo names them. arm64 has one profile: no check.
  required=
  if [ "$TARGETARCH" = amd64 ]; then
    required=$(rustc --print cfg --target "$target" $flags | sed -n 's/^target_feature="\(.*\)"$/\1/p' | \
      sed 's/^sse3$/pni/; s/^sse4\.1$/sse4_1/; s/^sse4\.2$/sse4_2/; s/^x87$/fpu/' | tr '\n' ' ' | sed 's/ $//')
  fi
  printf '%s\tleanvm-sidecar-%s\t%s\n' "$profile" "$profile" "$required" >> "$out/profiles.tsv"
done
echo "$LEANVM_TAG $LEANVM_COMMIT" > "$out/revision"
cat "$out/profiles.tsv"
EOF

FROM ${BASE_IMAGE_PREFIX}ubuntu:24.04
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

RUN apt-get -y update && apt-get -y upgrade && apt-get -y install curl libc-bin libc6 adduser libjemalloc-dev && \
    # Clean apt cache
    apt-get clean && \
    rm -rf /var/cache/apt/archives/* && \
    rm -rf /var/lib/apt/lists/*

# leanvm-sidecar runs the fastest build the CPU supports, or the one LEANVM_CPU_PROFILE names
COPY --from=leanvm-build /out/lib/leanvm /usr/local/lib/leanvm
COPY --chmod=755 <<'EOF' /usr/local/bin/leanvm-sidecar
#!/bin/sh
set -eu
dir=/usr/local/lib/leanvm
requested=${LEANVM_CPU_PROFILE:-auto}
# The flags every CPU has
flags=$(awk -F: '$1 ~ /^(flags|Features)[ \t]*$/ { cpus++; n = split($2, f, " "); for (i = 1; i <= n; i++) count[f[i]]++ }
  END { for (flag in count) if (count[flag] == cpus) printf " %s", flag }' /proc/cpuinfo 2>/dev/null || true)
choice=$(awk -F'\t' -v flags="$flags " -v requested="$requested" '
  { profile[NR] = $1; binary[NR] = $2; required[NR] = $3 }
  END {
    for (i = NR; i >= 1; i--) {
      if (requested != "auto" && requested != profile[i]) continue
      n = split(required[i], r, " "); missing = ""
      for (j = 1; j <= n; j++) if (index(flags, " " r[j] " ") == 0) missing = missing " " r[j]
      if (missing == "") { print profile[i] " " binary[i]; exit }
      if (requested != "auto") { print "CPU profile " requested " needs" missing > "/dev/stderr"; exit }
    }
  }' "$dir/profiles.tsv")
if [ -z "$choice" ]; then
  echo "leanvm-sidecar: no build for CPU profile $requested" >&2
  exit 1
fi
echo "leanvm-sidecar CPU profile: ${choice%% *}" >&2
exec "$dir/${choice#* }" "$@"
EOF

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
