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

import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Token {
  private final String jwtToken;
  private final UInt64 expiryInMillis;

  public Token(final String jwtToken, final UInt64 expiryInMillis) {
    this.jwtToken = jwtToken;
    this.expiryInMillis = expiryInMillis;
  }

  public boolean isAvailableAt(final UInt64 instantInMillis) {
    return expiryInMillis.isGreaterThan(instantInMillis);
  }

  public String getJwtToken() {
    return jwtToken;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Token token = (Token) o;
    return jwtToken.equals(token.jwtToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jwtToken);
  }
}
