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

import static tech.pegasys.teku.reference.TestDataUtils.createAnchorFromState;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessageValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeStateUtils;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class GossipSyncCommitteeMessageTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final GossipSyncCommitteeMessageMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipSyncCommitteeMessageMetaData.class);
    final boolean signatureVerificationDisabled = metaData.getBlsSetting() == BlsSetting.IGNORED;
    final BLSSignatureVerifier blsVerifier =
        signatureVerificationDisabled ? BLSSignatureVerifier.NOOP : BLSSignatureVerifier.SIMPLE;
    final Spec spec = testDefinition.getSpec(!signatureVerificationDisabled);
    final BeaconState state = loadStateFromSsz(testDefinition, "state.ssz_snappy");
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();

    final StorageSystem storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(StateStorageMode.ARCHIVE)
            .build();
    final RecentChainData recentChainData = storageSystem.recentChainData();

    recentChainData.initializeFromAnchorPoint(createAnchorFromState(spec, state), UInt64.ZERO);

    final SyncCommitteeStateUtils syncCommitteeStateUtils =
        new SyncCommitteeStateUtils(spec, recentChainData);
    final SyncCommitteeMessageValidator validator =
        new SyncCommitteeMessageValidator(
            spec,
            recentChainData,
            syncCommitteeStateUtils,
            AsyncBLSSignatureVerifier.wrap(blsVerifier),
            new GossipValidationHelper(spec, recentChainData, metricsSystem));

    for (final GossipSyncCommitteeMessageMetaData.Message message : metaData.getMessages()) {
      final UInt64 messageTimeMs =
          UInt64.valueOf(metaData.getCurrentTimeMs()).plus(UInt64.valueOf(message.getOffsetMs()));
      setStoreTimeMillis(recentChainData, messageTimeMs);

      final SyncCommitteeMessage syncCommitteeMessage =
          loadSsz(
              testDefinition,
              message.getMessage() + ".ssz_snappy",
              SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
                  .getSyncCommitteeMessageSchema());

      final ValidatableSyncCommitteeMessage validatable =
          ValidatableSyncCommitteeMessage.fromNetwork(syncCommitteeMessage, message.getSubnetId());
      final InternalValidationResult result = validator.validate(validatable).join();

      GossipTestContext.assertValidationResult(
          "sync committee message " + message.getMessage() + " on subnet " + message.getSubnetId(),
          message.getExpected(),
          result);
    }
  }

  private static void setStoreTimeMillis(
      final RecentChainData recentChainData, final UInt64 timeMillis) {
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setTimeMillis(timeMillis);
    tx.commit().join();
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GossipSyncCommitteeMessageMetaData {

    @JsonProperty(value = "topic", required = true)
    private String topic;

    @JsonProperty(value = "messages", required = true)
    private List<Message> messages;

    @JsonProperty(value = "current_time_ms", required = true)
    private long currentTimeMs;

    @JsonProperty(value = "bls_setting", required = false, defaultValue = "0")
    private int blsSetting;

    public List<Message> getMessages() {
      return messages;
    }

    public long getCurrentTimeMs() {
      return currentTimeMs;
    }

    public BlsSetting getBlsSetting() {
      return BlsSetting.forCode(blsSetting);
    }

    private static class Message {

      @JsonProperty(value = "offset_ms", required = true)
      private long offsetMs;

      @JsonProperty(value = "subnet_id", required = true)
      private int subnetId;

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason", required = false)
      private String reason;

      public long getOffsetMs() {
        return offsetMs;
      }

      public int getSubnetId() {
        return subnetId;
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
