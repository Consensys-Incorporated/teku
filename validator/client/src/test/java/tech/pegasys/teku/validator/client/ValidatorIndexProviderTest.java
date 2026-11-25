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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.generator.signatures.NoOpLocalSigner.NO_OP_SIGNER;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

class ValidatorIndexProviderTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  @Test
  void shouldLoadValidatorKeys() {
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final OwnedValidators validators = ownedValidatorsWithKeys(key1, key2, key3);
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(validators, validatorApiChannel, asyncRunner);

    when(validatorApiChannel.getValidatorIndices(validators.getPublicKeys()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(key1, 1, key2, 20, key3, 300))));
    provider.lookupValidators();
    final IntCollection actual = provider.getValidatorIndices().getImmediately();
    assertThat(actual).containsExactlyInAnyOrderElementsOf(IntArrayList.of(1, 20, 300));
  }

  @Test
  void shouldRetryWhenLoadRequestFails() {
    final OwnedValidators validators = ownedValidatorsWithKeys(key1);
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(validators, validatorApiChannel, asyncRunner);

    when(validatorApiChannel.getValidatorIndices(validators.getPublicKeys()))
        .thenReturn(SafeFuture.failedFuture(new IOException("Server not available")))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(key1, 1))));
    provider.lookupValidators();
    final SafeFuture<IntCollection> future = provider.getValidatorIndices();
    assertThat(future).isNotCompleted();

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    assertThat(provider.getValidatorIndices()).isCompletedWithValue(IntArrayList.of(1));
  }

  @Test
  void shouldRetryWhenValidatorIndexDataIsUnavailable() {
    final OwnedValidators validators = ownedValidatorsWithKeys(key1);
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(validators, validatorApiChannel, asyncRunner);

    when(validatorApiChannel.getValidatorIndices(validators.getPublicKeys()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(key1, 1))));
    provider.lookupValidators();
    final SafeFuture<IntCollection> future = provider.getValidatorIndices();
    assertThat(future).isNotCompleted();

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    assertThat(provider.getValidatorIndices()).isCompletedWithValue(IntArrayList.of(1));
  }

  @Test
  void shouldLookupValidatorKeysThatWerePreviouslyUnknown() {
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final OwnedValidators ownedValidators = ownedValidatorsWithKeys(key1, key2, key3);
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(ownedValidators, validatorApiChannel, asyncRunner);

    when(validatorApiChannel.getValidatorIndices(ownedValidators.getPublicKeys()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(key2, 20))));
    provider.lookupValidators();

    assertThat(provider.getValidatorIndices()).isCompletedWithValue(IntArrayList.of(20));

    when(validatorApiChannel.getValidatorIndices(Set.of(key1, key3)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(key1, 1, key3, 300))));
    provider.lookupValidators();
    final IntCollection actual = provider.getValidatorIndices().getImmediately();
    assertThat(actual).containsExactlyInAnyOrderElementsOf(IntArrayList.of(1, 20, 300));
  }

  @Test
  void shouldNotMakeConcurrentRequests() {
    final SafeFuture<Optional<Map<BLSPublicKey, Integer>>> result = new SafeFuture<>();
    when(validatorApiChannel.getValidatorIndices(Set.of(key1))).thenReturn(result);

    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(ownedValidatorsWithKeys(key1), validatorApiChannel, asyncRunner);

    provider.lookupValidators();
    verify(validatorApiChannel).getValidatorIndices(Set.of(key1));

    provider.lookupValidators();
    verifyNoMoreInteractions(validatorApiChannel);

    // Can request again once the request completes.
    result.complete(Optional.of(Collections.emptyMap()));
    provider.lookupValidators();
    verify(validatorApiChannel, times(2)).getValidatorIndices(Set.of(key1));
  }

  @Test
  void shouldNotMakeRequestWhenAllValidatorsAreKnown() {
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(ownedValidatorsWithKeys(key1), validatorApiChannel, asyncRunner);

    when(validatorApiChannel.getValidatorIndices(Set.of(key1)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(key1, 1))));
    provider.lookupValidators();

    verify(validatorApiChannel).getValidatorIndices(any());

    provider.lookupValidators();

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldWaitForFirstSuccessfulRequestBeforeLookingUpValidatorIndices() {
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(ownedValidatorsWithKeys(key1), validatorApiChannel, asyncRunner);

    final SafeFuture<Optional<Map<BLSPublicKey, Integer>>> requestResult = new SafeFuture<>();
    when(validatorApiChannel.getValidatorIndices(Set.of(key1))).thenReturn(requestResult);

    final SafeFuture<IntCollection> result = provider.getValidatorIndices();
    assertThat(result).isNotDone();

    provider.lookupValidators();
    assertThat(result).isNotDone();

    requestResult.complete(Optional.of(Map.of(key1, 1)));
    assertThat(result).isCompletedWithValue(IntList.of(1));
  }

  @Test
  void shouldGetPublicKeyByIndex() {
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final OwnedValidators validators = ownedValidatorsWithKeys(key1, key2, key3);
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(validators, validatorApiChannel, asyncRunner);

    when(validatorApiChannel.getValidatorIndices(validators.getPublicKeys()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(key1, 1, key2, 20, key3, 300))));
    provider.lookupValidators();
    assertThat(provider.getPublicKey(1)).contains(key1);
    assertThat(provider.getPublicKey(20)).contains(key2);
    assertThat(provider.getPublicKey(300)).contains(key3);
    assertThat(provider.getPublicKey(400)).isEmpty();
  }

  private OwnedValidators ownedValidatorsWithKeys(final BLSPublicKey... keys) {
    final Map<BLSPublicKey, Validator> validators = new HashMap<>();
    for (BLSPublicKey key : keys) {
      validators.put(key, new Validator(key, NO_OP_SIGNER, Optional::empty));
    }
    return new OwnedValidators(validators);
  }
}
