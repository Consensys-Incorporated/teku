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

package tech.pegasys.teku.reference.phase0.gossip;

import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.reference.BlsSetting.IGNORED;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.validation.AttesterSlashingValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class GossipAttesterSlashingTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final GossipAttesterSlashingMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipAttesterSlashingMetaData.class);
    final boolean signatureVerificationDisabled = metaData.getBlsSetting() == IGNORED;
    final Spec spec = testDefinition.getSpec(!signatureVerificationDisabled);
    final BeaconState state = loadStateFromSsz(testDefinition, "state.ssz_snappy");
    final List<SignedBeaconBlock> blocks =
        GossipTestContext.loadBlocks(testDefinition, spec, metaData.getBlocks());
    final GossipTestContext ctx = GossipTestContext.create(spec, state, blocks);
    final AttesterSlashingValidator validator =
        new AttesterSlashingValidator(ctx.recentChainData, spec);

    for (final GossipAttesterSlashingMetaData.Message message : metaData.getMessages()) {
      final AttesterSlashing slashing =
          loadSsz(
              testDefinition,
              message.getMessage() + ".ssz_snappy",
              spec.getGenesisSchemaDefinitions().getAttesterSlashingSchema());
      final InternalValidationResult result = safeJoin(validator.validateForGossip(slashing));

      GossipTestContext.assertValidationResult(
          "attester slashing " + message.getMessage(), message.getExpected(), result);
    }
  }

  @SuppressWarnings("unused")
  private static class GossipAttesterSlashingMetaData {

    @JsonProperty(value = "topic", required = true)
    private String topic;

    @JsonProperty(value = "messages", required = true)
    private List<Message> messages;

    @JsonProperty(value = "blocks", required = true)
    private List<GossipTestContext.BlockEntry> blocks;

    @JsonProperty(value = "bls_setting", required = false, defaultValue = "0")
    private int blsSetting;

    public List<Message> getMessages() {
      return messages;
    }

    public List<GossipTestContext.BlockEntry> getBlocks() {
      return blocks;
    }

    public BlsSetting getBlsSetting() {
      return BlsSetting.forCode(blsSetting);
    }

    private static class Message {

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason", required = false)
      private String reason;

      public String getMessage() {
        return message;
      }

      public String getExpected() {
        return expected;
      }
    }
  }
}
