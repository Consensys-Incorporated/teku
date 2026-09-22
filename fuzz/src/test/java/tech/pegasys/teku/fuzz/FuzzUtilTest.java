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

package tech.pegasys.teku.fuzz;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.airlift.compress.v3.snappy.SnappyDecompressor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.fuzz.input.AttestationFuzzInput;
import tech.pegasys.teku.fuzz.input.AttesterSlashingFuzzInput;
import tech.pegasys.teku.fuzz.input.BeaconBlockBodyFuzzInput;
import tech.pegasys.teku.fuzz.input.BlockFuzzInput;
import tech.pegasys.teku.fuzz.input.BlockHeaderFuzzInput;
import tech.pegasys.teku.fuzz.input.BlsToExecutionChangeFuzzInput;
import tech.pegasys.teku.fuzz.input.ConsolidationRequestFuzzInput;
import tech.pegasys.teku.fuzz.input.DepositFuzzInput;
import tech.pegasys.teku.fuzz.input.DepositRequestFuzzInput;
import tech.pegasys.teku.fuzz.input.ProposerSlashingFuzzInput;
import tech.pegasys.teku.fuzz.input.SyncAggregateFuzzInput;
import tech.pegasys.teku.fuzz.input.VoluntaryExitFuzzInput;
import tech.pegasys.teku.fuzz.input.WithdrawalRequestFuzzInput;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodySchemaElectra;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateSchemaElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateSchemaFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

class FuzzUtilTest {

  private final SpecMilestone specMilestone = SpecMilestone.FULU;
  private final Spec spec = TestSpecFactory.createMinimal(specMilestone);
  private final SpecVersion specVersion = spec.forMilestone(specMilestone);
  private final SchemaDefinitionsFulu schemaDefinitions =
      SchemaDefinitionsFulu.required(specVersion.getSchemaDefinitions());
  private final BeaconBlockSchema beaconBlockSchema = schemaDefinitions.getBeaconBlockSchema();
  private final BeaconStateSchemaFulu beaconStateSchema =
      BeaconStateSchemaFulu.required(schemaDefinitions.getBeaconStateSchema());
  private final SignedBeaconBlockSchema signedBeaconBlockSchema =
      schemaDefinitions.getSignedBeaconBlockSchema();

  // Basic sanity tests for fuzzing harnesses.
  //
  // For the purposes of this class, we do not care that operations are
  // correct/equivalent according to the spec (the reference tests cover this),
  // but that the fuzz harness is equivalent to the behavior of the internal
  // process. For example, these tests do not care whether process_deposits is
  // correct, only that the harness correctly uses process_deposits.
  //
  // These test case files are generated using the consensus-specs repo's
  // gen_operations and gen_sanity generators. Reference link:
  // https://github.com/ethereum/consensus-specs/tree/dev/tests/generators

  @Test
  @SuppressWarnings("unchecked")
  public void fuzzAttestation_minimal() throws JsonProcessingException {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    final Path testCaseDir =
        Path.of("minimal/operations/attestation/pyspec_tests/one_basic_attestation");
    final Attestation data =
        loadSsz(
            testCaseDir.resolve("attestation.ssz_snappy"),
            spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions().getAttestationSchema());
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconStateFulu postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    final AttestationFuzzInput input = new AttestationFuzzInput(spec, preState, data);
    final byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    final Optional<Bytes> result = fuzzUtil.fuzzAttestation(rawInput).map(Bytes::wrap);

    assertThat(result).isNotEmpty();
    final BeaconStateFulu resultState =
        BeaconStateFulu.required(spec.deserializeBeaconState(result.get()));
    DeserializableTypeDefinition<BeaconStateFulu> t =
        (DeserializableTypeDefinition<BeaconStateFulu>)
            resultState.getSchema().getJsonTypeDefinition();
    assertThat(JsonUtil.prettySerialize(resultState, t))
        .isEqualTo(JsonUtil.prettySerialize(postState, t));
  }

