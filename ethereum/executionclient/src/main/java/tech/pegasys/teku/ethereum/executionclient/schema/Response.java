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

package tech.pegasys.teku.ethereum.executionclient.schema;

import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.spec.SpecMilestone;

public record Response<T>(
    T payload, String errorMessage, boolean receivedAsSsz, boolean isUnsupportedMediaTypeError) {
  private static final Logger LOG = LogManager.getLogger();

  public static <T> Response<T> fromNullPayload() {
    return new Response<>(null, null, false, false);
  }

  public static <T> Response<T> fromPayloadReceivedAsSsz(final T payload) {
    return new Response<>(payload, null, true, false);
  }

  public static <T> Response<T> fromPayloadReceivedAsJson(final T payload) {
    return new Response<>(payload, null, false, false);
  }

  public static <T> Response<T> fromErrorMessage(final String errorMessage) {
    final String strippedErrorMessage = errorMessage != null ? errorMessage.strip() : null;
    return new Response<>(null, strippedErrorMessage, false, false);
  }

  public static <T> Response<T> fromUnsupportedMediaTypeError() {
    return new Response<>(null, "Unsupported Media Type", false, true);
  }

  public <R> Response<R> unwrapVersioned(
      final Function<T, R> unwrapFunction,
      final SpecMilestone expectedMilestone,
      final Function<T, SpecMilestone> unwrapVersionFunction,
      final boolean strictVersionCheck) {

    if (isFailure() || payload == null) {
      return new Response<>(null, errorMessage, receivedAsSsz, isUnsupportedMediaTypeError);
    }

    final SpecMilestone receivedMilestone = unwrapVersionFunction.apply(payload);

    final boolean milestonesMismatch = !receivedMilestone.equals(expectedMilestone);

    if (milestonesMismatch) {
      if (strictVersionCheck) {
        throw new IllegalArgumentException(
            "Wrong response version: expected "
                + expectedMilestone
                + ", received "
                + receivedMilestone);
      } else {
        LOG.warn(
            "Wrong response version: expected {}, received {}.",
            expectedMilestone,
            receivedMilestone);
      }
    }

    return new Response<>(unwrapFunction.apply(payload), null, receivedAsSsz, false);
  }

  public Response<Optional<T>> convertToOptional() {
    if (isFailure()) {
      return new Response<>(null, errorMessage, receivedAsSsz, isUnsupportedMediaTypeError);
    }

    return payload == null
        ? new Response<>(Optional.empty(), null, receivedAsSsz, false)
        : new Response<>(Optional.of(payload), null, receivedAsSsz, false);
  }

  public boolean isSuccess() {
    return errorMessage == null;
  }

  public boolean isFailure() {
    return errorMessage != null;
  }
}
