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

package tech.pegasys.teku.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.server.BatchingVoteUpdateChannel;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.storageSystem.FileBackedStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@Fork(1)
@State(Scope.Thread)
public class VoteUpdateChannelBenchmark {
  public static final int UPDATE_COUNT = 15000;
  private final AsyncRunnerFactory asyncRunnerFactory =
      AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(new NoOpMetricsSystem()));

  private static final List<Map<UInt64, VoteTracker>> UPDATES = new ArrayList<>();

  static {
    final Random random = new Random();
    for (int i = 0; i < UPDATE_COUNT; i++) {
      final int voteCount = random.nextInt(1000);
      final Map<UInt64, VoteTracker> votes = new HashMap<>();
      for (int j = 0; j < voteCount; j++) {
        votes.put(UInt64.valueOf(random.nextInt(2000)), VoteTracker.DEFAULT);
      }
      UPDATES.add(votes);
    }
  }

  private VoteUpdateChannel realChannel;
  private AsyncRunnerEventThread eventThread;
  private BatchingVoteUpdateChannel batchingChannel;
  private StorageSystem storageSystem;
  private Path tempDirectory;

  @Setup
  public void setup() throws Exception {
    tempDirectory = Files.createTempDirectory(getClass().getSimpleName());
    storageSystem =
        FileBackedStorageSystemBuilder.create()
            .specProvider(TestSpecFactory.createDefault())
            .dataDir(tempDirectory)
            .version(DatabaseVersion.DEFAULT_VERSION)
            .build();
    realChannel = storageSystem.chainStorage();
    eventThread = new AsyncRunnerEventThread("batch", asyncRunnerFactory);
    eventThread.start();
    batchingChannel = new BatchingVoteUpdateChannel(realChannel, eventThread);
  }

  @TearDown
  public void tearDown() throws Exception {
    eventThread.stop();
    storageSystem.close();
    asyncRunnerFactory.shutdown();
    FileUtils.deleteDirectory(tempDirectory.toFile());
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void storeVotesDirect() {
    for (Map<UInt64, VoteTracker> update : UPDATES) {
      realChannel.onVotesUpdated(update);
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void storeVotesBatched() {
    for (Map<UInt64, VoteTracker> update : UPDATES) {
      batchingChannel.onVotesUpdated(update);
    }
    batchingChannel.awaitCompletion();
  }
}