  @Test
  public void fuzzAttesterSlashing_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    final Path testCaseDir =
        Path.of("minimal/operations/attester_slashing/pyspec_tests/basic_surround");
    final AttesterSlashing data =
        loadSsz(
            testCaseDir.resolve("attester_slashing.ssz_snappy"),
            spec.forMilestone(SpecMilestone.FULU)
                .getSchemaDefinitions()
                .getAttesterSlashingSchema());
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    AttesterSlashingFuzzInput input = new AttesterSlashingFuzzInput(spec, preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzAttesterSlashing(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzBlock_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    final Path testCaseDir = Path.of("minimal/sanity/blocks/pyspec_tests/attestation");
    final SignedBeaconBlock block0 =
        loadSsz(testCaseDir.resolve("blocks_0.ssz_snappy"), signedBeaconBlockSchema);
    final SignedBeaconBlock block1 =
        loadSsz(testCaseDir.resolve("blocks_1.ssz_snappy"), signedBeaconBlockSchema);
    final List<SignedBeaconBlock> blocks = List.of(block0, block1);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    BeaconState currentState = preState;
    for (SignedBeaconBlock block : blocks) {
      BlockFuzzInput input = new BlockFuzzInput(spec, currentState, block);
      byte[] rawInput = input.sszSerialize().toArrayUnsafe();
      Optional<Bytes> result = fuzzUtil.fuzzBlock(rawInput).map(Bytes::wrap);
      assertThat(result).isNotEmpty();
      currentState = beaconStateSchema.sszDeserialize(result.get());
    }

    assertThat(currentState).isNotNull();
    assertThat(currentState).isEqualTo(postState);
  }

  @Test
  public void fuzzBlockHeader_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    final Path testCaseDir =
        Path.of("minimal/operations/block_header/pyspec_tests/basic_block_header");
    final BeaconBlock data = loadSsz(testCaseDir.resolve("block.ssz_snappy"), beaconBlockSchema);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    BlockHeaderFuzzInput input = new BlockHeaderFuzzInput(spec, preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzBlockHeader(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzDeposit_minimal() {
    // fuzzDeposit is not applicable for Fulu
    final Spec spec = TestSpecFactory.createMinimalElectra();
    final BeaconStateSchemaElectra beaconStateSchema =
        BeaconStateSchemaElectra.required(
            spec.getGenesisSchemaDefinitions().getBeaconStateSchema());
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, SpecMilestone.ELECTRA, true);

    final Path testCaseDir =
        Path.of("minimal/operations/deposit/pyspec_tests/top_up__max_effective_balance");
    final Deposit data = loadSsz(testCaseDir.resolve("deposit.ssz_snappy"), Deposit.SSZ_SCHEMA);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    DepositFuzzInput input = new DepositFuzzInput(spec, preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzDeposit(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzProposerSlashing_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    final Path testCaseDir = Path.of("minimal/operations/proposer_slashing/pyspec_tests/basic");
    final ProposerSlashing data =
        loadSsz(testCaseDir.resolve("proposer_slashing.ssz_snappy"), ProposerSlashing.SSZ_SCHEMA);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    ProposerSlashingFuzzInput input = new ProposerSlashingFuzzInput(spec, preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzProposerSlashing(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzVoluntaryExit_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    final Path testCaseDir = Path.of("minimal/operations/voluntary_exit/pyspec_tests/basic");
    final SignedVoluntaryExit data =
        loadSsz(testCaseDir.resolve("voluntary_exit.ssz_snappy"), SignedVoluntaryExit.SSZ_SCHEMA);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    VoluntaryExitFuzzInput input = new VoluntaryExitFuzzInput(spec, preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzVoluntaryExit(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzSyncAggregate_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    BeaconBlockBodySchemaAltair<?> beaconBlockBodySchema =
        (BeaconBlockBodySchemaAltair<?>)
            specVersion.getSchemaDefinitions().getBeaconBlockBodySchema();

    final Path testCaseDir =
        Path.of(
            "minimal/operations/sync_aggregate/pyspec_tests/sync_committee_rewards_nonduplicate_committee");
    final SyncAggregate data =
        loadSsz(
            testCaseDir.resolve("sync_aggregate.ssz_snappy"),
            beaconBlockBodySchema.getSyncAggregateSchema());
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    SyncAggregateFuzzInput input = new SyncAggregateFuzzInput(spec, preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzSyncAggregate(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzExecutionPayload_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    BeaconBlockBodySchemaElectra<?> beaconBlockBodySchema =
        (BeaconBlockBodySchemaElectra<?>)
            specVersion.getSchemaDefinitions().getBeaconBlockBodySchema();

    final Path testCaseDir =
        Path.of("minimal/operations/execution_payload/pyspec_tests/success_regular_payload");
    final BeaconBlockBody data =
        loadSsz(testCaseDir.resolve("body.ssz_snappy"), beaconBlockBodySchema);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    BeaconBlockBodyFuzzInput input =
        new BeaconBlockBodyFuzzInput(spec, preState, data.toVersionElectra().orElseThrow());
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzExecutionPayload(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzBlsToExecutionChange_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    final Path testCaseDir =
        Path.of("minimal/operations/bls_to_execution_change/pyspec_tests/success/");
    final SignedBlsToExecutionChange data =
        loadSsz(
            testCaseDir.resolve("address_change.ssz_snappy"),
            SchemaDefinitionsFulu.required(specVersion.getSchemaDefinitions())
                .getSignedBlsToExecutionChangeSchema());
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    BlsToExecutionChangeFuzzInput input = new BlsToExecutionChangeFuzzInput(spec, preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzBlsToExecutionChange(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzDepositRequest_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    final Path testCaseDir =
        Path.of(
            "minimal/operations/deposit_request/pyspec_tests/process_deposit_request_max_effective_balance_compounding/");
    final DepositRequest data =
        loadSsz(
            testCaseDir.resolve("deposit_request.ssz_snappy"),
            SchemaDefinitionsFulu.required(specVersion.getSchemaDefinitions())
                .getExecutionRequestsSchema()
                .getDepositRequestsSchema()
                .getElementSchema());
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    DepositRequestFuzzInput input = new DepositRequestFuzzInput(spec, preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzDepositRequest(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzWithdrawalRequest_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    final Path testCaseDir =
        Path.of("minimal/operations/withdrawal_request/pyspec_tests/basic_withdrawal_request/");
    final WithdrawalRequest data =
        loadSsz(
            testCaseDir.resolve("withdrawal_request.ssz_snappy"),
            SchemaDefinitionsFulu.required(specVersion.getSchemaDefinitions())
                .getExecutionRequestsSchema()
                .getWithdrawalRequestsSchema()
                .getElementSchema());
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    WithdrawalRequestFuzzInput input = new WithdrawalRequestFuzzInput(spec, preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzWithdrawalRequest(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzConsolidationRequest_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(spec, specMilestone, true);

    final Path testCaseDir =
        Path.of(
            "minimal/operations/consolidation_request/pyspec_tests/basic_consolidation_in_current_consolidation_epoch/");
    final ConsolidationRequest data =
        loadSsz(
            testCaseDir.resolve("consolidation_request.ssz_snappy"),
            SchemaDefinitionsFulu.required(specVersion.getSchemaDefinitions())
                .getExecutionRequestsSchema()
                .getConsolidationRequestsSchema()
                .getElementSchema());
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz_snappy"), beaconStateSchema);
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz_snappy"), beaconStateSchema);

    ConsolidationRequestFuzzInput input = new ConsolidationRequestFuzzInput(spec, preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzConsolidationRequest(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  void fuzzShuffle_insufficientInput() {
    final FuzzUtil fuzzUtil = new FuzzUtil(true, true);

    byte[] emptyInput = new byte[0];
    assertThat(fuzzUtil.fuzzShuffle(emptyInput)).isEmpty();
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(15).toArrayUnsafe())).isEmpty();
    // minimum length is 34
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(33).toArrayUnsafe())).isEmpty();
  }

  @Test
  void fuzzShuffle_sufficientInput() {
    final FuzzUtil fuzzUtil = new FuzzUtil(true, true);

    // minimum length is 34, and should succeed
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(34).toArrayUnsafe())).isPresent();
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(80).toArrayUnsafe())).isPresent();

    // first 2 bytes (little endian) % 100 provide the count, the rest the seed
    // 1000 = 16 (little endian)
    byte[] input =
        Bytes.fromHexString(
                "0x10000000000000000000000000000000000000000000000000000000000000000000")
            .toArrayUnsafe();
    assertThat(fuzzUtil.fuzzShuffle(input))
        .hasValueSatisfying(
            b -> {
              assertThat(b).hasSize(16 * Long.BYTES);
            });
  }

  public <T extends SszData> T loadSsz(final Path path, final SszSchema<T> type) {
    // would have used TestDataUtils.loadSsz() but since these files are
    // resources it is not that straight-forward
    try {
      final byte[] data =
          getClass().getClassLoader().getResourceAsStream(path.toString()).readAllBytes();
      final SnappyDecompressor decompressor = SnappyDecompressor.create();
      final int uncompressedLength = decompressor.getUncompressedLength(data, 0);
      final byte[] uncompressed = new byte[uncompressedLength];
      decompressor.decompress(data, 0, data.length, uncompressed, 0, uncompressedLength);
      return type.sszDeserialize(Bytes.wrap(uncompressed));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final NullPointerException e) {
      throw new RuntimeException("Unable to locate file at " + path.toString(), e);
    }
  }
}
