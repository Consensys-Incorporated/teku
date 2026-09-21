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

package tech.pegasys.teku.data.eraFileFormat;

import com.google.common.base.Preconditions;
import io.airlift.compress.v3.snappy.SnappyDecompressor;
import io.airlift.compress.v3.snappy.SnappyFramedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class EraFile {
  private final Spec spec;
  private final ByteBuffer byteBuffer;
  private final RandomAccessFile file;
  private final FileChannel channel;
  private final long fileLength;

  private ReadSlotIndex stateIndices;
  private ReadSlotIndex blockIndices;
  private final String filename;
  private UInt64 currentSlot;
  private SignedBeaconBlock lastBlock = null;

  public EraFile(final Path path, final Spec spec) throws IOException {
    filename = path.getFileName().toString();
    file = new RandomAccessFile(path.toFile(), "r");
    channel = file.getChannel();
    fileLength = channel.size();
    this.spec = spec;

    final MappedByteBuffer mbb =
        channel.map(
            FileChannel.MapMode.READ_ONLY,
            0, // position
            file.length());
    byteBuffer = mbb.order(ByteOrder.LITTLE_ENDIAN);
  }

  public void readEraFile() {
    getStateIndices();
    getBlockIndices();
  }

  public void printStats() {
    int offset = 0;
    final Map<Bytes, Integer> entries = new HashMap<>();
    final Map<Bytes, Integer> entryBytes = new HashMap<>();
    while (offset < fileLength) {
      ReadEntry entry = new ReadEntry(byteBuffer, offset);
      offset += 8; // header
      offset += (int) entry.getDataSize();
      final Bytes key = Bytes.wrap(entry.getType());
      int i = entries.getOrDefault(key, 0);
      int size = entryBytes.getOrDefault(key, 0);
      entries.put(key, i + 1);
      entryBytes.put(key, size + (int) entry.getDataSize());
    }

    System.out.println("\nSummary Statistics");
    for (Bytes key : entries.keySet().stream().sorted().toList()) {
      final int count = entries.get(key);
      final int bytes = entryBytes.get(key);
      final double average = (double) bytes / (double) count;
      System.out.printf(
          "\ttype %s, bytes %10d, count %5d, average %12.02f%n",
          key.toUnprefixedHexString(), bytes, count, average);
    }
  }

  private void getStateIndices() {
    stateIndices = new ReadSlotIndex(byteBuffer, (int) fileLength);
    long recordStart = stateIndices.getRecordStart();
    if (!stateIndices.getEntry().isIndexType()) {
      throw new RuntimeException(
          String.format(
              " -- State e2store record type (i2) was not found at starting position %s",
              recordStart));
    }
    System.out.println("\tState slot: " + stateIndices.getStartSlot());
    System.out.println("\tState index start: " + recordStart);
  }

  private BeaconState getBeaconState(final ReadEntry entry) throws IOException {
    final SnappyFramedInputStream is =
        new SnappyFramedInputStream(
            SnappyDecompressor.create(), new ByteArrayInputStream(entry.getData()));

    return spec.atSlot(stateIndices.getStartSlot())
        .getSchemaDefinitions()
        .getBeaconStateSchema()
        .sszDeserialize(Bytes.of(is.readAllBytes()));
  }

  private SignedBeaconBlock getBlock(final ReadEntry entry) throws IOException {
    final SnappyFramedInputStream is =
        new SnappyFramedInputStream(
            SnappyDecompressor.create(), new ByteArrayInputStream(entry.getData()));

    if (currentSlot == null) {
      currentSlot = blockIndices.getStartSlot();
    }
    return spec.atSlot(currentSlot)
        .getSchemaDefinitions()
        .getSignedBeaconBlockSchema()
        .sszDeserialize(Bytes.of(is.readAllBytes()));
  }

  private BeaconState verifyStateInArchive() throws IOException {
    final int offset = stateIndices.getSlotOffsets().get(0);
    // offset is negative, relative to start of index
    if (stateIndices.getRecordStart() + offset < 0) {
      System.out.println("Offset for state goes beyond length of file");
    }
    final ReadEntry stateEntry =
        new ReadEntry(byteBuffer, (int) stateIndices.getRecordStart() + offset);
    Preconditions.checkArgument(
        stateEntry.isStateType(), "The first state index doesn't point to state data.");
    final BeaconState state = getBeaconState(stateEntry);
    Preconditions.checkArgument(
        stateIndices.getStartSlot().equals(state.getSlot()),
        "Reported state slot does not match the stored state.");
    System.out.println(
        "State index matches the stored state stored - slot "
            + state.getSlot()
            + " ("
            + state.hashTreeRoot()
            + ")");
    return state;
  }

  private void verifyFile(final SignedBeaconBlock previousArchiveLastBlock) throws IOException {
    System.out.println("\nVerifying " + filename);
    final BeaconState verifiedState = verifyStateInArchive();
    if (!verifiedState.getSlot().isZero()) {
      verifyBlocksWithReferenceState(verifiedState, previousArchiveLastBlock);
    }
  }

  public SignedBeaconBlock getLastBlock() {
    return lastBlock;
  }

  private void verifyBlocksWithReferenceState(
      final BeaconState verifiedState, final SignedBeaconBlock previousArchiveLastBlock)
      throws IOException {
    currentSlot = blockIndices.getStartSlot();
    Bytes32 lastRoot = Bytes32.ZERO;
    int populatedSlots = 0;
    int emptySlots = 0;
    for (int i = 0; i < blockIndices.getCount(); i++) {
      final int offset = blockIndices.getSlotOffsets().get(i);
      if (blockIndices.getRecordStart() + offset == 0) {
        currentSlot = currentSlot.increment();
        Preconditions.checkArgument(
            spec.getBlockRootAtSlot(verifiedState, currentSlot).equals(lastRoot),
            "Block at slot %s did not match the root stored in the reference state.",
            currentSlot);
        ++emptySlots;
        continue;
      }
      Preconditions.checkArgument(
          blockIndices.getRecordStart() + offset > 0,
          "Offset for block goes beyond length of file - start: %s; offset requested: %s; pos: %s",
          blockIndices.getRecordStart(),
          offset,
          i);
      final ReadEntry entry =
          new ReadEntry(byteBuffer, (int) blockIndices.getRecordStart() + offset);
      final SignedBeaconBlock block = getBlock(entry);
      lastBlock = block;

      currentSlot = block.getSlot();
      Preconditions.checkArgument(
          spec.getBlockRootAtSlot(verifiedState, block.getSlot()).equals(block.getRoot()),
          "Block at slot %s did not match the root stored in the reference state.",
          block.getSlot());
      if (!lastRoot.isZero()) {
        Preconditions.checkArgument(
            block.getParentRoot().equals(lastRoot),
            "Parent root did not match at slot %s",
            currentSlot);
      }
      lastRoot = block.getRoot();
      if (populatedSlots == 0 && previousArchiveLastBlock != null) {
        Preconditions.checkArgument(
            block.getParentRoot().equals(previousArchiveLastBlock.getRoot()),
            "First block in archive does not match last block of previous archive.");
      }
      // when fully implemented, we would check signature also
      ++populatedSlots;
    }
    System.out.println(
        "Verified block chain for "
            + populatedSlots
            + " populated slots and "
            + emptySlots
            + " empty slots");
  }

  private void getBlockIndices() {
    if (stateIndices.getStartSlot().isGreaterThan(UInt64.ZERO)) {
      blockIndices = new ReadSlotIndex(byteBuffer, (int) stateIndices.getRecordStart());
      System.out.println("\tCount of blocks: " + blockIndices.getCount());
      System.out.println("\tBlock start slot: " + blockIndices.getStartSlot());
      System.out.println("\tOffsets: " + blockIndices.getSlotOffsets().size());
    }
  }

  final void close() throws IOException {
    channel.close();
    file.close();
  }

  public static void main(final String[] args) throws IOException {
    if (args.length == 0) {
      System.out.println("Usage: eraFile [filename]...");
      System.exit(2);
    }
    final Spec spec = getNetworkFromEraFilename(args[0]);
    SignedBeaconBlock lastBlock = null;
    for (final String filename : args) {
      final Path p = Path.of(filename);
      final EraFile f = new EraFile(p, spec);
      f.readEraFile();
      f.printStats();
      f.verifyFile(lastBlock);
      lastBlock = f.getLastBlock();

      f.close();
    }
  }

  private static Spec getNetworkFromEraFilename(final String filename) {
    final Path p = Path.of(filename);
    final String network =
        Arrays.stream(p.getFileName().toString().toLowerCase(Locale.ROOT).split("-", 2))
            .findFirst()
            .orElseThrow();
    System.out.println("Loading network: " + network);
    return SpecFactory.create(network);
  }
}
