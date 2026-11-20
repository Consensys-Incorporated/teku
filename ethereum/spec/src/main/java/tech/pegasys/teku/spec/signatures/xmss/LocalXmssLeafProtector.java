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

package tech.pegasys.teku.spec.signatures.xmss;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.xmss.XmssPublicKey;

/**
 * Keeps each key's {@link XmssLeafRecord} in its own file, {@code <public key>.xmss-leaves}, in the
 * slashing protection directory. A record is written and synced to disk before signing is allowed.
 */
public class LocalXmssLeafProtector implements XmssLeafProtector {

  static final String FILE_SUFFIX = ".xmss-leaves";

  private final Map<XmssPublicKey, XmssLeafRecord> records = new HashMap<>();
  private final SyncDataAccessor dataAccessor;
  private final Path baseDir;

  public LocalXmssLeafProtector(final SyncDataAccessor dataAccessor, final Path baseDir) {
    this.dataAccessor = dataAccessor;
    this.baseDir = baseDir;
  }

  @Override
  public synchronized SafeFuture<Boolean> maySign(
      final XmssPublicKey publicKey, final UInt64 leafIndex, final Bytes32 signingRoot) {
    return SafeFuture.of(
        () -> {
          final XmssLeafRecord record = loadRecord(publicKey);
          final Optional<XmssLeafRecord> updated = record.maySign(leafIndex, signingRoot);
          if (updated.isEmpty()) {
            return false;
          }
          writeRecord(publicKey, record, updated.get());
          return true;
        });
  }

  @Override
  public synchronized SafeFuture<Optional<UInt64>> allocateReservedLeaf(
      final XmssPublicKey publicKey, final Bytes32 signingRoot) {
    return SafeFuture.of(
        () -> {
          final XmssLeafRecord record = loadRecord(publicKey);
          final Optional<UInt64> leaf = record.nextReservedLeaf();
          if (leaf.isEmpty()) {
            return Optional.empty();
          }
          // The next reserved leaf is above every recorded one, so it is always unused
          final XmssLeafRecord updated = record.maySign(leaf.get(), signingRoot).orElseThrow();
          writeRecord(publicKey, record, updated);
          return leaf;
        });
  }

  private XmssLeafRecord loadRecord(final XmssPublicKey publicKey) throws IOException {
    final XmssLeafRecord cached = records.get(publicKey);
    if (cached != null) {
      return cached;
    }
    final XmssLeafRecord record =
        dataAccessor
            .read(recordPath(publicKey))
            .map(XmssLeafRecord::fromBytes)
            .orElseGet(XmssLeafRecord::empty);
    records.put(publicKey, record);
    return record;
  }

  private void writeRecord(
      final XmssPublicKey publicKey, final XmssLeafRecord current, final XmssLeafRecord updated)
      throws IOException {
    if (updated.equals(current)) {
      // Already recorded with this signing root
      return;
    }
    dataAccessor.syncedWrite(recordPath(publicKey), updated.toBytes());
    records.put(publicKey, updated);
  }

  private Path recordPath(final XmssPublicKey publicKey) {
    return baseDir.resolve(publicKey.toBytes().toUnprefixedHexString() + FILE_SUFFIX);
  }
}
