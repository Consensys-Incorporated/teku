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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.util.BeaconBlockBodyLists;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class AbstractBeaconBlockBodyTest<T extends BeaconBlockBody> {
  protected Spec spec;
  protected DataStructureUtil dataStructureUtil;

  protected BLSSignature randaoReveal;
  protected Eth1Data eth1Data;
  protected Bytes32 graffiti;
  protected SszList<ProposerSlashing> proposerSlashings;
  protected SszList<AttesterSlashing> attesterSlashings;
  protected SszList<Attestation> attestations;
  protected SszList<Deposit> deposits;
  protected SszList<SignedVoluntaryExit> voluntaryExits;

  protected T defaultBlockBody;
  protected BlindedBeaconBlockBodyBellatrix defaultBlindedBlockBody;
  protected BeaconBlockBodySchema<?> blockBodySchema;
  protected BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyBellatrix> blindedBlockBodySchema;

  protected boolean supportsExecutionPayload;

  protected void setUpBaseClass(final SpecMilestone milestone, final Runnable additionalSetup) {
    spec = TestSpecFactory.createMinimal(milestone);
    supportsExecutionPayload = spec.isMilestoneSupported(SpecMilestone.BELLATRIX);
    dataStructureUtil = new DataStructureUtil(spec);
    BeaconBlockBodyLists blockBodyLists = BeaconBlockBodyLists.ofSpec(spec);

    voluntaryExits =
        blockBodyLists.createVoluntaryExits(
            dataStructureUtil.randomSignedVoluntaryExit(),
            dataStructureUtil.randomSignedVoluntaryExit(),
            dataStructureUtil.randomSignedVoluntaryExit());
    deposits =
        blockBodyLists.createDeposits(dataStructureUtil.randomDeposits(2).toArray(new Deposit[0]));
    attestations =
        blockBodyLists.createAttestations(
            dataStructureUtil.randomAttestation(),
            dataStructureUtil.randomAttestation(),
            dataStructureUtil.randomAttestation());
    attesterSlashings =
        blockBodyLists.createAttesterSlashings(dataStructureUtil.randomAttesterSlashing());
    proposerSlashings =
        blockBodyLists.createProposerSlashings(
            dataStructureUtil.randomProposerSlashing(),
            dataStructureUtil.randomProposerSlashing(),
            dataStructureUtil.randomProposerSlashing());
    graffiti = dataStructureUtil.randomBytes32();
    eth1Data = dataStructureUtil.randomEth1Data();
    randaoReveal = dataStructureUtil.randomSignature();

    additionalSetup.run();

    defaultBlockBody = createDefaultBlockBody();
    defaultBlindedBlockBody = supportsExecutionPayload ? createDefaultBlindedBlockBody() : null;
    blockBodySchema = defaultBlockBody.getSchema();
    blindedBlockBodySchema = supportsExecutionPayload ? defaultBlindedBlockBody.getSchema() : null;
  }

  protected T createBlockBody() {
    return createBlockBody(createContentProvider(false));
  }

  protected BlindedBeaconBlockBodyBellatrix createBlindedBlockBody() {
    return createBlindedBlockBody(createContentProvider(true));
  }

  protected abstract T createBlockBody(final Consumer<BeaconBlockBodyBuilder> contentProvider);

  protected abstract BlindedBeaconBlockBodyBellatrix createBlindedBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider);

  @SuppressWarnings("unchecked")
  protected BeaconBlockBodySchema<? extends T> getBlockBodySchema() {
    return (BeaconBlockBodySchema<? extends T>)
        spec.getGenesisSchemaDefinitions().getBeaconBlockBodySchema();
  }

  protected BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return spec.forMilestone(spec.getForkSchedule().getHighestSupportedMilestone())
        .getSchemaDefinitions()
        .createBeaconBlockBodyBuilder();
  }

  protected T createDefaultBlockBody() {
    return createBlockBody();
  }

  protected BlindedBeaconBlockBodyBellatrix createDefaultBlindedBlockBody() {
    return createBlindedBlockBody();
  }

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    T testBeaconBlockBody = defaultBlockBody;

    assertEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    T testBeaconBlockBody = createDefaultBlockBody();
    assertEquals(defaultBlockBody, testBeaconBlockBody);
  }

  private <C extends SszData> SszList<C> reversed(final SszList<C> list) {
    List<C> reversedList = list.stream().collect(Collectors.toList());
    Collections.reverse(reversedList);
    return list.getSchema().createFromElements(reversedList);
  }

  @Test
  void equalsReturnsFalseWhenProposerSlashingsAreDifferent() {
    // Create copy of proposerSlashings and reverse to ensure it is different.
    this.proposerSlashings = reversed(proposerSlashings);
    T testBeaconBlockBody = createBlockBody();

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttesterSlashingsAreDifferent() {
    // Create copy of attesterSlashings and change the element to ensure it is different.
    attesterSlashings =
        blockBodySchema.getAttesterSlashingsSchema().of(dataStructureUtil.randomAttesterSlashing());

    T testBeaconBlockBody = createBlockBody();

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttestationsAreDifferent() {
    // Create copy of attestations and reverse to ensure it is different.
    attestations = reversed(attestations);

    T testBeaconBlockBody = createBlockBody();

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenDepositsAreDifferent() {
    // Create copy of deposits and reverse to ensure it is different.
    deposits = reversed(deposits);

    T testBeaconBlockBody = createBlockBody();

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenExitsAreDifferent() {
    // Create copy of exits and reverse to ensure it is different.
    voluntaryExits = reversed(voluntaryExits);

    T testBeaconBlockBody = createBlockBody();

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void roundTripsViaSsz() {
    BeaconBlockBody newBeaconBlockBody =
        getBlockBodySchema().sszDeserialize(defaultBlockBody.sszSerialize());
    assertEquals(defaultBlockBody, newBeaconBlockBody);
  }

  @Test
  public void builderShouldCreateBlinded() {
    assumeTrue(supportsExecutionPayload);
    final BeaconBlockBody blockBody = createBlindedBlockBody(createContentProvider(true));
    assertThat(blockBody)
        .matches(
            beaconBlockBody ->
                beaconBlockBody.getOptionalExecutionPayload().isEmpty()
                    && beaconBlockBody.getOptionalExecutionPayloadHeader().isPresent());
  }

  @Test
  public void builderShouldCreateUnblinded() {
    assumeTrue(supportsExecutionPayload);
    final BeaconBlockBody blockBody = createBlockBody(createContentProvider(false));
    assertThat(blockBody)
        .matches(
            beaconBlockBody ->
                beaconBlockBody.getOptionalExecutionPayloadHeader().isEmpty()
                    && beaconBlockBody.getOptionalExecutionPayload().isPresent());
  }

  @Test
  public void verifyExecutionPayloadSupport() {
    assertEquals(
        createBeaconBlockBodyBuilder().supportsExecutionPayload(), supportsExecutionPayload);
  }

  protected Consumer<BeaconBlockBodyBuilder> createContentProvider(final boolean blinded) {
    return builder ->
        builder
            .randaoReveal(randaoReveal)
            .eth1Data(eth1Data)
            .graffiti(graffiti)
            .attestations(attestations)
            .proposerSlashings(proposerSlashings)
            .attesterSlashings(attesterSlashings)
            .deposits(deposits)
            .voluntaryExits(voluntaryExits);
  }
}
