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

package tech.pegasys.teku.spec.datastructures.state;

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.infrastructure.ssz.SszList;

/**
 * Decodes {@link Validator} fields straight from the validator's serialized bytes (fixed 121-byte
 * layout), as handed out by {@link SszList#forEachElementSsz}, so whole-registry scans read the
 * packed registry without creating a {@link Validator} view per index. Epochs and balances are
 * returned as raw uint64 bits ({@code -1L} is {@code FAR_FUTURE_EPOCH}); compare them unsigned.
 */
public final class ValidatorFields {

  private static final int PUBKEY = fieldOffset(0);
  private static final int WITHDRAWAL_CREDENTIALS = fieldOffset(1);
  private static final int EFFECTIVE_BALANCE = fieldOffset(2);
  private static final int SLASHED = fieldOffset(3);
  private static final int ACTIVATION_ELIGIBILITY_EPOCH = fieldOffset(4);
  private static final int ACTIVATION_EPOCH = fieldOffset(5);
  private static final int EXIT_EPOCH = fieldOffset(6);
  private static final int WITHDRAWABLE_EPOCH = fieldOffset(7);

  private ValidatorFields() {}

  private static int fieldOffset(final int fieldIndex) {
    int offset = 0;
    for (int i = 0; i < fieldIndex; i++) {
      offset += Validator.SSZ_SCHEMA.getChildSchema(i).getSszFixedPartSize();
    }
    return offset;
  }

  /** The validator's pubkey, as a copy of its 48 bytes (never a view over {@code data}). */
  public static Bytes48 pubkey(final Bytes data, final int offset) {
    return Bytes48.wrap(data.slice(offset + PUBKEY, Bytes48.SIZE).toArray());
  }

  public static byte withdrawalCredentialsPrefix(final Bytes data, final int offset) {
    return data.get(offset + WITHDRAWAL_CREDENTIALS);
  }

  public static long effectiveBalance(final Bytes data, final int offset) {
    return uint64(data, offset + EFFECTIVE_BALANCE);
  }

  public static boolean slashed(final Bytes data, final int offset) {
    return data.get(offset + SLASHED) != 0;
  }

  public static long activationEligibilityEpoch(final Bytes data, final int offset) {
    return uint64(data, offset + ACTIVATION_ELIGIBILITY_EPOCH);
  }

  public static long activationEpoch(final Bytes data, final int offset) {
    return uint64(data, offset + ACTIVATION_EPOCH);
  }

  public static long exitEpoch(final Bytes data, final int offset) {
    return uint64(data, offset + EXIT_EPOCH);
  }

  public static long withdrawableEpoch(final Bytes data, final int offset) {
    return uint64(data, offset + WITHDRAWABLE_EPOCH);
  }

  private static long uint64(final Bytes data, final int offset) {
    return data.getLong(offset, ByteOrder.LITTLE_ENDIAN);
  }
}
