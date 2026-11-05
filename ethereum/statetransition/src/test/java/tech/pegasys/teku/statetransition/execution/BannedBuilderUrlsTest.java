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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class BannedBuilderUrlsTest {

  private static final String URL = "http://builder.example.com";
  private static final String OTHER_URL = "http://other-builder.example.com";

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();

  @Test
  void urlIsNotBannedByDefault() {
    final BannedBuilderUrls bannedBuilderUrls = new BannedBuilderUrls(spec, 1);

    assertThat(bannedBuilderUrls.isBanned(URL, UInt64.ZERO)).isFalse();
  }

  @Test
  void bansUrlForConfiguredNumberOfEpochs() {
    final BannedBuilderUrls bannedBuilderUrls = new BannedBuilderUrls(spec, 2);
    final UInt64 banSlot = UInt64.valueOf(10);
    final UInt64 bannedUntilSlot = banSlot.plus(2L * slotsPerEpoch);

    bannedBuilderUrls.ban(URL, banSlot);

    assertThat(bannedBuilderUrls.isBanned(URL, banSlot)).isTrue();
    assertThat(bannedBuilderUrls.isBanned(URL, bannedUntilSlot.minus(1))).isTrue();
    assertThat(bannedBuilderUrls.isBanned(URL, bannedUntilSlot)).isFalse();
    assertThat(bannedBuilderUrls.isBanned(OTHER_URL, banSlot)).isFalse();
  }

  @Test
  void onSlotClearsExpiredBansOnly() {
    final BannedBuilderUrls bannedBuilderUrls = new BannedBuilderUrls(spec, 1);
    bannedBuilderUrls.ban(URL, UInt64.valueOf(10));
    bannedBuilderUrls.ban(OTHER_URL, UInt64.valueOf(12));

    final UInt64 firstBanExpirySlot = UInt64.valueOf(10L + slotsPerEpoch);
    bannedBuilderUrls.onSlot(firstBanExpirySlot);

    // URL is no longer banned even for earlier slots, since its ban has been cleared
    assertThat(bannedBuilderUrls.isBanned(URL, UInt64.valueOf(10))).isFalse();
    assertThat(bannedBuilderUrls.isBanned(OTHER_URL, firstBanExpirySlot)).isTrue();
  }

  @Test
  void banningAgainExtendsTheBan() {
    final BannedBuilderUrls bannedBuilderUrls = new BannedBuilderUrls(spec, 1);
    bannedBuilderUrls.ban(URL, UInt64.valueOf(10));
    bannedBuilderUrls.ban(URL, UInt64.valueOf(15));

    assertThat(bannedBuilderUrls.isBanned(URL, UInt64.valueOf(15L + slotsPerEpoch - 1))).isTrue();
  }

  @Test
  void doesNotBanWhenBanEpochsIsZero() {
    final BannedBuilderUrls bannedBuilderUrls = new BannedBuilderUrls(spec, 0);

    bannedBuilderUrls.ban(URL, UInt64.valueOf(10));

    assertThat(bannedBuilderUrls.isBanned(URL, UInt64.valueOf(10))).isFalse();
  }
}
