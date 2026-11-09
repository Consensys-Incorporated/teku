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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig.TOLERANCE_IN_SECONDS;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SafeTokenProviderTest {
  private SecretKey jwtSecretKey;

  private SafeTokenProvider safeTokenProvider;

  @BeforeEach
  void setUp() {
    jwtSecretKey = JwtTestHelper.generateJwtSecret();
    safeTokenProvider =
        new SafeTokenProvider(new TokenProvider(new JwtConfig(jwtSecretKey, Optional.empty())));
  }

  @Test
  void testGetToken_TokenUpdateOnExpiry() {
    UInt64 timeInMillis = UInt64.valueOf(System.currentTimeMillis());
    final Optional<Token> originalToken = safeTokenProvider.token(timeInMillis);
    validateTokenAtInstant(originalToken, timeInMillis);
    validateJwtTokenAtInstant(jwtSecretKey, originalToken, timeInMillis);

    timeInMillis = timeInMillis.plus(TimeUnit.SECONDS.toMillis(JwtConfig.EXPIRES_IN_SECONDS));
    final Optional<Token> updatedToken = safeTokenProvider.token(timeInMillis);
    validateTokenAtInstant(updatedToken, timeInMillis);
    validateJwtTokenAtInstant(jwtSecretKey, updatedToken, timeInMillis);

    assertThat(originalToken.equals(updatedToken)).isFalse();
  }

  @Test
  void testGetToken_SameTokenPreExpiry() {
    UInt64 timeInMillis = UInt64.valueOf(System.currentTimeMillis());
    final Optional<Token> originalToken = safeTokenProvider.token(timeInMillis);
    validateTokenAtInstant(originalToken, timeInMillis);
    validateJwtTokenAtInstant(jwtSecretKey, originalToken, timeInMillis);

    timeInMillis =
        timeInMillis.plus(TimeUnit.SECONDS.toMillis(JwtConfig.EXPIRES_IN_SECONDS)).minus(1);
    final Optional<Token> updatedToken = safeTokenProvider.token(timeInMillis);
    validateTokenAtInstant(updatedToken, timeInMillis);
    validateJwtTokenAtInstant(jwtSecretKey, updatedToken, timeInMillis);

    Assertions.assertThat(originalToken).isEqualTo(updatedToken);
  }

  @Test
  void testGetToken_addsIdToClaimsWhenConfigured() {
    final String claimId = "foobar";

    safeTokenProvider =
        new SafeTokenProvider(new TokenProvider(new JwtConfig(jwtSecretKey, Optional.of(claimId))));

    final UInt64 timeInMillis = UInt64.valueOf(System.currentTimeMillis());
    final Optional<Token> token = safeTokenProvider.token(timeInMillis);
    assertThat(token).isPresent();

    final Claims claims =
        Jwts.parser()
            .verifyWith(jwtSecretKey)
            .build()
            .parseSignedClaims(token.get().getJwtToken())
            .getPayload();

    assertThat(claims.get("id")).isEqualTo(claimId);
  }

  public static void validateTokenAtInstant(
      final Optional<Token> optionalToken, final UInt64 instantInMillis) {
    Assertions.assertThat(optionalToken).isPresent();
    assertThat(optionalToken.get().isAvailableAt(instantInMillis)).isTrue();
    assertThat(optionalToken.get().getJwtToken()).isNotBlank();
  }

  public static void validateJwtTokenAtInstant(
      final SecretKey jwtSecretKey,
      final Optional<Token> optionalToken,
      final UInt64 instantInMillis) {
    Assertions.assertThat(optionalToken).isPresent();
    final long issuedAtInSeconds =
        Jwts.parser()
            .verifyWith(jwtSecretKey)
            .build()
            .parseSignedClaims(optionalToken.get().getJwtToken())
            .getPayload()
            .get(Claims.ISSUED_AT, Long.class);
    assertThat(instantInMillis.plus(TimeUnit.SECONDS.toMillis(TOLERANCE_IN_SECONDS)))
        .isGreaterThan(UInt64.valueOf(TimeUnit.SECONDS.toMillis(issuedAtInSeconds)));
    assertThat(instantInMillis.minus(TimeUnit.SECONDS.toMillis(TOLERANCE_IN_SECONDS)))
        .isLessThan(UInt64.valueOf(TimeUnit.SECONDS.toMillis(issuedAtInSeconds)));
  }
}
