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

package tech.pegasys.teku.ethereum.executionclient.auth;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SafeTokenProvider {
  private Optional<Token> token = Optional.empty();

  private final TokenProvider tokenProvider;

  public SafeTokenProvider(final TokenProvider tokenProvider) {
    this.tokenProvider = tokenProvider;
  }

  public synchronized Optional<Token> token(final UInt64 instantInMillis) {
    if (this.token.map(token -> token.isAvailableAt(instantInMillis)).orElse(false)) {
      return token;
    }
    return refreshTokenFromSource(instantInMillis);
  }

  private Optional<Token> refreshTokenFromSource(final UInt64 instantInMillis) {
    final Optional<Token> optionalToken = tokenProvider.token(instantInMillis);
    this.token = optionalToken;
    return optionalToken;
  }
}
