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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container10;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadBellatrix;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

/** A Beacon block body */
class BeaconBlockBodyBellatrixImpl
    extends Container10<
        BeaconBlockBodyBellatrixImpl,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate,
        ExecutionPayloadBellatrix>
    implements BeaconBlockBodyBellatrix {

  BeaconBlockBodyBellatrixImpl(final BeaconBlockBodySchemaBellatrixImpl type) {
    super(type);
  }

  BeaconBlockBodyBellatrixImpl(
      final BeaconBlockBodySchemaBellatrixImpl type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  BeaconBlockBodyBellatrixImpl(
      final BeaconBlockBodySchemaBellatrixImpl type,
      final SszSignature randaoReveal,
      final Eth1Data eth1Data,
      final SszBytes32 graffiti,
      final SszList<ProposerSlashing> proposerSlashings,
      final SszList<AttesterSlashing> attesterSlashings,
      final SszList<Attestation> attestations,
      final SszList<Deposit> deposits,
      final SszList<SignedVoluntaryExit> voluntaryExits,
      final SyncAggregate syncAggregate,
      final ExecutionPayloadBellatrix executionPayload) {
    super(
        type,
        randaoReveal,
        eth1Data,
        graffiti,
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits,
        syncAggregate,
        executionPayload);
  }

  public static BeaconBlockBodyBellatrixImpl required(final BeaconBlockBody body) {
    checkArgument(
        body instanceof BeaconBlockBodyBellatrixImpl,
        "Expected bellatrix block body but got %s",
        body.getClass());
    return (BeaconBlockBodyBellatrixImpl) body;
  }

  @Override
  public BLSSignature getRandaoReveal() {
    return getField0().getSignature();
  }

  @Override
  public SszSignature getRandaoRevealSsz() {
    return getField0();
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

  @Override
  public SszList<SignedVoluntaryExit> getVoluntaryExits() {
    return getField7();
  }

  @Override
  public SyncAggregate getSyncAggregate() {
    return getField8();
  }

  @Override
  public ExecutionPayload getExecutionPayload() {
    return getField9();
  }

  @Override
  public BeaconBlockBodySchemaBellatrixImpl getSchema() {
    return (BeaconBlockBodySchemaBellatrixImpl) super.getSchema();
  }
}
