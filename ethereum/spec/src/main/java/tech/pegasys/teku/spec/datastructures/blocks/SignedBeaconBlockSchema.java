/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.blocks;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Optional;
import java.util.OptionalLong;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszNetworkValidator;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SignedBeaconBlockSchema
    extends ContainerSchema2<SignedBeaconBlock, BeaconBlock, SszSignature>
    implements SignedBlockContainerSchema<SignedBeaconBlock> {

  private final OptionalLong networkSszLengthBytesUpperBound;
  private final Optional<SszNetworkValidator<SignedBeaconBlock>> networkSszValidator;

  public SignedBeaconBlockSchema(
      final BeaconBlockSchema beaconBlockSchema, final String containerName) {
    this(beaconBlockSchema, containerName, OptionalLong.empty(), Optional.empty());
  }

  public SignedBeaconBlockSchema(
      final BeaconBlockSchema beaconBlockSchema,
      final String containerName,
      final OptionalLong networkSszLengthBytesUpperBound,
      final Optional<SszNetworkValidator<SignedBeaconBlock>> networkSszValidator) {
    super(
        containerName,
        namedSchema(SignedBeaconBlockFields.MESSAGE, beaconBlockSchema),
        namedSchema(SignedBeaconBlockFields.SIGNATURE, SszSignatureSchema.INSTANCE));
    this.networkSszLengthBytesUpperBound = networkSszLengthBytesUpperBound;
    this.networkSszValidator = networkSszValidator;
    validateNetworkSszLengthBytesUpperBound();
  }

  @Override
  public OptionalLong getNetworkSszLengthBytesUpperBound() {
    return networkSszLengthBytesUpperBound;
  }

  @Override
  public Optional<SszNetworkValidator<SignedBeaconBlock>> getNetworkSszValidator() {
    return networkSszValidator;
  }

  public SignedBeaconBlock create(final BeaconBlock message, final BLSSignature signature) {
    return new SignedBeaconBlock(this, message, signature);
  }

  @Override
  public SignedBeaconBlock createFromBackingNode(final TreeNode node) {
    return new SignedBeaconBlock(this, node);
  }

  public LongList getBlindedNodeGeneralizedIndices() {
    return GIndexUtil.gIdxComposeAll(
        getChildGeneralizedIndex(getFieldIndex(SignedBeaconBlockFields.MESSAGE)),
        getBlockSchema().getBlindedNodeGeneralizedIndices());
  }

  public BeaconBlockSchema getBlockSchema() {
    return (BeaconBlockSchema) getChildSchema(getFieldIndex(SignedBeaconBlockFields.MESSAGE));
  }
}
