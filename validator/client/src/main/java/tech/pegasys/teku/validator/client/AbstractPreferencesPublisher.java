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

package tech.pegasys.teku.validator.client;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

// Base class for proposer and builder preferences publishing
abstract class AbstractPreferencesPublisher implements ValidatorTimingChannel {

  private static final Logger LOG = LogManager.getLogger();

  protected final OwnedValidators ownedValidators;
  protected final Spec spec;

  public AbstractPreferencesPublisher(final OwnedValidators ownedValidators, final Spec spec) {
    this.ownedValidators = ownedValidators;
    this.spec = spec;
  }

  @Override
  public void onProposerDutiesLoaded(final UInt64 epoch, final ProposerDuties proposerDuties) {
    if (!spec.areProposerAndBuilderPreferencesRequiredAtEpoch(epoch)) {
      return;
    }

    final List<ProposerDuty> ownedProposerDuties =
        proposerDuties.getDuties().stream()
            .filter(duty -> ownedValidators.hasValidator(duty.getPublicKey()))
            .toList();

    LOG.debug(
        "Owned validators have {} proposer duties in epoch {}", ownedProposerDuties.size(), epoch);

    if (ownedProposerDuties.isEmpty()) {
      return;
    }

    // Gloas's get_shuffling_dependent_root(store, head, e) returns the block root at
    // start_of_(e-MIN_SEED_LOOKAHEAD) - 1. As far as MIN_SEED_LOOKAHEAD == 1,
    // for next-epoch duties, BlockProposalUtilFulu's
    // getBlockProposalDependentRoot returns the same value, so we reuse it here.
    final Bytes32 dependentRoot = proposerDuties.getDependentRoot();

    publishPreferences(epoch, ownedProposerDuties, dependentRoot);
  }

  abstract void publishPreferences(
      UInt64 epoch, List<ProposerDuty> ownedProposerDuties, Bytes32 dependentRoot);
}
