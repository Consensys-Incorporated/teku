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

package tech.pegasys.teku.benchmarks.gen;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;

/**
 * Utility class for generating BLS keypairs and blocks files Test methods need to be run manually
 */
public class KeyFileGenerator {

  public static void main(final String[] args) throws Exception {
    final int randomSeed = 0;
    final Iterator<BLSKeyPair> keyPairIterator =
        IntStream.range(randomSeed, randomSeed + Integer.MAX_VALUE)
            .mapToObj(BLSTestUtil::randomKeyPair)
            .iterator();

    int limit = 400;
    if (args.length == 1) {
      // read cli positional args
      try {
        limit = Integer.parseInt(args[0]);
      } catch (Exception e) {
        dieUsage(Optional.of("Failed to parse limit: " + e.getMessage()));
      }
    } else if (args.length > 0) {
      dieUsage(Optional.empty());
    }
    System.out.println("Generating " + (limit * 1024) + " keys, at 400K keys per file.");
    int offset = 0;
    while (offset < limit) {
      final int rangeMax = Math.min(offset + 400, limit);
      generateKeyPairs(offset, rangeMax, keyPairIterator);
      offset = rangeMax;
    }
  }

  private static void dieUsage(final Optional<String> maybeContext) {
    maybeContext.ifPresent(System.out::println);
    System.out.println("Usage: keyFileGenerator <limitK>");
    System.exit(2);
  }

  // Used by other processes to read the keys generated and stored in bls-key-pairs
  // can specify up to 3_276_800 keys and a list will be returned to the caller
  public static List<BLSKeyPair> readValidatorKeys(final int limit) {
    if (limit > 3_276_800) {
      System.out.println(
          "Only resource files up to 3200K validators has been generated, insufficient stored keys to satisfy request.");
      throw new IllegalStateException("Cannot continue");
    }
    System.out.println("Loading keypairs...");
    final List<BLSKeyPair> validatorKeys = new ArrayList<>();

    final List<String> resources =
        List.of(
            "bls-key-pairs-0-400k.txt.gz",
            "bls-key-pairs-400-800k.txt.gz",
            "bls-key-pairs-800-1200k.txt.gz",
            "bls-key-pairs-1200-1600k.txt.gz",
            "bls-key-pairs-1600-2000k.txt.gz",
            "bls-key-pairs-2000-2400k.txt.gz",
            "bls-key-pairs-2400-2800k.txt.gz",
            "bls-key-pairs-2800-3200k.txt.gz");
    for (String resource : resources) {
      System.out.println(" -> " + resource);
      final int currentReaderLimit = limit - validatorKeys.size();
      try (BlsKeyPairIO.Reader reader =
          BlsKeyPairIO.createReaderForResource("/bls-key-pairs/" + resource)) {
        validatorKeys.addAll(reader.readAll(currentReaderLimit));
      } catch (Exception e) {
        System.out.println("Failed to read resource " + resource + ": " + e.getMessage());
      }
      if (validatorKeys.size() == limit) {
        break;
      }
    }

    System.out.println(validatorKeys.size() + " Keypairs loaded.");
    return validatorKeys;
  }

  private static void generateKeyPairs(
      final int startPosition, final int limitK, final Iterator<BLSKeyPair> keyPairIterator)
      throws Exception {
    File outFile = new File("bls-key-pairs-" + startPosition + "-" + limitK + "k.txt");

    System.out.println(" -> Generating keypairs from " + startPosition + "...");
    try (BlsKeyPairIO.Writer writer = BlsKeyPairIO.createWriter(outFile, keyPairIterator::next)) {
      for (int i = startPosition; i < limitK; i++) {
        writer.write(1024);
        if (i % 50 == 49) {
          System.out.println("  -> Generated " + (i + 1) + "K");
        }
      }
    }
  }
}
