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

import java.math.BigInteger;
import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes32;

public class BLSConstants {

  public static final int BLS_PUBKEY_SIZE = 48;
  public static final int BLS_SIGNATURE_SIZE = 96;

  static final Bytes32 CURVE_ORDER_BYTES =
      Bytes32.fromHexString("0x73eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000001");
  static final BigInteger CURVE_ORDER_BI =
      CURVE_ORDER_BYTES.toUnsignedBigInteger(ByteOrder.BIG_ENDIAN);

  @SuppressWarnings("NonFinalStaticField")
  public static boolean verificationDisabled = false;

  public static void disableBLSVerification() {
    verificationDisabled = true;
  }

  public static void enableBLSVerification() {
    verificationDisabled = false;
  }
}
