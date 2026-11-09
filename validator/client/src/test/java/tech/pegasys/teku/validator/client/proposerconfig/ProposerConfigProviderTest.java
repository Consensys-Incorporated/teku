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

package tech.pegasys.teku.validator.client.proposerconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.validator.client.proposerconfig.AbstractProposerConfigProvider.LAST_PROPOSER_CONFIG_VALIDITY_PERIOD;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.validator.client.ProposerConfig;
import tech.pegasys.teku.validator.client.proposerconfig.loader.ProposerConfigLoader;

public class ProposerConfigProviderTest {
  private static final String SOURCE = "some/file/path";

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final ProposerConfigLoader proposerConfigLoader = mock(ProposerConfigLoader.class);

  private final ProposerConfig proposerConfigA =
      new ProposerConfig(ImmutableMap.of(), new ProposerConfig.Config(Eth1Address.ZERO, null));

  private final ProposerConfig proposerConfigB =
      new ProposerConfig(
          ImmutableMap.of(),
          new ProposerConfig.Config(
              Eth1Address.fromHexString("0x6e35733c5af9B61374A128e6F85f553aF09ff89A"), null));

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);

  private final ProposerConfigProvider proposerConfigProvider =
      ProposerConfigProvider.create(
          asyncRunner, true, proposerConfigLoader, timeProvider, Optional.of(SOURCE));
  private final URL sourceUrl;

  public ProposerConfigProviderTest() throws MalformedURLException {
    sourceUrl = new File(SOURCE).toURI().toURL();
  }

  @Test
  void getProposerConfig_shouldReturnConfig() {
    SafeFuture<Optional<ProposerConfig>> futureMaybeConfig =
        proposerConfigProvider.getProposerConfig();

    assertThat(futureMaybeConfig).isNotCompleted();

    when(proposerConfigLoader.getProposerConfig(sourceUrl)).thenReturn(proposerConfigA);
    asyncRunner.executeQueuedActions();

    assertThat(futureMaybeConfig).isCompletedWithValue(Optional.of(proposerConfigA));
  }

  @Test
  void getProposerConfig_onErrorShouldThrowWhenNoLastConfigAvailable() {
    SafeFuture<Optional<ProposerConfig>> futureMaybeConfig =
        proposerConfigProvider.getProposerConfig();

    when(proposerConfigLoader.getProposerConfig(sourceUrl))
        .thenThrow(new RuntimeException("error"));
    asyncRunner.executeQueuedActions();

    assertThat(futureMaybeConfig).isCompletedExceptionally();
  }

  @Test
  void getProposerConfig_onErrorShouldReturnLastConfigWhenLastConfigAvailable() {
    SafeFuture<Optional<ProposerConfig>> futureMaybeConfig =
        proposerConfigProvider.getProposerConfig();

    when(proposerConfigLoader.getProposerConfig(sourceUrl)).thenReturn(proposerConfigA);
    asyncRunner.executeQueuedActions();

    assertThat(futureMaybeConfig).isCompletedWithValue(Optional.of(proposerConfigA));

    timeProvider.advanceTimeBySeconds(LAST_PROPOSER_CONFIG_VALIDITY_PERIOD + 10);
    futureMaybeConfig = proposerConfigProvider.getProposerConfig();

    when(proposerConfigLoader.getProposerConfig(sourceUrl))
        .thenThrow(new RuntimeException("error"));
    asyncRunner.executeQueuedActions();

    verify(proposerConfigLoader, times(2)).getProposerConfig(any());
    assertThat(futureMaybeConfig).isCompletedWithValue(Optional.of(proposerConfigA));
  }

  @Test
  void getProposerConfig_shouldReturnLastConfigWhenLastConfigAvailableAndNotExpired() {
    SafeFuture<Optional<ProposerConfig>> futureMaybeConfig =
        proposerConfigProvider.getProposerConfig();

    when(proposerConfigLoader.getProposerConfig(sourceUrl)).thenReturn(proposerConfigA);
    asyncRunner.executeQueuedActions();

    assertThat(futureMaybeConfig).isCompletedWithValue(Optional.of(proposerConfigA));

    timeProvider.advanceTimeBySeconds(LAST_PROPOSER_CONFIG_VALIDITY_PERIOD - 10);
    futureMaybeConfig = proposerConfigProvider.getProposerConfig();

    asyncRunner.executeQueuedActions();

    verify(proposerConfigLoader, times(1)).getProposerConfig(sourceUrl);
    assertThat(futureMaybeConfig).isCompletedWithValue(Optional.of(proposerConfigA));
  }

  @Test
  void getProposerConfig_shouldRefreshWhenLastConfigAvailableButExpired() {
    SafeFuture<Optional<ProposerConfig>> futureMaybeConfig =
        proposerConfigProvider.getProposerConfig();

    when(proposerConfigLoader.getProposerConfig(sourceUrl)).thenReturn(proposerConfigA);
    asyncRunner.executeQueuedActions();

    assertThat(futureMaybeConfig).isCompletedWithValue(Optional.of(proposerConfigA));

    timeProvider.advanceTimeBySeconds(LAST_PROPOSER_CONFIG_VALIDITY_PERIOD + 10);
    futureMaybeConfig = proposerConfigProvider.getProposerConfig();

    when(proposerConfigLoader.getProposerConfig(sourceUrl)).thenReturn(proposerConfigB);
    asyncRunner.executeQueuedActions();

    verify(proposerConfigLoader, times(2)).getProposerConfig(sourceUrl);
    assertThat(futureMaybeConfig).isCompletedWithValue(Optional.of(proposerConfigB));
  }

  @Test
  void getProposerConfig_onConcurrentCallsShouldMergeFutures() {
    SafeFuture<Optional<ProposerConfig>> futureMaybeConfig =
        proposerConfigProvider.getProposerConfig();

    SafeFuture<Optional<ProposerConfig>> futureMaybeConfig2 =
        proposerConfigProvider.getProposerConfig();
    assertThat(futureMaybeConfig2).isEqualTo(futureMaybeConfig);
    assertThat(futureMaybeConfig2).isNotCompleted();

    when(proposerConfigLoader.getProposerConfig(sourceUrl)).thenReturn(proposerConfigA);
    asyncRunner.executeQueuedActions();

    assertThat(futureMaybeConfig).isCompletedWithValue(Optional.of(proposerConfigA));
  }

  @Test
  void getProposerConfig_shouldAlwaysReturnFirstValidConfigWhenRefreshIsFalse() {
    final ProposerConfigProvider proposerConfigProvider =
        ProposerConfigProvider.create(
            asyncRunner, false, proposerConfigLoader, timeProvider, Optional.of(SOURCE));

    SafeFuture<Optional<ProposerConfig>> futureMaybeConfig =
        proposerConfigProvider.getProposerConfig();

    assertThat(futureMaybeConfig).isNotCompleted();

    when(proposerConfigLoader.getProposerConfig(sourceUrl)).thenReturn(proposerConfigA);
    asyncRunner.executeQueuedActions();

    assertThat(futureMaybeConfig).isCompletedWithValue(Optional.of(proposerConfigA));

    when(proposerConfigLoader.getProposerConfig(sourceUrl)).thenReturn(proposerConfigB);

    futureMaybeConfig = proposerConfigProvider.getProposerConfig();
    assertThat(futureMaybeConfig).isCompletedWithValue(Optional.of(proposerConfigA));
  }
}
