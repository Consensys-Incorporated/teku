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

package tech.pegasys.teku.statetransition.pq.timing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.annotations.VisibleForTesting;
import io.airlift.compress.v3.zstd.ZstdOutputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Writes PQ timing events to a run directory in the format of the Lighthouse pq-measurement reports
 * (schema version 1): a run.json manifest plus zstd-compressed JSON lines chunks.
 *
 * <p>The open chunk is written as plain JSON lines ({@code events-NNNNNN.jsonl.partial}), flushed
 * with every status, and compressed when it is finished: the zstd stream holds megabytes before
 * writing a block and can't flush one, so a compressed open chunk would stay empty for minutes, and
 * all of it would be lost if the node were killed.
 *
 * <p>Emitting never blocks. A full queue drops the event and counts it in the manifest, so gossip
 * validation and proving are never held up by the disk.
 */
public class PqTimingRecorder {

  private static final Logger LOG = LogManager.getLogger();

  static final int QUEUE_SIZE = 16384;
  static final long ROTATE_BYTES = 64L << 20;
  static final long RETAIN_BYTES = 8L << 30;
  static final long ROTATE_NANOS = TimeUnit.MINUTES.toNanos(5);
  static final long STATUS_NANOS = TimeUnit.SECONDS.toNanos(10);

  private static final Entry CLOSE = new Entry(null);
  private static final ObjectMapper JSON =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  private static final ObjectMapper JSON_LINE = new ObjectMapper();

  public enum EventClass {
    RECEIPT("receipt"),
    VALIDATION("validation"),
    WORK("work"),
    STATUS("status");

    private final String label;

    EventClass(final String label) {
      this.label = label;
    }
  }

  private final Path directory;
  private final Map<String, Object> metadata;
  private final LongSupplier wallNanos;
  private final LongSupplier monoNanos;
  private final long originMonoNanos;
  private final long rotateBytes;
  private final long retainBytes;
  private final BlockingQueue<Entry> queue;
  private final ReadWriteLock closeLock = new ReentrantReadWriteLock();
  private final AtomicBoolean failed = new AtomicBoolean();
  private final AtomicLong sequence = new AtomicLong();
  private final Map<EventClass, AtomicLong> dropped = new EnumMap<>(EventClass.class);
  private final Thread writer;
  private boolean closed;
  private long firstDroppedSequence;
  private long lastDroppedSequence;
  private long firstDroppedWallNanos;
  private long lastDroppedWallNanos;

  private PqTimingRecorder(
      final Path directory,
      final Map<String, Object> metadata,
      final LongSupplier wallNanos,
      final LongSupplier monoNanos,
      final int queueSize,
      final long rotateBytes,
      final long retainBytes) {
    this.directory = directory;
    this.metadata = new LinkedHashMap<>(metadata);
    this.wallNanos = wallNanos;
    this.monoNanos = monoNanos;
    this.originMonoNanos = monoNanos.getAsLong();
    this.rotateBytes = rotateBytes;
    this.retainBytes = retainBytes;
    this.queue = new ArrayBlockingQueue<>(queueSize);
    for (final EventClass eventClass : EventClass.values()) {
      dropped.put(eventClass, new AtomicLong());
    }
    this.writer = new Thread(this::write, "pq-timing-writer");
    writer.setDaemon(true);
  }

  /** Creates the run directory, writes its first manifest and starts the writer thread. */
  public static PqTimingRecorder open(
      final Path root,
      final String runId,
      final Map<String, Object> metadata,
      final LongSupplier wallNanos,
      final LongSupplier monoNanos)
      throws IOException {
    return open(root, runId, metadata, wallNanos, monoNanos, QUEUE_SIZE, ROTATE_BYTES);
  }

  @VisibleForTesting
  static PqTimingRecorder open(
      final Path root,
      final String runId,
      final Map<String, Object> metadata,
      final LongSupplier wallNanos,
      final LongSupplier monoNanos,
      final int queueSize,
      final long rotateBytes)
      throws IOException {
    final Path directory = root.resolve(runId);
    Files.createDirectories(directory);
    final PqTimingRecorder recorder =
        new PqTimingRecorder(
            directory, metadata, wallNanos, monoNanos, queueSize, rotateBytes, RETAIN_BYTES);
    recorder.metadata.put("recorder_status", recorder.status());
    recorder.metadata.put("chunks", List.of());
    recorder.writeMetadata();
    recorder.writer.start();
    return recorder;
  }

