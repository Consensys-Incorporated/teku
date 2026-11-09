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

package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import tech.pegasys.teku.infrastructure.async.stream.AsyncStreamVisitor;

public interface ReqRespResponseLogger<TResponse> {

  void onNextItem(TResponse s);

  void onComplete();

  void onError(Throwable error);

  default AsyncStreamVisitor<TResponse> asAsyncStreamVisitor() {
    return new AsyncStreamVisitor<>() {
      @Override
      public void onNext(TResponse tResponse) {
        ReqRespResponseLogger.this.onNextItem(tResponse);
      }

      @Override
      public void onComplete() {
        ReqRespResponseLogger.this.onComplete();
      }

      @Override
      public void onError(Throwable t) {
        ReqRespResponseLogger.this.onError(t);
      }
    };
  }
}
