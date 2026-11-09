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

package tech.pegasys.teku.spec.datastructures.execution;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Optional;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface ExecutionPayloadSummary {

  UInt64 getGasLimit();

  UInt64 getGasUsed();

  UInt64 getBlockNumber();

  Bytes32 getBlockHash();

  UInt256 getBaseFeePerGas();

  UInt64 getTimestamp();

  Bytes32 getPrevRandao();

  Bytes32 getReceiptsRoot();

  Bytes32 getStateRoot();

  Bytes20 getFeeRecipient();

  Bytes32 getParentHash();

  Bytes getLogsBloom();

  Bytes getExtraData();

  boolean isDefaultPayload();

  Optional<Bytes32> getOptionalWithdrawalsRoot();

  default String toLogString() {
    return LogFormatter.formatBlock(getBlockNumber(), getBlockHash());
  }

  default UInt64 computeGasPercentage(final Logger logger) {
    try {
      return getGasLimit().isGreaterThan(0L)
          ? getGasUsed().times(100).dividedBy(getGasLimit())
          : ZERO;
    } catch (final ArithmeticException ex) {
      logger.debug("Failed to compute gas percentage", ex);
      return UInt64.ZERO;
    }
  }
}
