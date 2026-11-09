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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.ReqRespResponseLogger;

public record LoggingResponseCallback<T>(
    ResponseCallback<T> callback, ReqRespResponseLogger<T> logger) implements ResponseCallback<T> {

  @Override
  public SafeFuture<Void> respond(final T data) {
    logger.onNextItem(data);
    return callback.respond(data);
  }

  @Override
  public void respondAndCompleteSuccessfully(final T data) {
    logger.onNextItem(data);
    logger.onComplete();
    callback.respondAndCompleteSuccessfully(data);
  }

  @Override
  public void completeSuccessfully() {
    logger.onComplete();
    callback.completeSuccessfully();
  }

  @Override
  public void completeWithErrorResponse(final RpcException error) {
    logger.onError(error);
    callback.completeWithErrorResponse(error);
  }

  @Override
  public void completeWithUnexpectedError(final Throwable error) {
    logger.onError(error);
    callback.completeWithUnexpectedError(error);
  }
}
