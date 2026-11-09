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

package tech.pegasys.teku.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.NodeSyncingException;

class DutyResultTest {

  private static final UInt64 SLOT = UInt64.valueOf(323);
  private static final String TYPE = "type";
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);
  private final BLSPublicKey validatorKey = dataStructureUtil.randomPublicKey();
  private final Set<String> validatorId = Set.of(validatorKey.toAbbreviatedString());

  @Test
  void shouldReportSuccess() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    DutyResult.success(root).report(TYPE, SLOT, validatorLogger);

    verify(validatorLogger).dutyCompleted(TYPE, SLOT, 1, Set.of(root), Optional.empty());
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  void shouldReportError() {
    final RuntimeException error = new RuntimeException("Oh no");
    DutyResult.forError(validatorKey, error).report(TYPE, SLOT, validatorLogger);

    verify(validatorLogger).dutyFailed(TYPE, SLOT, validatorId, error);
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  void shouldReportNodeSyncing() {
    DutyResult.forError(new NodeSyncingException()).report(TYPE, SLOT, validatorLogger);

    verify(validatorLogger).dutySkippedWhileSyncing(TYPE, SLOT, 1);
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  void shouldCombineSuccessResults() {
    final Bytes32 root1 = dataStructureUtil.randomBytes32();
    final Bytes32 root2 = dataStructureUtil.randomBytes32();

    final DutyResult combined =
        DutyResult.success(root1)
            .combine(DutyResult.success(root2))
            .combine(DutyResult.success(root1));
    combined.report(TYPE, SLOT, validatorLogger);

    verify(validatorLogger).dutyCompleted(TYPE, SLOT, 3, Set.of(root1, root2), Optional.empty());
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  void shouldCombineErrorResults() {
    final Exception exception1 = new RuntimeException("Oops");
    final Exception exception2 = new IllegalArgumentException("Nope");

    final DutyResult combined =
        DutyResult.forError(validatorKey, exception1)
            .combine(DutyResult.forError(validatorKey, exception2))
            .combine(DutyResult.forError(validatorKey, exception1));
    combined.report(TYPE, SLOT, validatorLogger);

    verify(validatorLogger).dutyFailed(TYPE, SLOT, validatorId, exception1);
    verify(validatorLogger).dutyFailed(TYPE, SLOT, validatorId, exception2);
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  void shouldCombineNodeSyncingResults() {
    final DutyResult combined =
        DutyResult.forError(new NodeSyncingException())
            .combine(DutyResult.forError(new NodeSyncingException()))
            .combine(DutyResult.forError(new NodeSyncingException()));
    combined.report(TYPE, SLOT, validatorLogger);

    verify(validatorLogger).dutySkippedWhileSyncing(TYPE, SLOT, 3);
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  void shouldCombineMixedResults() {
    final Bytes32 root1 = dataStructureUtil.randomBytes32();
    final Bytes32 root2 = dataStructureUtil.randomBytes32();
    final Exception exception1 = new RuntimeException("Nope");
    final Exception exception2 = new IllegalArgumentException("Oops");

    final DutyResult combined =
        DutyResult.success(root1)
            .combine(DutyResult.forError(validatorKey, exception1))
            .combine(DutyResult.forError(validatorKey, new NodeSyncingException()))
            .combine(DutyResult.forError(validatorKey, exception2))
            .combine(DutyResult.forError(validatorKey, new NodeSyncingException()))
            .combine(DutyResult.success(root2))
            .combine(DutyResult.success(root1));
    combined.report(TYPE, SLOT, validatorLogger);

    verify(validatorLogger).dutyCompleted(TYPE, SLOT, 3, Set.of(root1, root2), Optional.empty());
    verify(validatorLogger).dutySkippedWhileSyncing(TYPE, SLOT, 2);
    verify(validatorLogger).dutyFailed(TYPE, SLOT, validatorId, exception1);
    verify(validatorLogger).dutyFailed(TYPE, SLOT, validatorId, exception2);
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  void shouldCombineSummaryStrings() {

    final Bytes32 root1 = dataStructureUtil.randomBytes32();
    final Bytes32 root2 = dataStructureUtil.randomBytes32();
    final DutyResult combined =
        DutyResult.success(root1, List.of("a")).combine(DutyResult.success(root2, List.of("b")));
    combined.report(TYPE, SLOT, validatorLogger);
    verify(validatorLogger).dutyCompleted(TYPE, SLOT, 2, Set.of(root1, root2), Optional.of("a, b"));
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  void shouldCombineSafeFutureResults() {
    final Bytes32 root1 = dataStructureUtil.randomBytes32();
    final Bytes32 root2 = dataStructureUtil.randomBytes32();
    final Exception exception1 = new RuntimeException("Nope");
    final Exception exception2 = new IllegalArgumentException("Oops");
    final SafeFuture<DutyResult> combinedFuture =
        DutyResult.combine(
            List.of(
                SafeFuture.completedFuture(DutyResult.success(root1)),
                SafeFuture.completedFuture(DutyResult.forError(validatorKey, exception1)),
                SafeFuture.failedFuture(exception2),
                SafeFuture.completedFuture(
                    DutyResult.forError(validatorKey, new NodeSyncingException())),
                SafeFuture.failedFuture(new NodeSyncingException()),
                SafeFuture.completedFuture(DutyResult.success(root2))));

    assertThat(combinedFuture).isCompleted();
    safeJoin(combinedFuture).report(TYPE, SLOT, validatorLogger);
    verify(validatorLogger).dutyCompleted(TYPE, SLOT, 2, Set.of(root1, root2), Optional.empty());
    verify(validatorLogger).dutyFailed(TYPE, SLOT, validatorId, exception1);
    verify(validatorLogger).dutyFailed(TYPE, SLOT, Set.of(), exception2);
    verify(validatorLogger).dutySkippedWhileSyncing(TYPE, SLOT, 2);
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  void shouldWaitForFuturesToCompleteBeforeCombining() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    final SafeFuture<DutyResult> future1 = new SafeFuture<>();
    final SafeFuture<DutyResult> future2 = new SafeFuture<>();
    final SafeFuture<DutyResult> combinedFuture = DutyResult.combine(List.of(future1, future2));
    assertThat(combinedFuture).isNotDone();

    future1.complete(DutyResult.success(root));
    assertThat(combinedFuture).isNotDone();

    future2.complete(DutyResult.success(root));
    assertThat(combinedFuture).isCompleted();

    safeJoin(combinedFuture).report(TYPE, SLOT, validatorLogger);
    verify(validatorLogger).dutyCompleted(TYPE, SLOT, 2, Set.of(root), Optional.empty());
    verifyNoMoreInteractions(validatorLogger);
  }
}
