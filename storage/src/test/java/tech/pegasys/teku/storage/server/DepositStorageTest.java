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

package tech.pegasys.teku.storage.server;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystemArgumentsProvider;

public class DepositStorageTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private DepositStorage depositStorage;

  private final DepositsFromBlockEvent block99 =
      dataStructureUtil.randomDepositsFromBlockEvent(99L, 0, 10);
  private final DepositsFromBlockEvent block100 =
      dataStructureUtil.randomDepositsFromBlockEvent(100L, 10, 20);
  private final DepositsFromBlockEvent block101 =
      dataStructureUtil.randomDepositsFromBlockEvent(101L, 20, 21);

  @TempDir Path dataDirectory;
  private StorageSystem storageSystem;
  private Database database;

  private void setup(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    storageSystem = storageSystemSupplier.get(dataDirectory, spec);
    database = storageSystem.database();

    depositStorage = storageSystem.createDepositStorage();
  }

  @AfterEach
  void cleanUp() throws Exception {
    database.close();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldRecordAndRetrieveDepositEvents(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);

    final UInt64 firstBlock = dataStructureUtil.randomUInt64();
    final DepositsFromBlockEvent event1 =
        dataStructureUtil.randomDepositsFromBlockEvent(firstBlock, 0, 10);
    final DepositsFromBlockEvent event2 =
        dataStructureUtil.randomDepositsFromBlockEvent(firstBlock.plus(ONE), 10, 11);

    database.addDepositsFromBlockEvent(event1);
    database.addDepositsFromBlockEvent(event2);
    try (Stream<DepositsFromBlockEvent> events = database.streamDepositsFromBlocks()) {
      assertThat(events.collect(toList())).containsExactly(event1, event2);
    }
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldRemoveDepositEvents(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block99);
    database.addDepositsFromBlockEvent(block100);
    database.addDepositsFromBlockEvent(block101);

    SafeFuture<Boolean> removeFuture = depositStorage.removeDepositEvents();
    assertThat(removeFuture).isCompleted();
    assertThat(removeFuture.get()).isTrue();

    try (Stream<DepositsFromBlockEvent> deposits = database.streamDepositsFromBlocks()) {
      assertThat(deposits.collect(toList())).isEmpty();
    }
  }
}
