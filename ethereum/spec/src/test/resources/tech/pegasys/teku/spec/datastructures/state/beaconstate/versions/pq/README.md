`pq_genesis_minimal.ssz` is a PQ-at-genesis `BeaconState` (minimal preset, 64 validators) built by
the ethpandaops genesis generator's `pq` image (eth-beacon-genesis `284025a0e8`), through
ethereum-package `pq` (`580ca2ff18`) with `pq_fork_epoch: 0`, on 2026-11-19. It is an independent
(Go) implementation of the consensus-specs `pq` containers, used to cross-check Teku's PQ schemas.

Its `genesis_validators_root` is
`0x1532f07efb06c2d3db78b66ebfebbc2ec80e54163c9eb62428df0bf3d7ad874a`.
