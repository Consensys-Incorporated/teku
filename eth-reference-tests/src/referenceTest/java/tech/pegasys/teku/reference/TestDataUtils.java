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

package tech.pegasys.teku.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.xerial.snappy.Snappy;
import org.yaml.snakeyaml.LoaderOptions;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class TestDataUtils {

  private static final YAMLFactory YAML_FACTORY;

  static {
    final LoaderOptions loaderOptions = new LoaderOptions();
    // Set the code point limit to 100MB - context:
    // https://github.com/FasterXML/jackson-dataformats-text/tree/2.15/yaml#maximum-input-yaml-document-size-3-mb
    loaderOptions.setCodePointLimit(1024 * 1024 * 100);
    YAML_FACTORY = YAMLFactory.builder().loaderOptions(loaderOptions).build();
  }

  public static <T extends SszData> T loadSsz(
      final TestDefinition testDefinition, final String fileName, final SszSchema<T> type) {
    return loadSsz(testDefinition, fileName, type::sszDeserialize);
  }

  public static <T> T loadSsz(
      final TestDefinition testDefinition,
      final String fileName,
      final Function<Bytes, T> deserializer) {
    try {
      final Bytes sszData = readSszData(testDefinition, fileName);
      return deserializer.apply(sszData);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Bytes readSszData(final TestDefinition testDefinition, final String fileName)
      throws IOException {
    final Path testDirectory = testDefinition.getTestDirectory();
    final Path path = testDirectory.resolve(fileName);
    final byte[] fileContent = Files.readAllBytes(path);
    if (fileName.endsWith("_snappy")) {
      return Bytes.wrap(Snappy.uncompress(fileContent));
    } else {
      return Bytes.wrap(fileContent);
    }
  }

  public static BeaconState loadStateFromSsz(
      final TestDefinition testDefinition, final String fileName) {
    return loadSsz(
        testDefinition,
        fileName,
        testDefinition.getSpec().getGenesisSchemaDefinitions().getBeaconStateSchema());
  }

  public static <T> T loadYaml(
      final TestDefinition testDefinition, final String fileName, final Class<T> type)
      throws IOException {
    final Path path = testDefinition.getTestDirectory().resolve(fileName);
    try (final InputStream in = Files.newInputStream(path)) {
      return new ObjectMapper(YAML_FACTORY).readValue(in, type);
    }
  }

  public static <T> T loadYaml(
      final TestDefinition testDefinition,
      final String fileName,
      final DeserializableTypeDefinition<T> type)
      throws IOException {
    final Path path = testDefinition.getTestDirectory().resolve(fileName);
    try (final YAMLParser in = YAML_FACTORY.createParser(Files.newInputStream(path))) {
      in.nextToken();
      return type.deserialize(in);
    }
  }

  public static <T> T loadJson(
      final TestDefinition testDefinition,
      final String fileName,
      final DeserializableTypeDefinition<T> type)
      throws IOException {
    final Path path = testDefinition.getTestDirectory().resolve(fileName);
    try (final InputStream in = Files.newInputStream(path)) {
      return JsonUtil.parse(in, type);
    }
  }

  /**
   * Builds an {@link AnchorPoint} from a state without the genesis-block body-root check that
   * {@link AnchorPoint#fromGenesisState} performs. Some forks (e.g. gloas) define a genesis state
   * whose {@code latest_block_header.body_root} does not match the body produced by {@code
   * BeaconBlock.fromGenesisState}, so the anchor block is always synthesised from the state's
   * {@code latest_block_header}.
   */
  public static AnchorPoint createAnchorFromState(final Spec spec, final BeaconState state) {
    final BeaconBlockHeader header = BeaconBlockHeader.fromState(state);
    final UInt64 epoch = spec.computeNextEpochBoundary(state.getSlot());
    final Checkpoint checkpoint = new Checkpoint(epoch, header.hashTreeRoot());
    return AnchorPoint.create(spec, checkpoint, state, Optional.empty());
  }

  /**
   * Builds an {@link AnchorPoint} for gossip reference tests that load both an initial state and
   * the fixture blocks that may have produced it.
   *
   * <p>When one of the supplied blocks has a {@code state_root} matching the supplied state, the
   * anchor is created from that signed block and state. This keeps the store's anchor root and
   * stored block aligned with the spec-test fixture, which matters for gossip validators that
   * subsequently import or look up related blocks. If no matching block is available, the helper
   * falls back to {@link #createAnchorFromState(Spec, BeaconState)}, which synthesizes an anchor
   * from the state's {@code latest_block_header}.
   */
  public static AnchorPoint createAnchorFromStateAndMatchingBlock(
      final Spec spec, final BeaconState state, final Collection<SignedBeaconBlock> blocks) {
    final Optional<AnchorPoint> anchorFromMatchingStateRoot =
        blocks.stream()
            .filter(
                block ->
                    block.getStateRoot().equals(state.hashTreeRoot())
                        && block
                            .getMessage()
                            .getBody()
                            .hashTreeRoot()
                            .equals(state.getLatestBlockHeader().getBodyRoot()))
            .findFirst()
            .map(
                block ->
                    AnchorPoint.fromInitialBlockAndState(
                        spec, new SignedBlockAndState(block, state)));
    if (anchorFromMatchingStateRoot.isPresent()) {
      return anchorFromMatchingStateRoot.get();
    }

    final Bytes32 stateBlockRoot = BeaconBlockHeader.fromState(state).hashTreeRoot();
    return blocks.stream()
        .filter(block -> block.getRoot().equals(stateBlockRoot))
        .findFirst()
        .map(
            block -> {
              final UInt64 epoch = spec.computeNextEpochBoundary(state.getSlot());
              final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());
              return AnchorPoint.create(spec, checkpoint, state, Optional.of(block));
            })
        .orElseGet(() -> createAnchorFromState(spec, state));
  }
}
