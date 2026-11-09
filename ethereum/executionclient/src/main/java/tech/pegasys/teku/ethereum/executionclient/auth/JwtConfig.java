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

package tech.pegasys.teku.ethereum.executionclient.auth;

import java.nio.file.Path;
import java.util.Optional;
import javax.crypto.SecretKey;

public class JwtConfig {

  /**
   * EL SHOULD only accept iat timestamps which are within +-5 seconds from the current time.
   * https://github.com/ethereum/execution-apis/blob/main/src/engine/authentication.md
   */
  public static final long TOLERANCE_IN_SECONDS = 5;

  /**
   * This constant should be less than {@link #TOLERANCE_IN_SECONDS} for delivery delays/time
   * difference
   */
  public static final long EXPIRES_IN_SECONDS = TOLERANCE_IN_SECONDS - 2;

  private final SecretKey key;
  private final Optional<String> claimId;

  public JwtConfig(final SecretKey key, final Optional<String> claimId) {
    this.key = key;
    this.claimId = claimId;
  }

  public SecretKey getKey() {
    return key;
  }

  public Optional<String> getClaimId() {
    return claimId;
  }

  public static Optional<JwtConfig> createIfNeeded(
      final boolean needed,
      final Optional<String> jwtSecretFile,
      final Optional<String> jwtClaimId,
      final Path beaconDataDirectory) {
    if (needed) {
      final JwtSecretKeyLoader keyLoader =
          new JwtSecretKeyLoader(jwtSecretFile, beaconDataDirectory);
      return Optional.of(new JwtConfig(keyLoader.getSecretKey(), jwtClaimId));
    } else {
      return Optional.empty();
    }
  }
}
