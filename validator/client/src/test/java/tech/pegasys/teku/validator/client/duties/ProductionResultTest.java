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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;

class ProductionResultTest {
  private static final String MESSAGE1 = "Message1";
  private static final String MESSAGE2 = "Message2";
  private static final String MESSAGE3 = "Message3";
  private static final UInt64 SLOT = UInt64.valueOf(22323);
  private static final String PRODUCED_TYPE = "send";
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);

  @SuppressWarnings("unchecked")
  private final Function<List<String>, SafeFuture<List<SubmitDataError>>> sendFunction =
      mock(Function.class);

  @Test
  void send_shouldSendCreatedMessages() {
    when(sendFunction.apply(List.of(MESSAGE1, MESSAGE2)))
        .thenReturn(SafeFuture.completedFuture(emptyList()));
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final ProductionResult<String> prodResult1 =
        ProductionResult.success(dataStructureUtil.randomPublicKey(), blockRoot, MESSAGE1);
    final ProductionResult<String> prodResult2 =
        ProductionResult.success(dataStructureUtil.randomPublicKey(), blockRoot, MESSAGE2);

    final SafeFuture<DutyResult> result =
        ProductionResult.send(List.of(prodResult1, prodResult2), sendFunction);

    assertThatSafeFuture(result).isCompletedWithValue(DutyResult.success(blockRoot, 2));
  }

  @Test
  void send_shouldReportFailuresFromCreatedMessages() {
    when(sendFunction.apply(List.of(MESSAGE1, MESSAGE2, MESSAGE3)))
        .thenReturn(SafeFuture.completedFuture(List.of(new SubmitDataError(ONE, "No dice"))));
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final ProductionResult<String> prodResult1 =
        ProductionResult.success(dataStructureUtil.randomPublicKey(), blockRoot, MESSAGE1);
    final ProductionResult<String> prodResult2 =
        ProductionResult.success(dataStructureUtil.randomPublicKey(), blockRoot, MESSAGE2);
    final ProductionResult<String> prodResult3 =
        ProductionResult.success(dataStructureUtil.randomPublicKey(), blockRoot, MESSAGE3);

    final SafeFuture<DutyResult> result =
        ProductionResult.send(List.of(prodResult1, prodResult2, prodResult3), sendFunction);

    assertThatSafeFuture(result).isCompleted();
    final DutyResult dutyResult = safeJoin(result);
    dutyResult.report(PRODUCED_TYPE, SLOT, validatorLogger);

    verify(validatorLogger)
        .dutyCompleted(PRODUCED_TYPE, SLOT, 2, Set.of(blockRoot), Optional.empty());
    verify(validatorLogger)
        .dutyFailed(
            eq(PRODUCED_TYPE),
            eq(SLOT),
            eq(formatKeys(prodResult2.getValidatorPublicKeys())),
            any(RestApiReportedException.class));
  }

  @Test
  void send_shouldNotSendMessagesThatFailedToBeProduced() {
    when(sendFunction.apply(List.of(MESSAGE2))).thenReturn(SafeFuture.completedFuture(emptyList()));

    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final IllegalArgumentException error = new IllegalArgumentException("Nope");
    final ProductionResult<String> prodResult1 =
        ProductionResult.failure(dataStructureUtil.randomPublicKey(), error);
    final ProductionResult<String> prodResult2 =
        ProductionResult.success(dataStructureUtil.randomPublicKey(), blockRoot, MESSAGE2);

    final SafeFuture<DutyResult> result =
        ProductionResult.send(List.of(prodResult1, prodResult2), sendFunction);

    assertThatSafeFuture(result).isCompleted();
    final DutyResult dutyResult = safeJoin(result);
    dutyResult.report(PRODUCED_TYPE, SLOT, validatorLogger);

    verify(validatorLogger)
        .dutyCompleted(PRODUCED_TYPE, SLOT, 1, Set.of(blockRoot), Optional.empty());
    verify(validatorLogger)
        .dutyFailed(PRODUCED_TYPE, SLOT, formatKeys(prodResult1.getValidatorPublicKeys()), error);
  }

  private Set<String> formatKeys(final Collection<BLSPublicKey> keys) {
    return keys.stream().map(BLSPublicKey::toAbbreviatedString).collect(toSet());
  }
}
