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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.pq;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container10;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

/**
 * The PQ block body: a VRF proof replaces the RANDAO signature, voluntary exits, the sync
 * aggregate, BLS changes and payload attestations are gone, and {@code proof} proves the XMSS
 * signatures of the block's attestations.
 */
public class BeaconBlockBodyPq
    extends Container10<
        BeaconBlockBodyPq,
        SszBytes32Vector,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SignedExecutionPayloadBid,
        ExecutionRequests,
        SszByteList>
    implements BeaconBlockBody {

  BeaconBlockBodyPq(final BeaconBlockBodySchemaPq type) {
    super(type);
  }

  BeaconBlockBodyPq(final BeaconBlockBodySchemaPq type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public static BeaconBlockBodyPq required(final BeaconBlockBody body) {
    checkArgument(
        body instanceof BeaconBlockBodyPq, "Expected a PQ block body but was %s", body.getClass());
    return (BeaconBlockBodyPq) body;
  }

  /** The {@code VRFProof}: a leaf of the proposer's VRF tree followed by its Merkle branch. */
  public List<Bytes32> getVrfProof() {
    return getField0().asListUnboxed();
  }

  /** A PQ block has a VRF proof instead of a RANDAO signature. */
  @Override
  public BLSSignature getRandaoReveal() {
    throw new UnsupportedOperationException("A PQ block has a VRF proof, not a RANDAO signature");
  }

  /** A PQ block has a VRF proof instead of a RANDAO signature. */
  @Override
  public SszSignature getRandaoRevealSsz() {
    throw new UnsupportedOperationException("A PQ block has a VRF proof, not a RANDAO signature");
  }

  @Override
  public Eth1Data getEth1Data() {
    return getField1();
  }

  @Override
  public Bytes32 getGraffiti() {
    return getField2().get();
  }

  @Override
  public SszBytes32 getGraffitiSsz() {
    return getField2();
  }

  @Override
  public SszList<ProposerSlashing> getProposerSlashings() {
    return getField3();
  }

  @Override
  public SszList<AttesterSlashing> getAttesterSlashings() {
    return getField4();
  }

  @Override
  public SszList<Attestation> getAttestations() {
    return getField5();
  }

  @Override
  public SszList<Deposit> getDeposits() {
    return getField6();
  }

  /** PQ blocks have no voluntary exits: they are execution-layer requests. */
  @Override
  public SszList<SignedVoluntaryExit> getVoluntaryExits() {
    throw new UnsupportedOperationException("PQ blocks have no voluntary exits");
  }

  public SignedExecutionPayloadBid getSignedExecutionPayloadBid() {
    return getField7();
  }

  @Override
  public Optional<SignedExecutionPayloadBid> getOptionalSignedExecutionPayloadBid() {
    return Optional.of(getSignedExecutionPayloadBid());
  }

  public ExecutionRequests getParentExecutionRequests() {
    return getField8();
  }

  @Override
  public Optional<ExecutionRequests> getOptionalParentExecutionRequests() {
    return Optional.of(getParentExecutionRequests());
  }

  /** The {@code AttestationProof} of the XMSS signatures of the block's attestations. */
  public Bytes getProof() {
    return getField9().getBytes();
  }

  @Override
  public BeaconBlockBodySchemaPq getSchema() {
    return (BeaconBlockBodySchemaPq) super.getSchema();
  }
}
