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

package tech.pegasys.teku.spec.propertytest.util;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;

public class Mutator {
  /** The types of mutations that can occur. */
  private enum Mutation {
    REPLACE,
    ADD_BEFORE,
    ADD_AFTER,
    DELETE
  }

  /** Likelihood that a specific mutation will happen. A number between 0 and 1. */
  private static final Map<Mutation, Double> MUTATION_PROBABILITIES =
      Map.of(
          Mutation.REPLACE, 0.01,
          Mutation.ADD_BEFORE, 0.001,
          Mutation.ADD_AFTER, 0.001,
          Mutation.DELETE, 0.001);

  /** A map used to determine which mutation should occur, if any. */
  private static final TreeMap<Double, Mutation> PROBABILITY_MAP = new TreeMap<>();

  static {
    // Make a probability map to be queried with TreeMap.ceilingEntry(). For each non-zero
    // probability, create a new entry where the key is the cumulative probability. Given a random
    // number between 0 and 1, TreeMap.ceilingEntry(randomNumber) will return a mutation (or null)
    // that corresponds to its probability. You could expect this map to look something like:
    //
    // {
    //    0.010: REPLACE
    //    0.011: ADD_BEFORE
    //    0.012: ADD_AFTER
    //    0.013: DELETE
    // }
    double cumulativeProbability = 0.0;
    for (Map.Entry<Mutation, Double> entry : MUTATION_PROBABILITIES.entrySet()) {
      if (entry.getValue() > 0.0) {
        cumulativeProbability += entry.getValue();
        PROBABILITY_MAP.put(cumulativeProbability, entry.getKey());
      }
    }
  }

  /**
   * A helper function for generating random input data. Instead of blinding making data, start with
   * a known valid data and slightly mutate that. By default, you can expect a ~1% mutation rate.
   *
   * @param input The bytes to mutate, should resemble valid data.
   * @param seed The seed that should be used for randomness, for reproducibility.
   * @return A mutated version of the input.
   */
  public static Bytes mutate(final Bytes input, final int seed) {
    final Random random = new Random(seed);
    final ByteArrayOutputStream out = new ByteArrayOutputStream(input.size());

    for (final byte b : input.toArray()) {
      final double randomNumber = random.nextDouble();
      final Map.Entry<Double, Mutation> entry = PROBABILITY_MAP.ceilingEntry(randomNumber);
      if (entry != null) {
        switch (entry.getValue()) {
          case ADD_BEFORE -> {
            final byte before = (byte) random.nextInt();
            out.write(before);
            out.write(b);
          }
          case ADD_AFTER -> {
            final byte after = (byte) random.nextInt();
            out.write(b);
            out.write(after);
          }
          case REPLACE -> {
            final byte replace = (byte) random.nextInt();
            out.write(replace);
          }
          case DELETE -> {}
        }
      } else {
        out.write(b);
      }
    }
    return Bytes.wrap(out.toByteArray());
  }
}
