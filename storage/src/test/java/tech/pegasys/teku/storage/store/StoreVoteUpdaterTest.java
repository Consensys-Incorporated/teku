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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;

class StoreVoteUpdaterTest extends AbstractStoreTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  final VoteUpdateChannel voteUpdateChannel = mock(VoteUpdateChannel.class);

  private final UpdatableStore store = createGenesisStore();

  @Test
  void shouldSendUpdatesToStorageOnCommit() {
    final VoteUpdater voteUpdater = store.startVoteUpdate(voteUpdateChannel);
    final VoteTracker updatedVote = dataStructureUtil.randomVoteTracker();

    voteUpdater.putVote(UInt64.ZERO, updatedVote);
    verifyNoInteractions(voteUpdateChannel);

    voteUpdater.commit();
    verify(voteUpdateChannel).onVotesUpdated(Map.of(UInt64.ZERO, updatedVote));
  }

  @Test
  void shouldNotApplyChangesUntilCommitCalled() {
    final VoteUpdater voteUpdater = store.startVoteUpdate(voteUpdateChannel);
    final VoteTracker updatedVote = dataStructureUtil.randomVoteTracker();
    voteUpdater.putVote(UInt64.ZERO, updatedVote);

    assertVote(UInt64.ZERO, VoteTracker.DEFAULT);

    voteUpdater.commit();
    assertVote(UInt64.ZERO, updatedVote);
  }

  @Test
  void shouldIncludeUncommittedVotesInHighestVotedValidatorIndex() {
    setVote(UInt64.ZERO, dataStructureUtil.randomVoteTracker());
    setVote(UInt64.ONE, dataStructureUtil.randomVoteTracker());

    final VoteUpdater voteUpdater = store.startVoteUpdate(voteUpdateChannel);
    voteUpdater.putVote(UInt64.valueOf(2), dataStructureUtil.randomVoteTracker());

    assertThat(voteUpdater.getHighestVotedValidatorIndex()).isEqualTo(UInt64.valueOf(2));
  }

  private void setVote(final UInt64 validatorIndex, final VoteTracker vote) {
    final VoteUpdater voteUpdater = store.startVoteUpdate(voteUpdateChannel);
    voteUpdater.putVote(validatorIndex, vote);
    voteUpdater.commit();
  }

  private void assertVote(final UInt64 validatorIndex, final VoteTracker expectedVote) {
    final VoteTracker actualVote = store.startVoteUpdate(voteUpdateChannel).getVote(validatorIndex);
    assertThat(actualVote).isEqualTo(expectedVote);
  }
}
