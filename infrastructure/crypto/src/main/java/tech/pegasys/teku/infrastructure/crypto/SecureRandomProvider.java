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

import java.security.SecureRandom;

/**
 * Avoid direct construction of SecureRandom in Teku code, instead use this class to obtain
 * SecureRandom instance
 */
public class SecureRandomProvider {
  private static final SecureRandom PUBLIC_SECURE_RANDOM = secureRandom();

  // Returns a shared instance of secure random intended to be used where the value is used publicly
  public static SecureRandom publicSecureRandom() {
    return PUBLIC_SECURE_RANDOM;
  }

  public static SecureRandom createSecureRandom() {
    return secureRandom();
  }

  @SuppressWarnings("DoNotCreateSecureRandomDirectly")
  private static SecureRandom secureRandom() {
    return new SecureRandom();
  }
}
