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

package tech.pegasys.teku.spec.config;

import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigBellatrixImpl extends DelegatingSpecConfigAltair
    implements SpecConfigBellatrix {

  private final UInt64 inactivityPenaltyQuotientBellatrix;
  private final int minSlashingPenaltyQuotientBellatrix;
  private final int proportionalSlashingMultiplierBellatrix;
  private final int maxBytesPerTransaction;
  private final int maxTransactionsPerPayload;
  private final int bytesPerLogsBloom;
  private final int maxExtraDataBytes;

  // Transition
  private final UInt256 terminalTotalDifficulty;
  private final Bytes32 terminalBlockHash;
  private final UInt64 terminalBlockHashActivationEpoch;

  // Optimistic Sync
  private final int safeSlotsToImportOptimistically;

  public SpecConfigBellatrixImpl(
      final SpecConfigAltair specConfig,
      final UInt64 inactivityPenaltyQuotientBellatrix,
      final int minSlashingPenaltyQuotientBellatrix,
      final int proportionalSlashingMultiplierBellatrix,
      final int maxBytesPerTransaction,
      final int maxTransactionsPerPayload,
      final int bytesPerLogsBloom,
      final int maxExtraDataBytes,
      final UInt256 terminalTotalDifficulty,
      final Bytes32 terminalBlockHash,
      final UInt64 terminalBlockHashActivationEpoch,
      final int safeSlotsToImportOptimistically) {
    super(specConfig);
    this.inactivityPenaltyQuotientBellatrix = inactivityPenaltyQuotientBellatrix;
    this.minSlashingPenaltyQuotientBellatrix = minSlashingPenaltyQuotientBellatrix;
    this.proportionalSlashingMultiplierBellatrix = proportionalSlashingMultiplierBellatrix;
    this.maxBytesPerTransaction = maxBytesPerTransaction;
    this.maxTransactionsPerPayload = maxTransactionsPerPayload;
    this.bytesPerLogsBloom = bytesPerLogsBloom;
    this.maxExtraDataBytes = maxExtraDataBytes;
    this.terminalTotalDifficulty = terminalTotalDifficulty;
    this.terminalBlockHash = terminalBlockHash;
    this.terminalBlockHashActivationEpoch = terminalBlockHashActivationEpoch;
    this.safeSlotsToImportOptimistically = safeSlotsToImportOptimistically;
  }

  public static SpecConfigBellatrix required(final SpecConfig specConfig) {
    return specConfig
        .toVersionBellatrix()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected bellatrix spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  @Override
  public UInt64 getInactivityPenaltyQuotientBellatrix() {
    return inactivityPenaltyQuotientBellatrix;
  }

  @Override
  public int getMinSlashingPenaltyQuotientBellatrix() {
    return minSlashingPenaltyQuotientBellatrix;
  }

  @Override
  public int getProportionalSlashingMultiplierBellatrix() {
    return proportionalSlashingMultiplierBellatrix;
  }

  @Override
  public int getMaxBytesPerTransaction() {
    return maxBytesPerTransaction;
  }

  @Override
  public int getMaxTransactionsPerPayload() {
    return maxTransactionsPerPayload;
  }

  @Override
  public int getBytesPerLogsBloom() {
    return bytesPerLogsBloom;
  }

  @Override
  public int getMaxExtraDataBytes() {
    return maxExtraDataBytes;
  }

  @Override
  public UInt256 getTerminalTotalDifficulty() {
    return terminalTotalDifficulty;
  }

  @Override
  public Bytes32 getTerminalBlockHash() {
    return terminalBlockHash;
  }

  @Override
  public UInt64 getTerminalBlockHashActivationEpoch() {
    return terminalBlockHashActivationEpoch;
  }

  @Override
  public int getSafeSlotsToImportOptimistically() {
    return safeSlotsToImportOptimistically;
  }

  @Override
  public Optional<SpecConfigBellatrix> toVersionBellatrix() {
    return Optional.of(this);
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.BELLATRIX;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigBellatrixImpl that = (SpecConfigBellatrixImpl) o;
    return Objects.equals(specConfig, that.specConfig)
        && minSlashingPenaltyQuotientBellatrix == that.minSlashingPenaltyQuotientBellatrix
        && proportionalSlashingMultiplierBellatrix == that.proportionalSlashingMultiplierBellatrix
        && maxBytesPerTransaction == that.maxBytesPerTransaction
        && maxTransactionsPerPayload == that.maxTransactionsPerPayload
        && bytesPerLogsBloom == that.bytesPerLogsBloom
        && maxExtraDataBytes == that.maxExtraDataBytes
        && Objects.equals(
            inactivityPenaltyQuotientBellatrix, that.inactivityPenaltyQuotientBellatrix)
        && Objects.equals(terminalTotalDifficulty, that.terminalTotalDifficulty)
        && Objects.equals(terminalBlockHash, that.terminalBlockHash)
        && Objects.equals(terminalBlockHashActivationEpoch, that.terminalBlockHashActivationEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        inactivityPenaltyQuotientBellatrix,
        minSlashingPenaltyQuotientBellatrix,
        proportionalSlashingMultiplierBellatrix,
        maxBytesPerTransaction,
        maxTransactionsPerPayload,
        bytesPerLogsBloom,
        maxExtraDataBytes,
        terminalTotalDifficulty,
        terminalBlockHash,
        terminalBlockHashActivationEpoch);
  }
}
