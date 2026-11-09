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

import static tech.pegasys.teku.validator.client.ValidatorRegistrator.VALIDATOR_BUILDER_PUBLIC_KEY;

import java.util.Optional;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.signatures.Signer;

public class SignedValidatorRegistrationFactory {
  private static final Logger LOG = LogManager.getLogger();

  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider;
  private final TimeProvider timeProvider;

  public SignedValidatorRegistrationFactory(
      final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider,
      final TimeProvider timeProvider) {
    this.proposerConfigPropertiesProvider = proposerConfigPropertiesProvider;
    this.timeProvider = timeProvider;
  }

  public SafeFuture<SignedValidatorRegistration> createSignedValidatorRegistration(
      final Validator validator,
      final Optional<SignedValidatorRegistration> oldValidatorRegistration,
      final Consumer<Throwable> errorHandler) {

    final BLSPublicKey publicKey = validator.getPublicKey();

    final Optional<Eth1Address> feeRecipient =
        proposerConfigPropertiesProvider.getFeeRecipient(publicKey);

    if (feeRecipient.isEmpty()) {
      return SafeFuture.failedFuture(
          new IllegalStateException(
              String.format(
                  "Fee recipient not configured for %s. Will skip registering it.", publicKey)));
    }

    final UInt64 gasLimit = proposerConfigPropertiesProvider.getGasLimit(publicKey);

    final Optional<UInt64> maybeTimestampOverride =
        proposerConfigPropertiesProvider.getBuilderRegistrationTimestampOverride(publicKey);

    final ValidatorRegistration validatorRegistration =
        createValidatorRegistration(
            VALIDATOR_BUILDER_PUBLIC_KEY.apply(validator, proposerConfigPropertiesProvider),
            feeRecipient.get(),
            gasLimit,
            maybeTimestampOverride.orElse(timeProvider.getTimeInSeconds()));

    return oldValidatorRegistration
        .filter(
            cachedValidatorRegistration -> {
              final boolean needsUpdate =
                  registrationNeedsUpdating(
                      cachedValidatorRegistration.getMessage(),
                      validatorRegistration,
                      maybeTimestampOverride);
              if (needsUpdate) {
                LOG.debug(
                    "The cached registration for {} needs updating. Will create a new one.",
                    publicKey);
              }
              return !needsUpdate;
            })
        .map(SafeFuture::completedFuture)
        .orElseGet(
            () -> {
              final Signer signer = validator.getSigner();
              return signAndCacheValidatorRegistration(publicKey, validatorRegistration, signer);
            })
        .whenException(errorHandler);
  }

  private SafeFuture<SignedValidatorRegistration> signAndCacheValidatorRegistration(
      final BLSPublicKey cacheKey,
      final ValidatorRegistration validatorRegistration,
      final Signer signer) {
    return signer
        .signValidatorRegistration(validatorRegistration)
        .thenApply(
            signature -> {
              final SignedValidatorRegistration signedValidatorRegistration =
                  ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
                      validatorRegistration, signature);
              LOG.debug("Validator registration signed for {}", cacheKey);
              return signedValidatorRegistration;
            });
  }

  private boolean registrationNeedsUpdating(
      final ValidatorRegistration cachedValidatorRegistration,
      final ValidatorRegistration newValidatorRegistration,
      final Optional<UInt64> newMaybeTimestampOverride) {
    final boolean cachedTimestampIsDifferentThanOverride =
        newMaybeTimestampOverride
            .map(
                newTimestampOverride ->
                    !cachedValidatorRegistration.getTimestamp().equals(newTimestampOverride))
            .orElse(false);
    return !cachedValidatorRegistration
            .getFeeRecipient()
            .equals(newValidatorRegistration.getFeeRecipient())
        || !cachedValidatorRegistration.getGasLimit().equals(newValidatorRegistration.getGasLimit())
        || !cachedValidatorRegistration
            .getPublicKey()
            .equals(newValidatorRegistration.getPublicKey())
        || cachedTimestampIsDifferentThanOverride;
  }

  private ValidatorRegistration createValidatorRegistration(
      final BLSPublicKey publicKey,
      final Eth1Address feeRecipient,
      final UInt64 gasLimit,
      final UInt64 timestamp) {
    return ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA.create(
        feeRecipient, gasLimit, timestamp, publicKey);
  }
}
