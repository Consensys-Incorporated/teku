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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(
    milestone = {
      SpecMilestone.BELLATRIX,
      SpecMilestone.CAPELLA,
      SpecMilestone.DENEB,
      SpecMilestone.ELECTRA,
      SpecMilestone.FULU
    })
class BlindBlockUtilTest {

  private DataStructureUtil dataStructureUtil;
  private BlindBlockUtil blindBlockUtil;
  private SpecMilestone milestone;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    final Spec spec = specContext.getSpec();
    this.milestone = specContext.getSpecMilestone();
    dataStructureUtil = specContext.getDataStructureUtil();
    final SpecVersion specVersion = spec.forMilestone(specContext.getSpecMilestone());
    blindBlockUtil = specVersion.getBlindBlockUtil().orElseThrow();
  }

  @TestTemplate
  void shouldBlindBlock() {
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
    assertThat(signedBeaconBlock.isBlinded()).isFalse();
    final SignedBeaconBlock signedBlindedBeaconBlock =
        blindBlockUtil.blindSignedBeaconBlock(signedBeaconBlock);
    assertThat(signedBlindedBeaconBlock.isBlinded()).isTrue();
    assertThat(signedBlindedBeaconBlock.getBodyRoot()).isEqualTo(signedBeaconBlock.getBodyRoot());
    assertThat(signedBlindedBeaconBlock.getMessage().getBody().getOptionalExecutionPayload())
        .isEmpty();
    assertThat(signedBlindedBeaconBlock.getMessage().getBody().getOptionalExecutionPayloadHeader())
        .isNotEmpty();
  }

  @TestTemplate
  void shouldUnblindSignedBeaconBlockBeforeFulu() {
    assumeThat(milestone).isLessThanOrEqualTo(SpecMilestone.ELECTRA);
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
    final SignedBeaconBlock signedBlindedBeaconBlock =
        blindBlockUtil.blindSignedBeaconBlock(signedBeaconBlock);

    final SafeFuture<Optional<SignedBeaconBlock>> maybeSignedBeaconBlockSafeFuture =
        blindBlockUtil.unblindSignedBeaconBlock(
            signedBlindedBeaconBlock,
            unblinder ->
                unblinder.setExecutionPayloadSupplier(
                    () ->
                        SafeFuture.completedFuture(
                            signedBeaconBlock
                                .getBeaconBlock()
                                .orElseThrow()
                                .getBody()
                                .getOptionalExecutionPayload()
                                .orElseThrow())));

    final SignedBeaconBlock unblindedSignedBeaconBlock =
        maybeSignedBeaconBlockSafeFuture.getImmediately().orElseThrow();
    assertThat(unblindedSignedBeaconBlock).isEqualTo(signedBeaconBlock);
    assertThat(
            unblindedSignedBeaconBlock
                .getBeaconBlock()
                .orElseThrow()
                .getBody()
                .getOptionalExecutionPayload())
        .isNotEmpty();
    assertThat(
            unblindedSignedBeaconBlock
                .getBeaconBlock()
                .orElseThrow()
                .getBody()
                .getOptionalExecutionPayloadHeader())
        .isEmpty();
  }

  @TestTemplate
  void shouldReturnEmptySignedBeaconBlockAfterFulu() {
    assumeThat(milestone).isGreaterThanOrEqualTo(SpecMilestone.FULU);
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
    final SignedBeaconBlock signedBlindedBeaconBlock =
        blindBlockUtil.blindSignedBeaconBlock(signedBeaconBlock);

    assertThatThrownBy(
            () ->
                blindBlockUtil.unblindSignedBeaconBlock(
                    signedBlindedBeaconBlock,
                    unblinder -> {
                      unblinder.setExecutionPayloadSupplier(() -> SafeFuture.completedFuture(null));
                    }))
        .hasMessageContaining("Should not be called for Fulu block");
    final SafeFuture<Optional<SignedBeaconBlock>> maybeSignedBeaconBlockSafeFuture =
        blindBlockUtil.unblindSignedBeaconBlock(
            signedBlindedBeaconBlock,
            unblinder -> unblinder.setCompletionSupplier(() -> SafeFuture.completedFuture(null)));

    final Optional<SignedBeaconBlock> maybeUnblindedSignedBeaconBlock =
        maybeSignedBeaconBlockSafeFuture.getImmediately();
    assertThat(maybeUnblindedSignedBeaconBlock).isEmpty();
  }
}
