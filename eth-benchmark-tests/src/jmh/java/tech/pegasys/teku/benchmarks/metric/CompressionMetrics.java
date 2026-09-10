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

package tech.pegasys.teku.benchmarks.metric;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Shared JMH auxiliary counter that reports the compression ratio alongside the timing results of
 * the Snappy codec benchmarks.
 */
@AuxCounters(AuxCounters.Type.EVENTS)
@State(Scope.Thread)
public class CompressionMetrics {
  private double compressionRatio;

  public void record(
      final long rawDataSizeBytes, final long encodedSizeBytes, final int measurementCount) {
    // JMH sums EVENTS counters across measurement iterations, so each iteration contributes its
    // share of the constant ratio.
    compressionRatio = (double) rawDataSizeBytes / encodedSizeBytes / measurementCount;
  }

  public double compressionRatio() {
    return compressionRatio;
  }
}
