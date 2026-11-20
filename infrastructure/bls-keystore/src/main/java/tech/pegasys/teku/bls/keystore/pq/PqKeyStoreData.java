/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.bls.keystore.pq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;
import tech.pegasys.teku.bls.keystore.model.Crypto;

/**
 * A PQ validator keystore: a validator's leanXMSS seed and VRF seed, encrypted with the EIP-2335
 * {@code crypto} scheme, next to the public values derived from them. No standard exists for PQ
 * keystores yet, so this is Teku's own format; {@code format} and {@code version} name it so it can
 * change. See {@code docs/pq/pq-keystore.md}.
 *
 * <p>Version 1:
 *
 * <pre>{@code
 * {
 *   "format": "teku-pq-validator-keystore",
 *   "version": 1,
 *   "xmss_pubkey": "<32 bytes, hex>",
 *   "vrf_root": "<32 bytes, hex>",
 *   "bls_pubkey": "<48 bytes, hex; optional>",
 *   "crypto": { <EIP-2335 crypto; the secret is xmss_seed ‖ vrf_seed, 64 bytes> },
 *   "uuid": "<random UUID>"
 * }
 * }</pre>
 *
 * <p>{@code bls_pubkey} names the BLS validator the PQ keys belong to, for validators that existed
 * before the PQ fork. Like {@code pubkey} in EIP-2335, the public fields are not authenticated:
 * loaders must check them against the values derived from the decrypted seeds.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PqKeyStoreData {
  public static final String FORMAT = "teku-pq-validator-keystore";
  public static final int VERSION = 1;
  public static final int SEED_SIZE = 32;
  public static final int SECRET_SIZE = 2 * SEED_SIZE;
  public static final int BLS_PUBKEY_SIZE = 48;

  private final String format;
  private final Integer version;
  // Bytes rather than Bytes32, whose deserializer left-pads short values, so validate() sees them
  private final Bytes xmssPubkey;
  private final Bytes vrfRoot;
  private final Bytes blsPubkey;
  private final Crypto crypto;
  private final UUID uuid;

  @JsonCreator
  public PqKeyStoreData(
      @JsonProperty(value = "format", required = true) final String format,
      @JsonProperty(value = "version", required = true) final Integer version,
      @JsonProperty(value = "xmss_pubkey", required = true) final Bytes xmssPubkey,
      @JsonProperty(value = "vrf_root", required = true) final Bytes vrfRoot,
      @JsonProperty(value = "bls_pubkey") final Bytes blsPubkey,
      @JsonProperty(value = "crypto", required = true) final Crypto crypto,
      @JsonProperty(value = "uuid") final UUID uuid) {
    this.format = format;
    this.version = version;
    this.xmssPubkey = xmssPubkey;
    this.vrfRoot = vrfRoot;
    this.blsPubkey = blsPubkey;
    this.crypto = crypto;
    this.uuid = uuid;
  }

  public PqKeyStoreData(
      final Bytes32 xmssPubkey,
      final Bytes32 vrfRoot,
      final Optional<Bytes> blsPubkey,
      final Crypto crypto) {
    this(FORMAT, VERSION, xmssPubkey, vrfRoot, blsPubkey.orElse(null), crypto, UUID.randomUUID());
  }

  @JsonProperty("format")
  public String getFormat() {
    return format;
  }

  @JsonProperty("version")
  public Integer getVersion() {
    return version;
  }

  @JsonProperty("xmss_pubkey")
  public Bytes32 getXmssPubkey() {
    return Bytes32.wrap(xmssPubkey);
  }

  @JsonProperty("vrf_root")
  public Bytes32 getVrfRoot() {
    return Bytes32.wrap(vrfRoot);
  }

  @JsonProperty("bls_pubkey")
  public Bytes getBlsPubkeyOrNull() {
    return blsPubkey;
  }

  @JsonIgnore
  public Optional<Bytes> getBlsPubkey() {
    return Optional.ofNullable(blsPubkey);
  }

  @JsonProperty("crypto")
  public Crypto getCrypto() {
    return crypto;
  }

  @JsonProperty("uuid")
  public UUID getUuid() {
    return uuid;
  }

  public void validate() throws KeyStoreValidationException {
    if (!FORMAT.equals(format)) {
      throw new KeyStoreValidationException(
          String.format("Not a PQ validator keystore: format was %s, expected %s", format, FORMAT));
    }
    if (!Objects.equals(version, VERSION)) {
      throw new KeyStoreValidationException(
          String.format("The PQ validator keystore version %s is not supported", version));
    }
    checkSize("xmss_pubkey", xmssPubkey, Bytes32.SIZE);
    checkSize("vrf_root", vrfRoot, Bytes32.SIZE);
    if (blsPubkey != null) {
      checkSize("bls_pubkey", blsPubkey, BLS_PUBKEY_SIZE);
    }
    crypto.validate();
  }

  private static void checkSize(final String field, final Bytes value, final int size) {
    if (value.size() != size) {
      throw new KeyStoreValidationException(
          String.format("Expected %s bytes for %s but got %s", size, field, value.size()));
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("format", format)
        .add("version", version)
        .add("xmssPubkey", xmssPubkey)
        .add("vrfRoot", vrfRoot)
        .add("blsPubkey", blsPubkey)
        .add("uuid", uuid)
        .toString();
  }
}
