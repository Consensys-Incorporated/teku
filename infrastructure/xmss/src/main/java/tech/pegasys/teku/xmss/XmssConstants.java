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

package tech.pegasys.teku.xmss;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Sizes and switches for leanXMSS, the hash-based signature scheme of the PQ fork. */
public final class XmssConstants {

  public static final int PUBLIC_KEY_SIZE = 32;
  public static final int SIGNATURE_SIZE = 2404;
  public static final int SEED_SIZE = 32;
  public static final int MESSAGE_SIZE = 32;

  /** A signature starts with its leaf index, as a little-endian uint32. */
  public static final int LEAF_INDEX_SIZE = 4;

  public static final UInt64 MAX_LEAF_INDEX = UInt64.valueOf(0xFFFFFFFFL);

  /**
   * When set, {@link Xmss#verify} accepts every signature. Reference tests need this because their
   * vectors carry stub signatures. Never set it in production.
   */
  @SuppressWarnings("NonFinalStaticField")
  public static boolean verificationDisabled = false;

  private XmssConstants() {}

  public static void disableXmssVerification() {
    verificationDisabled = true;
  }

  public static void enableXmssVerification() {
    verificationDisabled = false;
  }
}
