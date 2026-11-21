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

package tech.pegasys.teku.validator.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

class XmssKeyWarmerTest {

  private static final int LOOKAHEAD = 256;
  // The per-slot leaves move to a new bottom tree at slot 4096, then every 8192 slots
  private static final long SECOND_PER_SLOT_TREE_START = 4096;
  private static final long THIRD_PER_SLOT_TREE_START = 4096 + 8192;

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
  private final OwnedValidators validators = new OwnedValidators();
  private final XmssSigner signer1 = readySigner();
  private final XmssSigner signer2 = readySigner();

  @Test
  void onSlot_preparesEveryKeyForTheCurrentSlotOnce() {
    final XmssKeyWarmer warmer = createWarmer(spec, 2);
    addValidator(signer1);
    addValidator(signer2);

    warmer.onSlot(UInt64.ZERO);
    warmer.onSlot(UInt64.ONE);

    verify(signer1).prepareForSlot(UInt64.ZERO);
    verify(signer2).prepareForSlot(UInt64.ZERO);
    verify(signer1, times(1)).prepareForSlot(any());
    verify(signer2, times(1)).prepareForSlot(any());
  }

  @Test
  void onSlot_preparesTheNextPerSlotTreeAheadOfItsFirstSlot() {
    final XmssKeyWarmer warmer = createWarmer(spec, 2);
    addValidator(signer1);
    final UInt64 lastSlotBefore = UInt64.valueOf(SECOND_PER_SLOT_TREE_START - LOOKAHEAD - 1);

    warmer.onSlot(lastSlotBefore);
    verify(signer1).prepareForSlot(lastSlotBefore);
    verify(signer1, times(1)).prepareForSlot(any());

    warmer.onSlot(lastSlotBefore.plus(1));
    verify(signer1).prepareForSlot(UInt64.valueOf(SECOND_PER_SLOT_TREE_START));

    // Nothing more until the next tree comes into range, including when the new tree is in use
    warmer.onSlot(lastSlotBefore.plus(2));
    warmer.onSlot(UInt64.valueOf(SECOND_PER_SLOT_TREE_START));
    warmer.onSlot(UInt64.valueOf(THIRD_PER_SLOT_TREE_START - LOOKAHEAD - 1));
    verify(signer1, times(2)).prepareForSlot(any());

    warmer.onSlot(UInt64.valueOf(THIRD_PER_SLOT_TREE_START - LOOKAHEAD));
    verify(signer1).prepareForSlot(UInt64.valueOf(THIRD_PER_SLOT_TREE_START));
    verify(signer1, times(3)).prepareForSlot(any());
  }

  @Test
  void onSlot_preparesKeysAheadOfThePqFork() {
    final Spec specWithLaterPq = TestSpecFactory.createMinimalWithPqForkEpoch(UInt64.valueOf(100));
    final UInt64 forkSlot = specWithLaterPq.computeStartSlotAtEpoch(UInt64.valueOf(100));
    final XmssKeyWarmer warmer = createWarmer(specWithLaterPq, 2);
    addValidator(signer1);

    warmer.onSlot(forkSlot.minus(LOOKAHEAD + 1));
    verify(signer1, never()).prepareForSlot(any());

    warmer.onSlot(forkSlot.minus(LOOKAHEAD));
    verify(signer1).prepareForSlot(forkSlot);
  }

  @Test
  void onSlot_ignoresValidatorsWithoutAnXmssKey() {
    final XmssKeyWarmer warmer = createWarmer(spec, 2);
    validators.addValidator(
        new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty));
    addValidator(signer1);

    warmer.onSlot(UInt64.ZERO);

    verify(signer1).prepareForSlot(UInt64.ZERO);
  }

  @Test
  void onValidatorsAdded_preparesTheNewKeysOnTheNextSlot() {
    final XmssKeyWarmer warmer = createWarmer(spec, 2);
    addValidator(signer1);
    warmer.onSlot(UInt64.ZERO);

    addValidator(signer2);
    warmer.onValidatorsAdded();
    warmer.onSlot(UInt64.ONE);

    verify(signer2).prepareForSlot(UInt64.ONE);
  }

  @Test
  void onSlot_preparesTheOtherKeysWhenOneFails() {
    final XmssKeyWarmer warmer = createWarmer(spec, 2);
    when(signer1.prepareForSlot(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("native failure")));
    addValidator(signer1);
    addValidator(signer2);

    warmer.onSlot(UInt64.ZERO);

    verify(signer1).prepareForSlot(UInt64.ZERO);
    verify(signer2).prepareForSlot(UInt64.ZERO);
  }

  @Test
  void onSlot_preparesNoMoreKeysAtOnceThanAllowed() {
    final XmssKeyWarmer warmer = createWarmer(spec, 1);
    final SafeFuture<Integer> firstKeyPrepared = new SafeFuture<>();
    final XmssSigner slowSigner = mock(XmssSigner.class);
    when(slowSigner.prepareForSlot(any())).thenReturn(firstKeyPrepared);
    addValidator(slowSigner);
    addValidator(signer1);
    addValidator(signer2);

    warmer.onSlot(UInt64.ZERO);

    final XmssSigner[] waiting = {signer1, signer2};
    verify(slowSigner).prepareForSlot(UInt64.ZERO);
    for (final XmssSigner signer : waiting) {
      verify(signer, never()).prepareForSlot(any());
    }

    firstKeyPrepared.complete(2);

    for (final XmssSigner signer : waiting) {
      verify(signer).prepareForSlot(UInt64.ZERO);
    }
  }

  private XmssKeyWarmer createWarmer(final Spec spec, final int maxKeysAtOnce) {
    return new XmssKeyWarmer(spec, validators, timeProvider, LOOKAHEAD, maxKeysAtOnce);
  }

  private void addValidator(final XmssSigner xmssSigner) {
    validators.addValidator(
        new Validator(
            dataStructureUtil.randomPublicKey(),
            mock(Signer.class),
            Optional::empty,
            true,
            Optional.of(xmssSigner)));
  }

  private static XmssSigner readySigner() {
    final XmssSigner signer = mock(XmssSigner.class);
    when(signer.prepareForSlot(any())).thenReturn(SafeFuture.completedFuture(1));
    return signer;
  }
}
