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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DelegatingSpecConfigBellatrix extends DelegatingSpecConfigAltair
    implements SpecConfigBellatrix {
  private final SpecConfigBellatrix specConfigBellatrix;

  public DelegatingSpecConfigBellatrix(final SpecConfigBellatrix specConfig) {
    super(specConfig);
    this.specConfigBellatrix = SpecConfigBellatrix.required(specConfig);
  }

  @Override
  public Optional<SpecConfigBellatrix> toVersionBellatrix() {
    return Optional.of(this);
  }

  @Override
  public UInt64 getInactivityPenaltyQuotientBellatrix() {
    return specConfigBellatrix.getInactivityPenaltyQuotientBellatrix();
  }

  @Override
  public int getMinSlashingPenaltyQuotientBellatrix() {
    return specConfigBellatrix.getMinSlashingPenaltyQuotientBellatrix();
  }

  @Override
  public int getProportionalSlashingMultiplierBellatrix() {
    return specConfigBellatrix.getProportionalSlashingMultiplierBellatrix();
  }

  @Override
  public int getMaxBytesPerTransaction() {
    return specConfigBellatrix.getMaxBytesPerTransaction();
  }

  @Override
  public int getMaxTransactionsPerPayload() {
    return specConfigBellatrix.getMaxTransactionsPerPayload();
  }

  @Override
  public int getBytesPerLogsBloom() {
    return specConfigBellatrix.getBytesPerLogsBloom();
  }

  @Override
  public int getMaxExtraDataBytes() {
    return specConfigBellatrix.getMaxExtraDataBytes();
  }

  @Override
  public UInt64 getTerminalBlockHashActivationEpoch() {
    return specConfigBellatrix.getTerminalBlockHashActivationEpoch();
  }

  @Override
  public Bytes32 getTerminalBlockHash() {
    return specConfigBellatrix.getTerminalBlockHash();
  }

  @Override
  public UInt256 getTerminalTotalDifficulty() {
    return specConfigBellatrix.getTerminalTotalDifficulty();
  }

  @Override
  public int getSafeSlotsToImportOptimistically() {
    return specConfigBellatrix.getSafeSlotsToImportOptimistically();
  }
}
