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

package tech.pegasys.teku.spec.generator;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ProposerSlashingGenerator {
  private final List<BLSKeyPair> validatorKeys;
  private final DataStructureUtil dataStructureUtil;
  private final SigningRootUtil signingRootUtil;

  public ProposerSlashingGenerator(final Spec spec, final List<BLSKeyPair> validatorKeys) {
    this.validatorKeys = validatorKeys;
    this.dataStructureUtil = new DataStructureUtil(spec);
    this.signingRootUtil = new SigningRootUtil(spec);
  }

  public ProposerSlashing createProposerSlashingForBlock(
      final SignedBlockAndState goodBlockAndState) {
    final SignedBeaconBlockHeader signedGoodBlockHeader = goodBlockAndState.getBlock().asHeader();
    final BeaconBlockHeader goodBlockHeader = signedGoodBlockHeader.getMessage();
    final BeaconBlockHeader badBlockHeader =
        new BeaconBlockHeader(
            goodBlockHeader.getSlot(),
            goodBlockHeader.getProposerIndex(),
            goodBlockHeader.getParentRoot(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());

    final SignedBeaconBlockHeader signedBadBlockHeader =
        signBlockHeader(badBlockHeader, goodBlockAndState.getState());

    return new ProposerSlashing(goodBlockAndState.getBlock().asHeader(), signedBadBlockHeader);
  }

  private SignedBeaconBlockHeader signBlockHeader(
      final BeaconBlockHeader blockHeader, final BeaconState state) {
    final BLSKeyPair proposerKeyPair = validatorKeys.get(blockHeader.getProposerIndex().intValue());
    final Bytes signingRootForSignBlockHeader =
        signingRootUtil.signingRootForSignBlockHeader(blockHeader, state.getForkInfo());
    return new SignedBeaconBlockHeader(
        blockHeader, sign(proposerKeyPair, signingRootForSignBlockHeader));
  }

  private BLSSignature sign(final BLSKeyPair keypair, final Bytes signingRoot) {
    return BLS.sign(keypair.getSecretKey(), signingRoot);
  }
}
