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

package tech.pegasys.teku.spec.config.builder;

import static tech.pegasys.teku.spec.constants.NetworkConstants.DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigBellatrixImpl;

public class BellatrixBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigAltair, SpecConfigBellatrix> {

  private UInt64 inactivityPenaltyQuotientBellatrix;
  private Integer minSlashingPenaltyQuotientBellatrix;
  private Integer proportionalSlashingMultiplierBellatrix;
  private Integer maxBytesPerTransaction;
  private Integer maxTransactionsPerPayload;
  private Integer bytesPerLogsBloom;
  private Integer maxExtraDataBytes;

  // Transition
  private UInt256 terminalTotalDifficulty;
  private Bytes32 terminalBlockHash;
  private UInt64 terminalBlockHashActivationEpoch;

  // Optimistic Sync
  private Integer safeSlotsToImportOptimistically = DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;

  BellatrixBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigBellatrix> build(
      final SpecConfigAndParent<SpecConfigAltair> specConfigAndParent) {
    return SpecConfigAndParent.of(
        new SpecConfigBellatrixImpl(
            specConfigAndParent.specConfig(),
            inactivityPenaltyQuotientBellatrix,
            minSlashingPenaltyQuotientBellatrix,
            proportionalSlashingMultiplierBellatrix,
            maxBytesPerTransaction,
            maxTransactionsPerPayload,
            bytesPerLogsBloom,
            maxExtraDataBytes,
            terminalTotalDifficulty,
            terminalBlockHash,
            terminalBlockHashActivationEpoch,
            safeSlotsToImportOptimistically),
        specConfigAndParent);
  }

  @Override
  public void validate() {
    // temporary, provide default values for backward compatibility
    if (terminalTotalDifficulty == null) {
      terminalTotalDifficulty =
          UInt256.valueOf(
              new BigInteger(
                  "115792089237316195423570985008687907853269984665640564039457584007913129638912"));
    }
    if (terminalBlockHash == null) {
      terminalBlockHash = Bytes32.fromHexStringLenient("0x00");
    }
    if (terminalBlockHashActivationEpoch == null) {
      terminalBlockHashActivationEpoch = UInt64.valueOf("18446744073709551615");
    }
    defaultValuesIfRequired(this);
    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("inactivityPenaltyQuotientBellatrix", inactivityPenaltyQuotientBellatrix);
    constants.put("minSlashingPenaltyQuotientBellatrix", minSlashingPenaltyQuotientBellatrix);
    constants.put(
        "proportionalSlashingMultiplierBellatrix", proportionalSlashingMultiplierBellatrix);
    constants.put("maxBytesPerTransaction", maxBytesPerTransaction);
    constants.put("maxTransactionsPerPayload", maxTransactionsPerPayload);
    constants.put("bytesPerLogsBloom", bytesPerLogsBloom);
    constants.put("maxExtraDataBytes", maxExtraDataBytes);
    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("TERMINAL_TOTAL_DIFFICULTY", terminalTotalDifficulty);
    rawConfig.accept("TERMINAL_BLOCK_HASH", terminalBlockHash);
    rawConfig.accept("TERMINAL_BLOCK_HASH_ACTIVATION_EPOCH", terminalBlockHashActivationEpoch);
  }

  public BellatrixBuilder inactivityPenaltyQuotientBellatrix(
      final UInt64 inactivityPenaltyQuotientBellatrix) {
    this.inactivityPenaltyQuotientBellatrix = inactivityPenaltyQuotientBellatrix;
    return this;
  }

  public BellatrixBuilder minSlashingPenaltyQuotientBellatrix(
      final Integer minSlashingPenaltyQuotientBellatrix) {
    this.minSlashingPenaltyQuotientBellatrix = minSlashingPenaltyQuotientBellatrix;
    return this;
  }

  public BellatrixBuilder proportionalSlashingMultiplierBellatrix(
      final Integer proportionalSlashingMultiplierBellatrix) {
    this.proportionalSlashingMultiplierBellatrix = proportionalSlashingMultiplierBellatrix;
    return this;
  }

  public BellatrixBuilder maxBytesPerTransaction(final Integer maxBytesPerTransaction) {
    this.maxBytesPerTransaction = maxBytesPerTransaction;
    return this;
  }

  public BellatrixBuilder maxTransactionsPerPayload(final Integer maxTransactionsPerPayload) {
    this.maxTransactionsPerPayload = maxTransactionsPerPayload;
    return this;
  }

  public BellatrixBuilder bytesPerLogsBloom(final Integer bytesPerLogsBloom) {
    this.bytesPerLogsBloom = bytesPerLogsBloom;
    return this;
  }

  public BellatrixBuilder terminalTotalDifficulty(final UInt256 terminalTotalDifficulty) {
    this.terminalTotalDifficulty = terminalTotalDifficulty;
    return this;
  }

  public BellatrixBuilder terminalBlockHash(final Bytes32 terminalBlockHash) {
    this.terminalBlockHash = terminalBlockHash;
    return this;
  }

  public BellatrixBuilder terminalBlockHashActivationEpoch(
      final UInt64 terminalBlockHashActivationEpoch) {
    this.terminalBlockHashActivationEpoch = terminalBlockHashActivationEpoch;
    return this;
  }

  public BellatrixBuilder maxExtraDataBytes(final Integer maxExtraDataBytes) {
    this.maxExtraDataBytes = maxExtraDataBytes;
    return this;
  }

  public BellatrixBuilder safeSlotsToImportOptimistically(
      final Integer safeSlotsToImportOptimistically) {
    this.safeSlotsToImportOptimistically = safeSlotsToImportOptimistically;
    return this;
  }
}
