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

package tech.pegasys.teku.infrastructure.events;

import java.lang.reflect.Method;

public interface ChannelExceptionHandler {
  ChannelExceptionHandler THROWING_HANDLER =
      (error, subscriber, invokedMethod, args) -> {
        throw new RuntimeException(error);
      };

  void handleException(Throwable error, Object subscriber, Method invokedMethod, Object[] args);
}
