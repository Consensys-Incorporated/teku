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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.TestDataUtils.createAnchorFromState;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.SignedBlsToExecutionChangeValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class GossipBlsToExecutionChangeTestExecutor implements TestExecutor {

  private final List<String> testsToSkip;

  public GossipBlsToExecutionChangeTestExecutor(final String... testsToSkip) {
    this.testsToSkip = List.of(testsToSkip);
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    if (testsToSkip.contains(testDefinition.getTestName())) {
      throw new TestAbortedException(
          "Test " + testDefinition.getDisplayName() + " has been ignored");
    }

    final GossipBlsToExecutionChangeMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipBlsToExecutionChangeMetaData.class);
    final Spec spec = testDefinition.getSpec();
    final BeaconState state = loadStateFromSsz(testDefinition, "state.ssz_snappy");

    final StorageSystem storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(StateStorageMode.ARCHIVE)
            .build();
    final RecentChainData recentChainData = storageSystem.recentChainData();

    recentChainData.initializeFromAnchorPoint(createAnchorFromState(spec, state), UInt64.ZERO);

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
    final SignedBlsToExecutionChangeValidator validator =
        new SignedBlsToExecutionChangeValidator(
            spec,
            timeProvider,
            recentChainData,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));

    // The validator does not deduplicate by validator_index — that is done by
    // MappedOperationPool in production. Replicate it here for the test.
    final Set<UInt64> seenValidators = new HashSet<>();

    for (final GossipBlsToExecutionChangeMetaData.Message message : metaData.getMessages()) {
      final UInt64 messageTimeMs =
          UInt64.valueOf(metaData.getCurrentTimeMs()).plus(UInt64.valueOf(message.getOffsetMs()));
      timeProvider.advanceTimeByMillis(messageTimeMs.minusMinZero(timeProvider.getTimeInMillis()));

      final SignedBlsToExecutionChange signedBlsToExecutionChange =
          loadSsz(
              testDefinition,
              message.getMessage() + ".ssz_snappy",
              SchemaDefinitionsCapella.required(spec.getGenesisSchemaDefinitions())
                  .getSignedBlsToExecutionChangeSchema());

      final UInt64 validatorIndex = signedBlsToExecutionChange.getMessage().getValidatorIndex();
      if (seenValidators.contains(validatorIndex)) {
        assertThat(message.getExpected())
            .describedAs(
                "Expected ignore for duplicate validator index %s message %s",
                validatorIndex, message.getMessage())
            .isEqualTo("ignore");
        continue;
      }

      final InternalValidationResult result =
          validator.validateForGossip(signedBlsToExecutionChange).join();

      GossipTestContext.assertValidationResult(
          "BLS-to-execution-change " + message.getMessage(), message.getExpected(), result);

      if (result.code() == ValidationResultCode.ACCEPT) {
        seenValidators.add(validatorIndex);
      }
    }
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GossipBlsToExecutionChangeMetaData {

    @JsonProperty(value = "topic", required = true)
    private String topic;

    @JsonProperty(value = "messages", required = true)
    private List<Message> messages;

    @JsonProperty(value = "current_time_ms", required = true)
    private long currentTimeMs;

    public List<Message> getMessages() {
      return messages;
    }

    public long getCurrentTimeMs() {
      return currentTimeMs;
    }

    private static class Message {

      @JsonProperty(value = "offset_ms", required = true)
      private long offsetMs;

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason", required = false)
      private String reason;

      public long getOffsetMs() {
        return offsetMs;
      }

      public String getMessage() {
        return message;
      }

      public String getExpected() {
        return expected;
      }
    }
  }
}
