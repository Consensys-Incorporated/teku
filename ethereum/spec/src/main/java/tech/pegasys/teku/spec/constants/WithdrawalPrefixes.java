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

package tech.pegasys.teku.spec.constants;

import org.apache.tuweni.bytes.Bytes;

public class WithdrawalPrefixes {
  public static final byte BLS_WITHDRAWAL_BYTE = 0x00;
  public static final Bytes BLS_WITHDRAWAL_PREFIX = Bytes.of(BLS_WITHDRAWAL_BYTE);

  public static final byte ETH1_ADDRESS_WITHDRAWAL_BYTE = 0x01;
  public static final Bytes ETH1_ADDRESS_WITHDRAWAL_PREFIX = Bytes.of(ETH1_ADDRESS_WITHDRAWAL_BYTE);

  public static final byte COMPOUNDING_WITHDRAWAL_BYTE = 0x02;
  public static final Bytes COMPOUNDING_WITHDRAWAL_PREFIX = Bytes.of(COMPOUNDING_WITHDRAWAL_BYTE);

  public static final byte BUILDER_WITHDRAWAL_BYTE = (byte) 0xB0;
  public static final Bytes BUILDER_WITHDRAWAL_PREFIX = Bytes.of(BUILDER_WITHDRAWAL_BYTE);
}
