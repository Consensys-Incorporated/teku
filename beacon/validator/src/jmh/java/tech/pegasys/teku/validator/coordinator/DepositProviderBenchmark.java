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

package tech.pegasys.teku.validator.coordinator;

import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.ethereum.pow.api.Deposit;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

@Fork(1)
@State(Scope.Thread)
public class DepositProviderBenchmark {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final int depositEventCount = 1000;

  private final List<DepositsFromBlockEvent> events =
      IntStream.range(0, depositEventCount)
          .mapToObj(
              index ->
                  DepositsFromBlockEvent.create(
                      UInt64.valueOf(index),
                      Bytes32.ZERO,
                      UInt64.valueOf(index),
                      Stream.of(
                          new Deposit(
                              dataStructureUtil.randomPublicKey(),
                              dataStructureUtil.randomBytes32(),
                              dataStructureUtil.randomSignature(),
                              spec.getGenesisSpecConfig().getMaxEffectiveBalance(),
                              UInt64.valueOf(index)))))
          .toList();

  private final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final DepositProvider depositProvider =
      new DepositProvider(
          metricsSystem,
          mock(RecentChainData.class),
          new Eth1DataCache(spec, metricsSystem, new Eth1VotingPeriod(spec)),
          mock(StorageUpdateChannel.class),
          mock(Eth1DepositStorageChannel.class),
          spec);

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void addDeposits() {
    events.forEach(depositProvider::onDepositsFromBlock);
  }
}
