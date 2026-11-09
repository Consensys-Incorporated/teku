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

package tech.pegasys.teku.infrastructure.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class MessageDigestFactory {
  private static final Logger LOG = LogManager.getLogger();

  public static final String SHA_256 = "SHA-256";
  public static final String KECCAK_256 = "KECCAK-256";
  private static final Provider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();
  private static final Provider SHA_256_SECURITY_PROVIDER = selectSha256SecurityProvider();

  @SuppressWarnings("DoNotInvokeMessageDigestDirectly")
  public static MessageDigest createSha256() {
    try {
      return MessageDigest.getInstance(SHA_256, SHA_256_SECURITY_PROVIDER);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  @SuppressWarnings("DoNotInvokeMessageDigestDirectly")
  public static MessageDigest createKeccak256() {
    try {
      return MessageDigest.getInstance(KECCAK_256, BOUNCY_CASTLE_PROVIDER);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("KECCAK-256 algorithm not available", e);
    }
  }

  /**
   * We want to use a known security provider. The SUN provider shipped as part of openjdk based
   * JREs is faster than BouncyCastle but may not be available on all JREs (eg IBM's or if the name
   * changes in future releases).
   *
   * <p>We do expect that if the SUN provider is available it should support SHA-256, so log a
   * warning if that's not the case.
   *
   * <p>If the SUN provider isn't usable, we fallback to BouncyCastle which is shipped with Teku.
   *
   * @return the security provider.
   */
  @SuppressWarnings("DoNotInvokeMessageDigestDirectly")
  private static Provider selectSha256SecurityProvider() {
    final Provider sunProvider = Security.getProvider("SUN");
    if (sunProvider == null) {
      return new BouncyCastleProvider();
    }
    try {
      MessageDigest.getInstance(SHA_256, sunProvider);
      return sunProvider;
    } catch (final Throwable t) {
      LOG.warn(
          "SUN security provider available but does not support SHA-256, falling back to BouncyCastle.",
          t);
      return BOUNCY_CASTLE_PROVIDER;
    }
  }
}
