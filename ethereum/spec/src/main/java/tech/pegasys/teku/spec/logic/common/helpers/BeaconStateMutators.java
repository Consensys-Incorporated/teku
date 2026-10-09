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

package tech.pegasys.teku.spec.logic.common.helpers;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

public class BeaconStateMutators {
  private static final Logger LOG = LogManager.getLogger();

  protected final SpecConfig specConfig;
  protected final MiscHelpers miscHelpers;
  private final BeaconStateAccessors beaconStateAccessors;

  public BeaconStateMutators(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  /**
   * this is the same as {@link #increaseBalance(MutableBeaconState, int, UInt64)} but in addition
   * it updates the lastBlockRewardCache to keep track of the total rewards for the last block
   *
   * @param state
   * @param proposerIndex
   * @param delta
   */
  public void increaseProposerBalance(
      final MutableBeaconState state, final int proposerIndex, final UInt64 delta) {
    increaseBalance(state, proposerIndex, delta);
    BeaconStateCache.getSlotCaches(state).increaseBlockProposerRewards(delta);
  }

  /**
   * Increase validator balance by ``delta``.
   *
   * @param state
   * @param index
   * @param delta
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#increase_balance</a>
   */
  public void increaseBalance(final MutableBeaconState state, final int index, final UInt64 delta) {
    state.getBalances().setElement(index, state.getBalances().getElement(index).plus(delta));
  }

  /**
   * Decrease validator balance by ``delta`` with underflow protection.
   *
   * @param state
   * @param index
   * @param delta
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#decrease_balance</a>
   */
  public void decreaseBalance(final MutableBeaconState state, final int index, final UInt64 delta) {
    state
        .getBalances()
        .setElement(index, state.getBalances().getElement(index).minusMinZero(delta));
  }

  /**
   * Initiate the exit of the validator with index ``index``.
   *
   * <p>This function differs from the reference implementation because it was too slow. The current
   * implementation relies on a lazily initialized context which compute the initial values for
   * `exitQueueEpoch` and `exitQueueChurn`. Consecutive initiateValidatorExit calls will
   * progressively update the context values without the need of recomputing them from the state.
   *
   * <p>The assumption is that validators' `exitEpoch` and the context are only updated via this
   * function so that they can be maintained consistent and aligned with the state
   *
   * @param state
   * @param index
   * @param validatorExitContextSupplier
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v1.2.0/specs/phase0/beacon-chain.md#initiate_validator_exit</a>
   */
  public void initiateValidatorExit(
      final MutableBeaconState state,
      final int index,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {

    final Validator validator = state.getValidators().get(index);
    // Return if validator already initiated exit
    if (!validator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
      return;
    }

    final ValidatorExitContext validatorExitContext = validatorExitContextSupplier.get();

    if (validatorExitContext.exitQueueChurn.compareTo(validatorExitContext.churnLimit) >= 0) {
      validatorExitContext.exitQueueEpoch = validatorExitContext.exitQueueEpoch.increment();
      validatorExitContext.exitQueueChurn = UInt64.ONE;
    } else {
      validatorExitContext.exitQueueChurn = validatorExitContext.exitQueueChurn.increment();
    }

    // Set validator exit epoch and withdrawable epoch
    state
        .getValidators()
        .set(
            index,
            validator
                .withExitEpoch(validatorExitContext.exitQueueEpoch)
                .withWithdrawableEpoch(
                    validatorExitContext.exitQueueEpoch.plus(
                        specConfig.getMinValidatorWithdrawabilityDelay())));
  }

  /**
   * We want the context to be lazily initialized, so we use a memoizing supplier.
   *
   * @param state
   * @return
   */
  public Supplier<ValidatorExitContext> createValidatorExitContextSupplier(
      final BeaconState state) {
    return Suppliers.memoize(() -> createValidatorExitContext(state));
  }

  /**
   * This function implements an optimized version of exitQueueEpoch and exitQueueChurn calculation,
   * compared to the `initiate_validator_exit` reference implementation.
   *
   * @param state
   * @return
   */
  private ValidatorExitContext createValidatorExitContext(final BeaconState state) {
    final ValidatorExitContext validatorExitContext =
        new ValidatorExitContext(beaconStateAccessors.getValidatorChurnLimit(state));

    validatorExitContext.exitQueueEpoch = UInt64.ZERO;

    final List<Validator> exitedValidatorsInExitQueueEpoch = new ArrayList<>();

    for (Validator validator : state.getValidators()) {
      final UInt64 validatorExitEpoch = validator.getExitEpoch();
      if (validatorExitEpoch.equals(FAR_FUTURE_EPOCH)) {
        continue;
      }

      if (validatorExitEpoch.equals(validatorExitContext.exitQueueEpoch)) {
        exitedValidatorsInExitQueueEpoch.add(validator);
        continue;
      }
      if (validatorExitEpoch.isGreaterThan(validatorExitContext.exitQueueEpoch)) {
        validatorExitContext.exitQueueEpoch = validatorExitEpoch;
        exitedValidatorsInExitQueueEpoch.clear();
        exitedValidatorsInExitQueueEpoch.add(validator);
      }
    }
    final UInt64 activationExitEpoch =
        miscHelpers.computeActivationExitEpoch(beaconStateAccessors.getCurrentEpoch(state));

    if (activationExitEpoch.isGreaterThan(validatorExitContext.exitQueueEpoch)) {
      validatorExitContext.exitQueueEpoch = activationExitEpoch;
      validatorExitContext.exitQueueChurn = UInt64.ZERO;
    } else {
      validatorExitContext.exitQueueChurn = UInt64.valueOf(exitedValidatorsInExitQueueEpoch.size());
    }

    return validatorExitContext;
  }

  public static class ValidatorExitContext {
    private UInt64 exitQueueEpoch;
    private UInt64 exitQueueChurn;

    private final UInt64 churnLimit;

    private ValidatorExitContext(final UInt64 churnLimit) {
      this.churnLimit = churnLimit;
    }

    public UInt64 getExitQueueEpoch() {
      return exitQueueEpoch;
    }

    public UInt64 getExitQueueChurn() {
      return exitQueueChurn;
    }

    public UInt64 getChurnLimit() {
      return churnLimit;
    }

    public void setExitQueueEpoch(final UInt64 exitQueueEpoch) {
      this.exitQueueEpoch = exitQueueEpoch;
    }

    public void setExitQueueChurn(final UInt64 exitQueueChurn) {
      this.exitQueueChurn = exitQueueChurn;
    }
  }

  /**
   * <a
   * href="https://github.com/ethereum/consensus-specs/blob/master/specs/phase0/beacon-chain.md#deposits">add_validator_to_registry</a>
   */
  public void addValidatorToRegistry(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount) {
    final Validator validator =
        miscHelpers.getValidatorFromDeposit(pubkey, withdrawalCredentials, amount);
    LOG.debug("Adding new validator with index {} to state", state.getValidators().size());
    state.getValidators().append(validator);
    state.getBalances().appendElement(amount);
  }

  public void slashValidator(
      final MutableBeaconState state,
      final int slashedIndex,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    slashValidator(state, slashedIndex, -1, validatorExitContextSupplier);
  }

  private void slashValidator(
      final MutableBeaconState state,
      final int slashedIndex,
      final int whistleblowerIndex,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(state);
    initiateValidatorExit(state, slashedIndex, validatorExitContextSupplier);

    final Validator validator = state.getValidators().get(slashedIndex);

    state
        .getValidators()
        .set(
            slashedIndex,
            validator
                .withSlashed(true)
                .withWithdrawableEpoch(
                    validator
                        .getWithdrawableEpoch()
                        .max(epoch.plus(specConfig.getEpochsPerSlashingsVector()))));
    BeaconStateCache.getTransitionCaches(state).getTotalActiveUnslashedBalance().clear();

    int index = epoch.mod(specConfig.getEpochsPerSlashingsVector()).intValue();
    state
        .getSlashings()
        .setElement(
            index, state.getSlashings().getElement(index).plus(validator.getEffectiveBalance()));
    decreaseBalance(
        state,
        slashedIndex,
        validator.getEffectiveBalance().dividedBy(getMinSlashingPenaltyQuotient()));

    // Apply proposer and whistleblower rewards
    int proposerIndex = beaconStateAccessors.getBeaconProposerIndex(state);

    final UInt64 whistleblowerReward =
        validator.getEffectiveBalance().dividedBy(getWhistleblowerRewardQuotient());

    if (whistleblowerIndex == -1) {
      // proposer takes all rewards
      increaseProposerBalance(state, proposerIndex, whistleblowerReward);
    } else {
      // split between proposer and whistleblower
      final UInt64 proposerReward = calculateProposerReward(whistleblowerReward);
      increaseProposerBalance(state, proposerIndex, proposerReward);
      increaseBalance(state, whistleblowerIndex, whistleblowerReward.minus(proposerReward));
    }
  }

  protected UInt64 calculateProposerReward(final UInt64 whistleblowerReward) {
    return whistleblowerReward.dividedBy(specConfig.getProposerRewardQuotient());
  }

  protected int getMinSlashingPenaltyQuotient() {
    return specConfig.getMinSlashingPenaltyQuotient();
  }

  protected int getWhistleblowerRewardQuotient() {
    return specConfig.getWhistleblowerRewardQuotient();
  }
}