  public Path getDirectory() {
    return directory;
  }

  public boolean isFailed() {
    return failed.get();
  }

  @VisibleForTesting
  long getDropped(final EventClass eventClass) {
    return dropped.get(eventClass).get();
  }

  public long nowWallNanos() {
    return wallNanos.getAsLong();
  }

  public long nowMonoNanos() {
    return monoNanos.getAsLong();
  }

  /** Records an event stamped now. */
  public void emit(
      final EventClass eventClass, final String kind, final Map<String, Object> fields) {
    emit(eventClass, kind, nowWallNanos(), nowMonoNanos(), fields);
  }

  /**
   * Records an event stamped with an earlier time, such as a gossip receipt that is only described
   * once the message is decoded.
   */
  public void emit(
      final EventClass eventClass,
      final String kind,
      final long wallNanos,
      final long monoNanos,
      final Map<String, Object> fields) {
    if (failed.get()) {
      return;
    }
    final long seq = sequence.incrementAndGet();
    final Map<String, Object> event = new LinkedHashMap<>(fields);
    event.put("seq", seq);
    event.put("kind", kind);
    event.put("wall_ns", wallNanos);
    event.put("mono_ns", monoNanos - originMonoNanos);
    closeLock.readLock().lock();
    try {
      if (!closed && queue.offer(new Entry(event))) {
        return;
      }
    } finally {
      closeLock.readLock().unlock();
    }
    dropped.get(eventClass).incrementAndGet();
    synchronized (this) {
      if (firstDroppedSequence == 0) {
        firstDroppedSequence = seq;
        firstDroppedWallNanos = wallNanos;
      }
      lastDroppedSequence = seq;
      lastDroppedWallNanos = wallNanos;
    }
  }

