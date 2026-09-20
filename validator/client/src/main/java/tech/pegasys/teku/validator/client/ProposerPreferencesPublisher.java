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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.ProposerPreferencesUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class ProposerPreferencesPublisher implements ValidatorTimingChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;
  private final OwnedValidators ownedValidators;
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider;
  private final ForkProvider forkProvider;
  private final Spec spec;

  public ProposerPreferencesPublisher(
      final ValidatorApiChannel validatorApiChannel,
      final OwnedValidators ownedValidators,
      final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider,
      final ForkProvider forkProvider,
      final Spec spec) {
    this.validatorApiChannel = validatorApiChannel;
    this.ownedValidators = ownedValidators;
    this.proposerConfigPropertiesProvider = proposerConfigPropertiesProvider;
    this.forkProvider = forkProvider;
    this.spec = spec;
  }

  @Override
  public void onProposerDutiesLoaded(final UInt64 epoch, final ProposerDuties proposerDuties) {
    if (!spec.isProposerPreferencesAvailableAtEpoch(epoch)) {
      return;
    }

    final List<ProposerDuty> ownedProposerDuties =
        proposerDuties.getDuties().stream()
            .filter(duty -> ownedValidators.hasValidator(duty.getPublicKey()))
            .toList();

    if (ownedProposerDuties.isEmpty()) {
      LOG.debug("No owned validators have proposer duties in epoch {}", epoch);
      return;
    }

    // Gloas's get_shuffling_dependent_root(store, head, e) returns the block root at
    // start_of_(e-MIN_SEED_LOOKAHEAD) - 1. As far as MIN_SEED_LOOKAHEAD == 1,
    // for next-epoch duties, BlockProposalUtilFulu's
    // getBlockProposalDependentRoot returns the same value, so we reuse it here.
    final Bytes32 dependentRoot = proposerDuties.getDependentRoot();
    final int minSeedLookahead = spec.getGenesisSpec().getConfig().getMinSeedLookahead();
    checkArgument(
        minSeedLookahead == 1,
        "Proposer preferences can reuse the proposer duties dependent root only when "
            + "MIN_SEED_LOOKAHEAD is 1, but it is %s",
        minSeedLookahead);

    final ProposerPreferencesUtil preferencesUtil = spec.getProposerPreferencesUtil(epoch);

    forkProvider
        .getForkInfo(ownedProposerDuties.getFirst().getSlot())
        .thenCompose(
            forkInfo ->
                SafeFuture.collectAll(
                        ownedProposerDuties.stream()
                            .map(
                                duty ->
                                    createSignedProposerPreferences(
                                        duty, epoch, dependentRoot, forkInfo, preferencesUtil)))
                    .thenCompose(
                        signedPreferences -> {
                          final List<SignedProposerPreferences> preferencesList =
                              signedPreferences.stream().flatMap(Optional::stream).toList();
                          if (preferencesList.isEmpty()) {
                            return SafeFuture.COMPLETE;
                          }
                          LOG.debug("Publishing {} proposer preferences", preferencesList.size());
                          return validatorApiChannel
                              .sendSignedProposerPreferences(preferencesList)
                              .thenAccept(
                                  errors -> {
                                    if (!errors.isEmpty()) {
                                      throw new IllegalArgumentException(
                                          errors.stream()
                                              .map(SubmitDataError::message)
                                              .collect(Collectors.joining("; ")));
                                    }
                                    LOG.debug(
                                        "Proposer preferences published successfully for {} validators",
                                        preferencesList.size());
                                  });
                        }))
        .finish(error -> VALIDATOR_LOGGER.proposerPreferencesPublicationFailed(epoch, error));
  }

  private SafeFuture<Optional<SignedProposerPreferences>> createSignedProposerPreferences(
      final ProposerDuty duty,
      final UInt64 epoch,
      final Bytes32 dependentRoot,
      final ForkInfo forkInfo,
      final ProposerPreferencesUtil preferencesUtil) {
    final Optional<Validator> maybeValidator = ownedValidators.getValidator(duty.getPublicKey());
    if (maybeValidator.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    final Optional<Eth1Address> maybeFeeRecipient =
        proposerConfigPropertiesProvider.getFeeRecipient(duty.getPublicKey());
    if (maybeFeeRecipient.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    // duties are loaded ahead of time, so the gas limit must be the one scheduled for the duty
    // epoch
    final UInt64 targetGasLimit =
        proposerConfigPropertiesProvider.getGasLimit(duty.getPublicKey(), epoch);
    final Optional<ProposerPreferences> maybePreferences =
        preferencesUtil.createProposerPreferences(
            dependentRoot,
            duty.getSlot(),
            UInt64.valueOf(duty.getValidatorIndex()),
            maybeFeeRecipient.get(),
            targetGasLimit);
    if (maybePreferences.isEmpty()) {
      // Pre-Gloas, the util is NOOP, nothing to publish
      return SafeFuture.completedFuture(Optional.empty());
    }
    final ProposerPreferences preferences = maybePreferences.get();

    return maybeValidator
        .get()
        .getSigner()
        .signProposerPreferences(preferences, forkInfo)
        .thenApply(
            signature -> preferencesUtil.createSignedProposerPreferences(preferences, signature))
        .exceptionally(
            error -> {
              LOG.warn(
                  "Failed to sign proposer preferences for validator {}",
                  duty.getPublicKey(),
                  error);
              return Optional.empty();
            });
  }
}
