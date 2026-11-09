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

package tech.pegasys.teku.statetransition.datacolumns.db;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDBStub;
import tech.pegasys.teku.statetransition.datacolumns.util.StubAsync;

@SuppressWarnings("FutureReturnValueIgnored")
public class ColumnIdCachingDasDbTest {
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

  private final Duration dbDelay = ofMillis(5);
  private final StubAsync stubAsync = new StubAsync();

  private final DataColumnSidecarDBStub db = new DataColumnSidecarDBStub();
  private final DataColumnSidecarDB asyncDb =
      new DelayedDasDb(this.db, stubAsync.getStubAsyncRunner(), dbDelay);

  private final int slotReadCacheSize = 2;
  private final int sidecarsWriteCacheSize = 2;
  private final ColumnIdCachingDasDb columnIdCachingDb =
      new ColumnIdCachingDasDb(asyncDb, __ -> 128, slotReadCacheSize, sidecarsWriteCacheSize);

  private DataColumnSidecar createSidecar(final int slot, final int index) {
    final UInt64 slotU = UInt64.valueOf(slot);
    final BeaconBlockBody beaconBlockBody =
        dataStructureUtil.randomBeaconBlockBodyWithCommitments(1);
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(slotU, beaconBlockBody);
    final SignedBeaconBlock signedBlock = dataStructureUtil.signedBlock(block);
    return dataStructureUtil.randomDataColumnSidecar(signedBlock.asHeader(), UInt64.valueOf(index));
  }

  @Test
  void sanityTest() {
    final SafeFuture<List<DataColumnSlotAndIdentifier>> res1 =
        columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));

    assertThat(res1).isNotDone();
    stubAsync.advanceTimeGradually(dbDelay);
    assertThat(res1).isCompletedWithValue(emptyList());
    final long reads0 = db.getDbReadCounter().get();
    assertThat(reads0).isGreaterThan(0);

    final SafeFuture<List<DataColumnSlotAndIdentifier>> res2 =
        columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));

    assertThat(res2).isCompletedWithValue(emptyList());
    final long reads1 = db.getDbReadCounter().get();
    assertThat(reads1).isEqualTo(reads0);
  }

  @Test
  void checkReadCacheIsUpdated() {
    final SafeFuture<List<DataColumnSlotAndIdentifier>> res1 =
        columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));

    stubAsync.advanceTimeGradually(ofMillis(1));

    final SafeFuture<Void> addCompleteFuture = columnIdCachingDb.addSidecar(createSidecar(777, 77));
    stubAsync.advanceTimeGraduallyUntilAllDone(ofSeconds(1));

    assertThat(res1)
        .isCompleted(); // no assumptions on result: cache may or may not pick up latest changes
    assertThat(addCompleteFuture).isCompleted();
    final long reads0 = db.getDbReadCounter().get();
    assertThat(reads0).isGreaterThan(0);

    final SafeFuture<List<DataColumnSlotAndIdentifier>> res2 =
        columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));
    stubAsync.advanceTimeGradually(dbDelay);

    assertThat(res2).isCompletedWithValueMatching(l -> !l.isEmpty());
  }

  @Test
  void checkWriteCacheIsUsed() {
    final SafeFuture<List<DataColumnSlotAndIdentifier>> res1 =
        columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));

    stubAsync.advanceTimeGradually(ofMillis(1));

    final DataColumnSidecar sidecar = createSidecar(777, 77);
    final SafeFuture<Void> addCompleteFuture1 = columnIdCachingDb.addSidecar(sidecar);
    stubAsync.advanceTimeGraduallyUntilAllDone(ofSeconds(1));

    assertThat(res1)
        .isCompleted(); // no assumptions on result: cache may or may not pick up latest changes
    assertThat(addCompleteFuture1).isCompleted();
    final long reads1 = db.getDbReadCounter().get();
    assertThat(reads1).isEqualTo(1);
    final long writes1 = db.getDbWriteCounter().get();
    assertThat(writes1).isEqualTo(1);

    final SafeFuture<List<DataColumnSlotAndIdentifier>> res2 =
        columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));
    stubAsync.advanceTimeGradually(dbDelay);

    assertThat(res2).isCompletedWithValueMatching(l -> !l.isEmpty());

    final long reads2 = db.getDbReadCounter().get();
    assertThat(reads2).isEqualTo(2);
    final long writes2 = db.getDbWriteCounter().get();
    assertThat(writes2).isEqualTo(writes1);

    // Retry saving the same sidecar, db should not be used
    final SafeFuture<Void> addCompleteFuture2 = columnIdCachingDb.addSidecar(sidecar);
    stubAsync.advanceTimeGraduallyUntilAllDone(ofSeconds(1));

    assertThat(addCompleteFuture2).isCompleted();
    final long reads3 = db.getDbReadCounter().get();
    assertThat(reads3).isEqualTo(reads2);
    final long writes3 = db.getDbWriteCounter().get();
    assertThat(writes3).isEqualTo(writes1);
  }

  @Test
  void checkCacheIsPruned() {
    for (int i = 0; i < slotReadCacheSize + 1; i++) {
      columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777 + i));
    }
    stubAsync.advanceTimeGradually(dbDelay);
    final long reads0 = db.getDbReadCounter().get();
    assertThat(reads0).isGreaterThan(0);

    columnIdCachingDb.getColumnIdentifiers(UInt64.valueOf(777));
    stubAsync.advanceTimeGradually(dbDelay);
    final long reads1 = db.getDbReadCounter().get();
    // the first cache entry (for slot 777) should be evicted and a query to underlying db should be
    // done
    assertThat(reads1).isGreaterThan(reads0);
  }
}
