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

package tech.pegasys.teku.bls;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.impl.PublicKey;

public final class BLSPublicKey {

  public static final int SSZ_BLS_PUBKEY_SIZE = BLSConstants.BLS_PUBKEY_SIZE;

  /**
   * Creates an empty public key (all zero bytes).
   *
   * @return the empty public key as per the Eth2 spec
   */
  public static BLSPublicKey empty() {
    return BLSPublicKey.fromBytesCompressed(Bytes48.ZERO);
  }

  public static BLSPublicKey fromHexString(final String hexString) {
    return BLSPublicKey.fromBytesCompressed(Bytes48.fromHexString(hexString));
  }

  /**
   * Aggregates list of PublicKeys, returns the public key that corresponds to G1 point at infinity
   * if list is empty, or if any of the public keys is infinity or not a G1 group member.
   *
   * @param publicKeys The list of public keys to aggregate
   * @return PublicKey The public key
   */
  public static BLSPublicKey aggregate(final List<BLSPublicKey> publicKeys) {
    return new BLSPublicKey(
        BLS.getBlsImpl()
            .aggregatePublicKeys(publicKeys.stream().map(BLSPublicKey::getPublicKey).toList()));
  }

  public static BLSPublicKey fromSSZBytes(final Bytes bytes) {
    checkArgument(
        bytes.size() == SSZ_BLS_PUBKEY_SIZE,
        "Expected " + SSZ_BLS_PUBKEY_SIZE + " bytes but received %s.",
        bytes.size());
    return SSZ.decode(
        bytes,
        reader -> new BLSPublicKey(Bytes48.wrap(reader.readFixedBytes(SSZ_BLS_PUBKEY_SIZE))));
  }

  /**
   * Create a PublicKey from 48-byte compressed format
   *
   * @param bytes 48 bytes to read the public key from
   * @return a public key. Note that implementation may lazily evaluate passed bytes so the method
   *     may not immediately fail if the supplied bytes are invalid. Use {@link
   *     BLSPublicKey#fromBytesCompressedValidate(Bytes48)} to validate immediately
   * @throws IllegalArgumentException If the supplied bytes are not a valid public key However if
   *     implementing class lazily parses bytes the exception might not be thrown on invalid input
   *     but throw on later usage. Use {@link BLSPublicKey#fromBytesCompressedValidate(Bytes48)} if
   *     need to immediately ensure input validity
   */
  public static BLSPublicKey fromBytesCompressed(final Bytes48 bytes)
      throws IllegalArgumentException {
    return new BLSPublicKey(bytes);
  }

  public static BLSPublicKey fromBytesCompressedValidate(final Bytes48 bytes)
      throws IllegalArgumentException {
    BLSPublicKey ret = new BLSPublicKey(bytes);
    ret.getPublicKey().forceValidation();
    return ret;
  }

  // Sometimes we are dealing with random, invalid pubkey points, e.g. when testing.
  // Let's only interpret the raw data into a point when necessary to do so.
  // And vice versa while aggregating we are dealing with points only so let's
  // convert point to raw data when necessary to do so.
  private final Supplier<PublicKey> publicKey;
  private final Supplier<Bytes48> bytesCompressed;

  /**
   * Construct from a BLSSecretKey object.
   *
   * @param secretKey A BLSSecretKey
   */
  public BLSPublicKey(final BLSSecretKey secretKey) {
    this(secretKey.getSecretKey().derivePublicKey());
  }

  /**
   * Construct from an implementation-specific PublicKey object.
   *
   * @param publicKey An implementation-specific PublicKey
   */
  BLSPublicKey(final PublicKey publicKey) {
    this(() -> publicKey, Suppliers.memoize(publicKey::toBytesCompressed));
  }

  BLSPublicKey(final Bytes48 bytesCompressed) {
    this(
        Suppliers.memoize(() -> BLS.getBlsImpl().publicKeyFromCompressed(bytesCompressed)),
        () -> bytesCompressed);
  }

  private BLSPublicKey(
      final Supplier<PublicKey> publicKey, final Supplier<Bytes48> bytesCompressed) {
    this.publicKey = publicKey;
    this.bytesCompressed = bytesCompressed;
  }

  /**
   * Returns the SSZ serialization of the <em>compressed</em> form of the signature.
   *
   * @return the serialization of the compressed form of the signature.
   */
  public Bytes toSSZBytes() {
    return SSZ.encode(writer -> writer.writeFixedBytes(toBytesCompressed()));
  }

  public Bytes48 toBytesCompressed() {
    return bytesCompressed.get();
  }

  PublicKey getPublicKey() {
    return publicKey.get();
  }

  public boolean isInGroup() {
    return publicKey.get().isInGroup();
  }

  public boolean isValid() {
    return publicKey.get().isValid();
  }

  public String toAbbreviatedString() {
    return toBytesCompressed().toUnprefixedHexString().substring(0, 7);
  }

  public String toHexString() {
    return toBytesCompressed().toHexString();
  }

  @Override
  public String toString() {
    return toBytesCompressed().toString();
  }

  @Override
  public boolean equals(final Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (obj instanceof final BLSPublicKey other) {

      return Objects.equals(this.toBytesCompressed(), other.toBytesCompressed());
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(toBytesCompressed());
  }
}