  /** Stops accepting events, then waits for the writer to finish the last chunk and manifest. */
  public void close() {
    closeLock.writeLock().lock();
    try {
      if (closed) {
        return;
      }
      closed = true;
    } finally {
      closeLock.writeLock().unlock();
    }
    try {
      queue.put(CLOSE);
      writer.join(TimeUnit.SECONDS.toMillis(30));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private synchronized Map<String, Object> status() {
    final Map<String, Object> droppedByClass = new LinkedHashMap<>();
    for (final EventClass eventClass : EventClass.values()) {
      droppedByClass.put(eventClass.label, dropped.get(eventClass).get());
    }
    final Map<String, Object> status = new LinkedHashMap<>();
    status.put("disk_failed", failed.get());
    status.put("dropped", droppedByClass);
    status.put("first_dropped_sequence", firstDroppedSequence == 0 ? null : firstDroppedSequence);
    status.put("last_dropped_sequence", lastDroppedSequence);
    status.put("submitted_sequence", sequence.get());
    status.put("first_dropped_wall_ns", firstDroppedWallNanos);
    status.put("last_dropped_wall_ns", lastDroppedWallNanos);
    return status;
  }

  private void writeMetadata() throws IOException {
    final Path partial = directory.resolve("run.json.partial");
    Files.write(partial, JSON.writeValueAsBytes(metadata));
    Files.move(
        partial,
        directory.resolve("run.json"),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
  }

  private void write() {
    try {
      writeEvents();
    } catch (final IOException | RuntimeException e) {
      failed.set(true);
      LOG.error("PQ timing export disabled after a writer error", e);
      metadata.put("recorder_status", status());
      try {
        writeMetadata();
      } catch (final IOException metadataError) {
        LOG.warn("Could not record the PQ timing export failure", metadataError);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void writeEvents() throws IOException, InterruptedException {
    Chunk current = null;
    int number = 0;
    final Deque<ChunkInfo> retained = new ArrayDeque<>();
    long retainedBytes = 0;
    long evictedChunks = 0;
    long evictedFirst = 0;
    long evictedLast = 0;
    long lastStatus = originMonoNanos;
    while (true) {
      final Entry entry =
          queue.poll(
              Math.max(1, STATUS_NANOS - (monoNanos.getAsLong() - lastStatus)),
              TimeUnit.NANOSECONDS);
      final boolean closing = entry != null && entry.event() == null;
      if (entry != null && !closing) {
        if (current == null) {
          current = Chunk.create(directory, ++number, monoNanos.getAsLong());
        }
        current.write(entry.event());
      }
      final boolean rotate =
          current != null
              && (closing
                  || current.bytes >= rotateBytes
                  || monoNanos.getAsLong() - current.startedMonoNanos >= ROTATE_NANOS);
      if (rotate) {
        final ChunkInfo info = current.finish(directory);
        current = null;
        retained.addLast(info);
        retainedBytes += info.bytes();
        while (retainedBytes > retainBytes && retained.size() > 1) {
          final ChunkInfo old = retained.removeFirst();
          Files.delete(directory.resolve(old.name()));
          retainedBytes -= old.bytes();
          if (evictedChunks == 0) {
            evictedFirst = old.first();
          }
          evictedChunks++;
          evictedLast = old.last();
        }
      }
      if (rotate || closing || monoNanos.getAsLong() - lastStatus >= STATUS_NANOS) {
        if (current != null) {
          current.flush();
        }
        final Map<String, Object> status = status();
        status.put("evicted_chunks", evictedChunks);
        if (evictedChunks != 0) {
          status.put("evicted_sequence_bounds", List.of(evictedFirst, evictedLast));
        }
        status.put("checked_unix_ns", wallNanos.getAsLong());
        metadata.put("chunks", new ArrayList<>(retained));
        metadata.put("recorder_status", status);
        if (closing) {
          metadata.put("ended_unix_ns", wallNanos.getAsLong());
        }
        writeMetadata();
        lastStatus = monoNanos.getAsLong();
      }
      if (closing) {
        return;
      }
    }
  }

  /** A queued event, or the end of the run when it holds none. */
  private record Entry(Map<String, Object> event) {}

  /** A finished chunk as listed in run.json. */
  record ChunkInfo(String name, long bytes, long first, long last) {}

  private static final class Chunk {
    private final String name;
    private final Path partial;
    private final OutputStream stream;
    private final long startedMonoNanos;
    private long bytes;
    private long first;
    private long last;

    private Chunk(
        final String name,
        final Path partial,
        final OutputStream stream,
        final long startedMonoNanos) {
      this.name = name;
      this.partial = partial;
      this.stream = stream;
      this.startedMonoNanos = startedMonoNanos;
    }

    static Chunk create(final Path directory, final int number, final long startedMonoNanos)
        throws IOException {
      final String name = String.format("events-%06d.jsonl.zst", number);
      final Path partial = directory.resolve(String.format("events-%06d.jsonl.partial", number));
      final OutputStream stream = new BufferedOutputStream(Files.newOutputStream(partial));
      return new Chunk(name, partial, stream, startedMonoNanos);
    }

    /** Writes the events so far to the open chunk, where they can be read. */
    void flush() throws IOException {
      stream.flush();
    }

    void write(final Map<String, Object> event) throws IOException {
      final byte[] line = JSON_LINE.writeValueAsBytes(event);
      stream.write(line);
      stream.write('\n');
      final long seq = (long) event.get("seq");
      first = first == 0 ? seq : Math.min(first, seq);
      last = Math.max(last, seq);
      bytes += line.length + 1;
    }

    /** Compresses the chunk into its final name, then removes the open chunk. */
    ChunkInfo finish(final Path directory) throws IOException {
      stream.close();
      final Path compressing = directory.resolve(name + ".partial");
      try (InputStream in = Files.newInputStream(partial);
          OutputStream out =
              new ZstdOutputStream(new BufferedOutputStream(Files.newOutputStream(compressing)))) {
        in.transferTo(out);
      }
      final long size = Files.size(compressing);
      Files.move(compressing, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE);
      Files.delete(partial);
      return new ChunkInfo(name, size, first, last);
    }
  }
}
