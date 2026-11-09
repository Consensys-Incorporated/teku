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

package tech.pegasys.teku.benchmarks.ssz;

import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;

public class SszBenchUtil {
  private static final Spec SPEC = TestSpecFactory.createDefault();

  public static void iterateData(PendingAttestation pa, Blackhole bh) {
    bh.consume(pa.getAggregationBits());
    bh.consume(pa.getInclusionDelay());
    bh.consume(pa.getProposerIndex());
    iterateData(pa.getData(), bh);
  }

  public static void iterateData(BeaconBlockBody bbb, Blackhole bh) {
    bh.consume(bbb.getRandaoReveal());
    iterateData(bbb.getEth1Data(), bh);
    bh.consume(bbb.getGraffiti());
    bbb.getProposerSlashings().forEach(s -> iterateData(s, bh));
    bbb.getAttesterSlashings().forEach(s -> iterateData(s, bh));
    bbb.getAttestations().forEach(a -> iterateData(a, bh));
    bbb.getDeposits().forEach(d -> iterateData(d, bh));
    bbb.getVoluntaryExits().forEach(d -> iterateData(d, bh));
  }

  public static void iterateData(SignedVoluntaryExit d, Blackhole bh) {
    bh.consume(d.getSignature());
    iterateData(d.getMessage(), bh);
  }

  public static void iterateData(VoluntaryExit message, Blackhole bh) {
    bh.consume(message.getEpoch());
    bh.consume(message.getValidatorIndex());
  }

  public static void iterateData(Deposit d, Blackhole bh) {
    d.getProof().forEach(bh::consume);
    iterateData(d.getData(), bh);
  }

  public static void iterateData(DepositData data, Blackhole bh) {
    bh.consume(data.getPubkey());
    bh.consume(data.getWithdrawalCredentials());
    bh.consume(data.getAmount());
    bh.consume(data.getSignature());
  }

  public static void iterateData(Attestation a, Blackhole bh) {
    bh.consume(a.getAggregationBits());
    iterateData(a.getData(), bh);
    bh.consume(a.getAggregateSignature());
  }

  public static void iterateData(AttesterSlashing s, Blackhole bh) {
    iterateData(s.getAttestation1(), bh);
    iterateData(s.getAttestation2(), bh);
  }

  public static void iterateData(IndexedAttestation a, Blackhole bh) {
    a.getAttestingIndices().forEach(bh::consume);
    bh.consume(a.getSignature());
    iterateData(a.getData(), bh);
  }

  public static void iterateData(ProposerSlashing s, Blackhole bh) {
    iterateData(s.getHeader1(), bh);
    iterateData(s.getHeader2(), bh);
  }

  public static void iterateData(SignedBeaconBlockHeader h, Blackhole bh) {
    bh.consume(h.getSignature());
    iterateData(h.getMessage(), bh);
  }

  public static void iterateData(BeaconBlockHeader h, Blackhole bh) {
    bh.consume(h.getSlot());
    bh.consume(h.getProposerIndex());
    bh.consume(h.getParentRoot());
    bh.consume(h.getBodyRoot());
    bh.consume(h.getStateRoot());
  }

  public static void iterateData(Eth1Data ed, Blackhole bh) {
    bh.consume(ed.getDepositRoot());
    bh.consume(ed.getDepositCount());
    bh.consume(ed.getBlockHash());
  }

  public static void iterateData(AttestationData ad, Blackhole bh) {
    bh.consume(ad.getSlot());
    bh.consume(ad.getIndex());
    bh.consume(ad.getEarliestSlotForForkChoice(SPEC));
    iterateData(ad.getSource(), bh);
    iterateData(ad.getTarget(), bh);
  }

  public static void iterateData(Checkpoint cp, Blackhole bh) {
    bh.consume(cp.getEpoch());
    bh.consume(cp.getRoot());
  }
}
