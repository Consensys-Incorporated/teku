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

package tech.pegasys.teku.validator.beaconnode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

class GenesisDataProviderTest {

  private static final UInt64 GENESIS_TIME = UInt64.valueOf(12341234);
  private static final Bytes32 GENESIS_VALIDATORS_ROOT = Bytes32.fromHexString("0x01");
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private final GenesisDataProvider genesisDataProvider =
      new GenesisDataProvider(asyncRunner, validatorApiChannel);

  @Test
  void shouldRequestGenesisTimeWhenNotPreviouslyLoaded() {
    final SafeFuture<Optional<GenesisData>> request = new SafeFuture<>();
    when(validatorApiChannel.getGenesisData()).thenReturn(request);

    final SafeFuture<UInt64> result = genesisDataProvider.getGenesisTime();
    assertThat(result).isNotDone();

    request.complete(Optional.of(new GenesisData(GENESIS_TIME, GENESIS_VALIDATORS_ROOT)));
    assertThat(result).isCompletedWithValue(GENESIS_TIME);
  }

  @Test
  void shouldReturnCachedGenesisTimeWhenPreviouslyLoaded() {
    when(validatorApiChannel.getGenesisData())
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new GenesisData(GENESIS_TIME, GENESIS_VALIDATORS_ROOT))));
    assertThat(genesisDataProvider.getGenesisData())
        .isCompletedWithValue(new GenesisData(GENESIS_TIME, GENESIS_VALIDATORS_ROOT));
    verify(validatorApiChannel).getGenesisData();

    // Subsequent requests just return the cached version
    assertThat(genesisDataProvider.getGenesisData())
        .isCompletedWithValue(new GenesisData(GENESIS_TIME, GENESIS_VALIDATORS_ROOT));
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldRetryWhenGenesisTimeFailsToLoad() {
    when(validatorApiChannel.getGenesisData())
        .thenReturn(failedFuture(new RuntimeException("Nope")))
        .thenReturn(
            completedFuture(Optional.of(new GenesisData(GENESIS_TIME, GENESIS_VALIDATORS_ROOT))));

    // First request fails
    final SafeFuture<UInt64> result = genesisDataProvider.getGenesisTime();
    verify(validatorApiChannel).getGenesisData();
    assertThat(result).isNotDone();
    Assertions.assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // Retry is scheduled.
    asyncRunner.executeQueuedActions();
    verify(validatorApiChannel, times(2)).getGenesisData();
    assertThat(result).isCompletedWithValue(GENESIS_TIME);
  }

  @Test
  void shouldRetryWhenGenesisTimeIsNotYetKnown() {
    when(validatorApiChannel.getGenesisData())
        .thenReturn(completedFuture(Optional.empty()))
        .thenReturn(
            completedFuture(Optional.of(new GenesisData(GENESIS_TIME, GENESIS_VALIDATORS_ROOT))));

    // First request fails
    final SafeFuture<UInt64> result = genesisDataProvider.getGenesisTime();
    verify(validatorApiChannel).getGenesisData();
    assertThat(result).isNotDone();
    Assertions.assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // Retry is scheduled.
    asyncRunner.executeQueuedActions();
    verify(validatorApiChannel, times(2)).getGenesisData();
    assertThat(result).isCompletedWithValue(GENESIS_TIME);
  }
}
