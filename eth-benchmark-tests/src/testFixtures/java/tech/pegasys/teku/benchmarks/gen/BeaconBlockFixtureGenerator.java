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

package tech.pegasys.teku.benchmarks.gen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;

/**
 * Generates a fixture of real beacon-block SSZ used by {@code BeaconBlockSnappyCodecBenchmark} so
 * the Snappy codecs can be benchmarked against genuine consensus data instead of synthetic
 * payloads.
 *
 * <p>Blocks are produced with {@link ChainBuilder} on a mainnet-Deneb spec and include the
 * attestations available at each slot, so the SSZ carries realistic structure (BLS signatures,
 * roots, aggregation bitlists, an execution payload header). Note these are consensus-layer blocks
 * without large execution-layer transaction payloads, so they are more compressible than full
 * mainnet blocks dominated by EL calldata.
 *
 * <p>Regenerate the committed fixture with:
 *
 * <pre>./gradlew :eth-benchmark-tests:generateBeaconBlockFixture</pre>
 */
public class BeaconBlockFixtureGenerator {

  public static final String RESOURCE = "/snappy/beacon-blocks-deneb.ssz.gz";

  private static final int VALIDATOR_COUNT = 2048;
  private static final int BLOCK_COUNT = 40;

  public static void main(final String[] args) throws Exception {
    final String outFile = args.length > 0 ? args[0] : "src/jmh/resources" + RESOURCE;
    Files.createDirectories(Path.of(outFile).toAbsolutePath().getParent());

    final Spec spec = TestSpecFactory.createMainnetDeneb();
    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, VALIDATOR_COUNT);
    final ChainBuilder chainBuilder = ChainBuilder.create(spec, validatorKeys);
    chainBuilder.generateGenesis(UInt64.ZERO, true);
    final int maxAttestations = spec.getGenesisSpecConfig().getMaxAttestations();

    System.out.printf(
        "Generating %d Deneb blocks with %d validators -> %s%n",
        BLOCK_COUNT, VALIDATOR_COUNT, outFile);

    long rawTotal = 0;
    try (BlockIO.Writer writer = BlockIO.createFileWriter(outFile)) {
      for (int slot = 1; slot <= BLOCK_COUNT; slot++) {
        final UInt64 currentSlot = UInt64.valueOf(slot);
        final BlockOptions options = BlockOptions.create();
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(currentSlot)
            .limit(maxAttestations)
            .forEach(options::addAttestation);
        final SignedBeaconBlock block =
            chainBuilder.generateBlockAtSlot(currentSlot, options).getBlock();
        final int blockSize = block.sszSerialize().size();
        writer.accept(block);
        rawTotal += blockSize;
        System.out.printf("  slot %2d -> %,8d bytes%n", slot, blockSize);
      }
    }
    System.out.printf(
        "Wrote %d blocks, %,d raw SSZ bytes (avg %,d) to %s%n",
        BLOCK_COUNT, rawTotal, rawTotal / BLOCK_COUNT, outFile);
  }
}
