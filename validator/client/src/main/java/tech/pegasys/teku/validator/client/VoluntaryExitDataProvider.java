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

package tech.pegasys.teku.validator.client;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

public class VoluntaryExitDataProvider {
  private final Spec spec;
  private final KeyManager keyManager;
  private final ValidatorApiChannel validatorApiChannel;
  private final GenesisDataProvider genesisDataProvider;
  private final TimeProvider timeProvider;

  public VoluntaryExitDataProvider(
      final Spec spec,
      final KeyManager keyManager,
      final ValidatorApiChannel validatorApiChannel,
      final GenesisDataProvider genesisDataProvider,
      final TimeProvider timeProvider) {
    this.spec = spec;
    this.keyManager = keyManager;
    this.validatorApiChannel = validatorApiChannel;
    this.genesisDataProvider = genesisDataProvider;
    this.timeProvider = timeProvider;
  }

  public SafeFuture<SignedVoluntaryExit> getSignedVoluntaryExit(
      final BLSPublicKey publicKey, final Optional<UInt64> maybeEpoch) {
    return genesisDataProvider
        .getGenesisData()
        .thenCombine(
            getValidatorIndices(publicKey),
            (genesisData, indicesMap) -> {
              final UInt64 currentEpoch = calculateCurrentEpoch(genesisData.getGenesisTime());
              final UInt64 epoch = maybeEpoch.orElse(currentEpoch);
              final Bytes32 genesisRoot = genesisData.getGenesisValidatorsRoot();
              // Exits are validated against the state's fork, so a past exit epoch must not drag
              // the signing fork back with it - that produces an exit the beacon node rejects. A
              // future epoch keeps signing against the fork at that epoch, so pre-signing for an
              // upcoming fork is unaffected.
              final Fork fork = spec.getForkSchedule().getFork(epoch.max(currentEpoch));
              final ForkInfo forkInfo = new ForkInfo(fork, genesisRoot);
              final int validatorIndex =
                  Optional.ofNullable(indicesMap.get(publicKey))
                      .orElseThrow(
                          () ->
                              new BadRequestException(
                                  String.format("Public key %s is not found.", publicKey)));

              return createSignedVoluntaryExit(validatorIndex, publicKey, epoch, forkInfo);
            });
  }

  private SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(final BLSPublicKey publicKey) {
    return validatorApiChannel
        .getValidatorIndices(Set.of(publicKey))
        .thenCompose(
            maybeValidatorIndices ->
                maybeValidatorIndices
                    .map(SafeFuture::completedFuture)
                    .orElseGet(
                        () ->
                            SafeFuture.failedFuture(
                                new NodeDataUnavailableException(
                                    "Beacon node returned no validator index data"))));
  }

  UInt64 calculateCurrentEpoch(final UInt64 genesisTime) {
    final SpecVersion genesisSpec = spec.getGenesisSpec();
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    final UInt64 slot = genesisSpec.miscHelpers().computeSlotAtTime(genesisTime, currentTime);
    return spec.computeEpochAtSlot(slot);
  }

  SignedVoluntaryExit createSignedVoluntaryExit(
      final int validatorIndex,
      final BLSPublicKey publicKey,
      final UInt64 epoch,
      final ForkInfo forkInfo) {
    final VoluntaryExit message = new VoluntaryExit(epoch, UInt64.valueOf(validatorIndex));

    final Optional<SignedVoluntaryExit> maybeLocalExit =
        getExitForValidator(publicKey, message, forkInfo);
    if (maybeLocalExit.isPresent()) {
      return maybeLocalExit.get();
    }

    throw new BadRequestException(
        String.format(
            "Validator %s is not in the list of keys managed by this service.", publicKey));
  }

  Optional<SignedVoluntaryExit> getExitForValidator(
      final BLSPublicKey publicKey, final VoluntaryExit message, final ForkInfo forkInfo) {
    final Optional<Validator> maybeValidator = keyManager.getValidatorByPublicKey(publicKey);

    if (maybeValidator.isPresent()) {
      final Validator validator = maybeValidator.get();
      final BLSSignature signature =
          validator.getSigner().signVoluntaryExit(message, forkInfo).join();
      return Optional.of(new SignedVoluntaryExit(message, signature));
    }
    return Optional.empty();
  }
}
