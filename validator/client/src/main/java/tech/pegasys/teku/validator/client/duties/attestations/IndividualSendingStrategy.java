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

package tech.pegasys.teku.validator.client.duties.attestations;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;

/**
 * Sends each message as soon as it is produced, rather than waiting for the others. For PQ, where a
 * leanXMSS signature takes 15 to 130 ms of CPU, a slot's attestations take a second or more to
 * sign, and a batch would hold the first signed until the last is.
 */
public class IndividualSendingStrategy<T> implements SendingStrategy<T> {
  private final Function<List<T>, SafeFuture<List<SubmitDataError>>> sendFunction;

  public IndividualSendingStrategy(
      final Function<List<T>, SafeFuture<List<SubmitDataError>>> sendFunction) {
    this.sendFunction = sendFunction;
  }

  @Override
  public SafeFuture<DutyResult> send(final Stream<SafeFuture<ProductionResult<T>>> messages) {
    return DutyResult.combine(
        messages
            .map(
                message ->
                    message.thenCompose(
                        result -> ProductionResult.send(List.of(result), sendFunction)))
            .toList());
  }
}
