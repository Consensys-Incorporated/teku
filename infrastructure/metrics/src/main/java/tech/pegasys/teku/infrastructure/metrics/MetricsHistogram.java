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

package tech.pegasys.teku.infrastructure.metrics;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MetricsHistogram {
  private static final double[] DEFAULT_BUCKETS =
      new double[] {
        0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
      };

  private final Histogram histogram;
  private final TimeProvider timeProvider;

  public MetricsHistogram(
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final MetricCategory category,
      final String name,
      final String help,
      final double... buckets) {
    this.histogram =
        metricsSystem.createHistogram(
            category, name, help, buckets.length > 0 ? buckets : DEFAULT_BUCKETS);
    this.timeProvider = timeProvider;
  }

  public MetricsHistogram(
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final MetricCategory category,
      final String name,
      final String help,
      final double[] buckets,
      final String... labels) {
    this.histogram =
        metricsSystem
            .createLabelledHistogram(
                category, name, help, buckets.length > 0 ? buckets : DEFAULT_BUCKETS)
            .labels(labels.length > 0 ? labels : new String[0]);
    this.timeProvider = timeProvider;
  }

  public static class Timer implements Closeable {
    private final Histogram histogram;
    private final TimeProvider timeProvider;
    private final UInt64 start;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public Timer(final Histogram histogram, final TimeProvider timeProvider) {
      this.histogram = histogram;
      this.timeProvider = timeProvider;
      this.start = timeProvider.getTimeInMillis();
    }

    @Override
    public void close() throws IOException {
      if (closed.compareAndSet(false, true)) {
        histogram.observe(timeProvider.getTimeInMillis().minusMinZero(start).doubleValue() / 1000);
      }
    }

    public Runnable closeUnchecked() {
      return () -> {
        try {
          close();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      };
    }
  }

  public Timer startTimer() {
    return new Timer(histogram, timeProvider);
  }
}
