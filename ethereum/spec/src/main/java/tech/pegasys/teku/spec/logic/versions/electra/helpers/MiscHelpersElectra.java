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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.crypto.Hash.getSha256Instance;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.crypto.Sha256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class MiscHelpersElectra extends MiscHelpersDeneb {
  // 2**16 - 1
  public static final UInt64 MAX_RANDOM_VALUE = UInt64.valueOf(65535);
  private final SpecConfigElectra specConfigElectra;
  private final PredicatesElectra predicatesElectra;

  public MiscHelpersElectra(
      final SpecConfigElectra specConfig,
      final PredicatesElectra predicates,
      final SchemaDefinitionsElectra schemaDefinitions) {
    super(
        SpecConfigDeneb.required(specConfig),
        predicates,
        SchemaDefinitionsDeneb.required(schemaDefinitions));
    this.specConfigElectra = specConfig;
    this.predicatesElectra = predicates;
  }

  public static MiscHelpersElectra required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Electra misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  @Override
  public int computeProposerIndex(
      final BeaconState state, final IntList indices, final Bytes32 seed) {
    return computeProposerIndex(
        state,
        indices,
        seed,
        SpecConfigElectra.required(specConfig).getMaxEffectiveBalanceElectra());
  }

  @Override
  protected int computeProposerIndex(
      final BeaconState state,
      final IntList indices,
      final Bytes32 seed,
      final UInt64 maxEffectiveBalance) {
    checkArgument(!indices.isEmpty(), "compute_proposer_index indices must not be empty");

    final Sha256 sha256 = getSha256Instance();

    int i = 0;
    final int total = indices.size();
    Bytes randomBytes = null;
    while (true) {
      final int candidateIndex = indices.getInt(computeShuffledIndex(i % total, total, seed));
      if (i % 16 == 0) {
        randomBytes = Bytes.wrap(sha256.digest(seed, uint64ToBytes(Math.floorDiv(i, 16L))));
      }
      final int offset = (i % 16) * 2;
      final UInt64 randomValue = bytesToUInt64(randomBytes.slice(offset, 2));
      final UInt64 validatorEffectiveBalance =
          state.getValidators().get(candidateIndex).getEffectiveBalance();
      if (validatorEffectiveBalance
          .times(MAX_RANDOM_VALUE)
          .isGreaterThanOrEqualTo(maxEffectiveBalance.times(randomValue))) {
        return candidateIndex;
      }
      i++;
    }
  }

  @Override
  public UInt64 getMaxEffectiveBalance(final Validator validator) {
    return predicatesElectra.hasCompoundingWithdrawalCredential(validator)
        ? specConfigElectra.getMaxEffectiveBalanceElectra()
        : specConfigElectra.getMinActivationBalance();
  }

  @Override
  public Validator getValidatorFromDeposit(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials, final UInt64 amount) {
    final Validator validator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            ZERO,
            false,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH);

    final UInt64 maxEffectiveBalance = getMaxEffectiveBalance(validator);
    final UInt64 validatorEffectiveBalance =
        amount
            .minusMinZero(amount.mod(specConfig.getEffectiveBalanceIncrement()))
            .min(maxEffectiveBalance);

    return validator.withEffectiveBalance(validatorEffectiveBalance);
  }

  @Override
  public boolean isFormerDepositMechanismDisabled(final BeaconState state) {
    // if the next deposit to be processed by Eth1Data poll has the index of the first deposit
    // processed with the new deposit flow, i.e. `eth1_deposit_index ==
    // deposit_requests_start_index`, we should stop Eth1Data deposits processing
    return state
        .getEth1DepositIndex()
        .equals(BeaconStateElectra.required(state).getDepositRequestsStartIndex());
  }

  @Override
  public Optional<MiscHelpersElectra> toVersionElectra() {
    return Optional.of(this);
  }
}
