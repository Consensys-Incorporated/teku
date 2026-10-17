/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.heze.helpers;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;

public class MiscHelpersHeze extends MiscHelpersGloas {

  private final SpecConfigHeze specConfigHeze;

  public MiscHelpersHeze(
      final SpecConfigHeze specConfig,
      final PredicatesGloas predicates,
      final SchemaDefinitionsHeze schemaDefinitions) {
    super(specConfig, predicates, schemaDefinitions);
    this.specConfigHeze = specConfig;
  }

  /**
   * compute_blob_data_retention_start_epoch
   *
   * <p>EIP-8198: the retention window is {@code MIN_BLOB_DATA_RETENTION_MS} of wall-clock time, so
   * its length in epochs depends on the slot durations it spans.
   */
  @Override
  public UInt64 computeDataColumnSidecarRetentionStartEpoch(final UInt64 currentEpoch) {
    final UInt64 windowMillis = specConfigHeze.getMinBlobDataRetentionMs();
    final UInt64 currentStartMillis =
        computeTimeMillisAtSlot(UInt64.ZERO, computeStartSlotAtEpoch(currentEpoch));
    if (currentStartMillis.isLessThan(windowMillis)) {
      return GENESIS_EPOCH;
    }
    return computeEpochAtSlot(
        computeSlotAtTimeMillis(UInt64.ZERO, currentStartMillis.minus(windowMillis)));
  }

  @Override
  public boolean isInclusionListAvailable() {
    return true;
  }

  @Override
  public Optional<MiscHelpersHeze> toVersionHeze() {
    return Optional.of(this);
  }
}
