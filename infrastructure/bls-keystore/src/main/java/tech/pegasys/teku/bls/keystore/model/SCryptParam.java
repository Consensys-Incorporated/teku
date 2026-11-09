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

package tech.pegasys.teku.bls.keystore.model;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.crypto.generators.SCrypt;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;

public class SCryptParam extends KdfParam {
  private final int n;
  private final int p;
  private final int r;

  /**
   * SCrypt Key Derivation Function
   *
   * @param dklen The length of key to generate.
   * @param n CPU/Memory cost parameter. Must be larger than 1, a power of 2 and less than <code>
   *     2^(128 * r / 8)</code>.
   * @param p Parallelization parameter. Must be a positive integer less than or equal to <code>
   *     Integer.MAX_VALUE / (128 * r * 8)</code>
   * @param r the block size, must be &gt;= 1.
   * @param salt The salt to use
   */
  @JsonCreator
  public SCryptParam(
      @JsonProperty(value = "dklen", required = true) final int dklen,
      @JsonProperty(value = "n", required = true) final int n,
      @JsonProperty(value = "p", required = true) final int p,
      @JsonProperty(value = "r", required = true) final int r,
      @JsonProperty(value = "salt", required = true) final Bytes salt) {
    super(dklen, salt);
    this.n = n;
    this.p = p;
    this.r = r;
  }

  /**
   * Create SCryptParam with dklen and salt and using reasonable defaults for n (2^18), p and r.
   *
   * @param dklen The derivative key length to generate
   * @param salt The salt to use
   */
  public SCryptParam(final int dklen, final Bytes salt) {
    this(dklen, 262_144, 1, 8, salt);
  }

  @Override
  public void validate() throws KeyStoreValidationException {
    super.validate();
    if (n <= 1 || !isPowerOf2(n)) {
      throw new KeyStoreValidationException("Cost parameter n must be > 1 and a power of 2");
    }
    // Only value of r that cost (as an int) could be exceeded for is 1
    if (r == 1 && n >= 65536) {
      throw new KeyStoreValidationException("Cost parameter n must be > 1 and < 65536");
    }
    if (r < 1) {
      throw new KeyStoreValidationException("Block size r must be >= 1");
    }
    int maxParallel = Integer.MAX_VALUE / (128 * r * 8);
    if (p < 1 || p > maxParallel) {
      throw new KeyStoreValidationException(
          String.format(
              "Parallelization parameter p must be >= 1 and <= %d (based on block size r of %d)",
              maxParallel, r));
    }
  }

  @JsonProperty(value = "n")
  public Integer getN() {
    return n;
  }

  @JsonProperty(value = "p")
  public Integer getP() {
    return p;
  }

  @JsonProperty(value = "r")
  public Integer getR() {
    return r;
  }

  @Override
  @JsonIgnore
  public KdfFunction getKdfFunction() {
    return KdfFunction.SCRYPT;
  }

  @Override
  protected Bytes generateDecryptionKey(final Bytes password) {
    checkNotNull(password, "Password cannot be null");
    return Bytes.wrap(
        SCrypt.generate(
            password.toArrayUnsafe(),
            getSalt().toArrayUnsafe(),
            getN(),
            getR(),
            getP(),
            getDkLen()));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("dklen", getDkLen())
        .add("n", n)
        .add("p", p)
        .add("r", r)
        .add("salt", getSalt())
        .toString();
  }

  private static boolean isPowerOf2(final int x) {
    return ((x & (x - 1)) == 0);
  }
}
