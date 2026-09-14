# AI Agent Instructions & Project Context

This file provides guidance to AI assistants when working with code in this repository.

## Project Overview

Teku is an open-source Ethereum consensus client written in Java, implementing a full beacon node and validator client. It is written in Java 25+ and follows the Ethereum consensus specifications.

## Build and Development Commands

### Building
```bash
# Full build with tests
./gradlew build

# Build without tests
./gradlew assemble

# Create distribution packages
./gradlew distTar installDist

# Build Docker image
./gradlew distDocker
```

### Code Style
```bash
# Apply Google Java code formatting (required before committing)
./gradlew spotlessApply

# Check code formatting
./gradlew spotlessCheck
```

### Testing
```bash
# Run all unit tests
./gradlew test

# Run tests for a specific module (example)
./gradlew :ethereum:spec:test

# Run integration tests
./gradlew integrationTest

# Run reference tests (consensus spec tests)
./gradlew referenceTest

# Run one reference suite manually (example)
ENV_TEST_TYPE=fork_choice/on_attestation ENV_SPEC=minimal ENV_MILESTONE=gloas ./gradlew --no-daemon :eth-reference-tests:referenceTest --tests tech.pegasys.teku.reference.ManualReferenceTestRunner -x generateReferenceTestClasses

# Run acceptance tests
./gradlew acceptanceTest

# Run property tests
./gradlew propertyTest
```

### Specrefs
Spec references live in `specrefs/` and reference the [Ethereum consensus specs](https://github.com/ethereum/consensus-specs/) or the user's local checkout of that repository. Run from the repository root:
```bash
ethspecify process --path=specrefs
ethspecify check --path=specrefs
```

When updating specrefs, keep exceptions only for references that are genuinely unimplemented, unnecessary, or intentionally implemented differently in Teku. If a reference has a real implementation source, remove it from exceptions and add stable `sources` anchors. After large generated diffs, check for duplicate entries:
```bash
rg '^- name:' specrefs/*.yml | sed 's/.*:- name: //' | sort | uniq -d
```

### Distribution
The built distribution is located in:
- Packaged: `build/distributions/`
- Expanded (ready to run): `build/install/teku/`

## Architecture Overview

### High-Level Architecture Diagram

```mermaid
graph TB
    subgraph "Application Layer"
        CLI[Teku CLI / BeaconNodeCommand]
        CLI --> Node[Node<br/>COMBINED/VC_ONLY/BOOTNODE]
    end

    subgraph "Service Layer"
        Node --> SC[ServiceController]
        SC --> SS[StorageService]
        SC --> ELS[ExecutionLayerService]
        SC --> BCS[BeaconChainService]
        SC --> PCS[PowchainService]
        SC --> VCS[ValidatorClientService]
        SC --> TS[TimerService]
    end

    subgraph "Core Logic Layer"
        BCS --> Spec[ethereum/spec<br/>Consensus Rules]
        BCS --> ST[ethereum/statetransition<br/>State Processing]
        BCS --> Sync[beacon/sync<br/>Block Sync]
        VCS --> ValClient[validator/client<br/>Duties & Slashing Protection]
        ELS --> ELM[ExecutionLayerManager<br/>Engine API / Builder API]
    end

    subgraph "Data & Storage Layer"
        SS --> DB[(Database<br/>RocksDB/LevelDB)]
        SS --> PA[storage/protoarray<br/>Fork Choice]
        BCS --> Storage[storage/api<br/>ChainStorage]
        Storage --> DB
    end

    subgraph "Networking Layer"
        BCS --> Net[networking/p2p<br/>Libp2p Gossip]
        Net --> Peers[Peer Network]
    end

    subgraph "API Layer"
        BCS --> RestAPI[data/beaconrestapi<br/>Beacon API]
        VCS --> ValAPI[validator/api<br/>Validator API]
    end

    subgraph "External Integration"
        ELM --> EL[Execution Layer Client<br/>Geth/Nethermind/Besu]
        ELM --> Builder[MEV-Boost Builder]
        PCS --> ETH1[Ethereum PoW Chain<br/>Deposit Contract]
    end

    subgraph "Event Channels (Event-Driven Communication)"
        TS -.SlotEventsChannel.-> BCS
        BCS -.VoteUpdateChannel.-> PA
        BCS -.CombinedStorageChannel.-> Storage
        ELS -.ExecutionClientEventsChannel.-> BCS
        PCS -.Eth1EventsChannel.-> BCS
    end

    subgraph "Infrastructure Layer"
        Infra[infrastructure/<br/>async, events, metrics, crypto, BLS, KZG, SSZ]
        Spec --> Infra
        ST --> Infra
        Storage --> Infra
        Net --> Infra
    end

    style CLI fill:#e1f5ff
    style Node fill:#e1f5ff
    style BCS fill:#ffe1e1
    style ELS fill:#ffe1e1
    style VCS fill:#ffe1e1
    style Spec fill:#fff4e1
    style DB fill:#e1ffe1
    style Net fill:#f0e1ff
    style RestAPI fill:#ffe1f5
    style EL fill:#f5f5f5
    style Infra fill:#e8e8e8
```

### Module Dependency Layers

```
┌─────────────────────────────────────────────────────────────┐
│  teku/                    (Application Entry Point)         │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────┴────────────────────────────────┐
│  services/         (Service Orchestration Layer)            │
│  ├─ beaconchain, chainstorage, executionlayer               │
│  ├─ powchain, timer, bootnode, zkchain                      │
└────────────┬──────────────────────────┬─────────────────────┘
             │                          │
┌────────────┴──────────┐  ┌───────────┴──────────────────────┐
│  validator/           │  │  beacon/                          │
│  ├─ client            │  │  ├─ pow, sync, validator          │
│  ├─ api               │  │                                   │
│  └─ beaconnode        │  └───────────────────────────────────┘
└───────────┬───────────┘
            │
┌───────────┴─────────────────────────────────────────────────┐
│  ethereum/              (Core Consensus Layer)              │
│  ├─ spec               (Consensus specification)            │
│  ├─ statetransition    (Block/slot processing)              │
│  ├─ executionlayer     (EL integration)                     │
│  ├─ dataproviders      (Data queries)                       │
│  ├─ networks           (Network configs)                    │
│  └─ weaksubjectivity   (Checkpoint validation)              │
└─────────┬──────────────────────────┬────────────────────────┘
          │                          │
┌─────────┴──────────┐  ┌───────────┴──────────────────┐
│  storage/          │  │  networking/                  │
│  ├─ api            │  │  ├─ p2p (Libp2p)             │
│  ├─ protoarray     │  │  ├─ eth2 (Protocol)          │
│  └─ archive        │  │  └─ nat                       │
└─────────┬──────────┘  └──────────┬───────────────────┘
          │                        │
┌─────────┴────────────────────────┴───────────────────────────┐
│  data/                  (REST API & Serialization)           │
│  ├─ beaconrestapi      (Beacon Chain REST API)               │
│  ├─ provider           (Data providers)                      │
│  ├─ serializer         (SSZ/JSON serialization)              │
│  └─ publisher          (Metrics publishing)                  │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────┴───────────────────────────────────┐
│  infrastructure/           (Foundation Layer)                │
│  ├─ async, events, metrics, logging                          │
│  ├─ bls, kzg, crypto                                         │
│  ├─ ssz (serialization)                                      │
│  ├─ bytes, collections, unsigned                             │
│  └─ http, restapi, serviceutils, time, yaml                 │
└──────────────────────────────────────────────────────────────┘
```

### Application Modes

Teku supports three operational modes controlled by `NodeMode`:
1. **COMBINED**: Full beacon node + validator client (default)
2. **VC_ONLY**: Validator client only (connects to remote beacon node)
3. **BOOTNODE_ONLY**: Network bootstrap node only

Entry point: `teku/src/main/java/tech/pegasys/teku/Teku.java`
CLI parsing: `teku/src/main/java/tech/pegasys/teku/cli/BeaconNodeCommand.java`

### Service-Based Architecture

Teku uses a composable service architecture where all major components are `Service` implementations managed by `ServiceController`. Services start in dependency order and stop in reverse order:

```
ServiceController
├─ StorageService (database and state storage)
├─ ExecutionLayerService (Execution Layer integration)
├─ BeaconChainService (consensus layer processing)
├─ PowchainService (PoW chain monitoring for deposits)
├─ ValidatorClientService (validator duties)
└─ TimerService (slot timing)
```

### Key Modules

**ethereum/spec** - Core consensus specification implementation
- Organized by milestones: PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB, FULU, GLOAS
- `Spec.java` provides unified API across all forks
- `SpecVersion.java` encapsulates version-specific operations
- `ForkSchedule.java` determines which fork rules apply at each epoch

**beacon/** - Beacon chain functionality
- `beacon:pow` - Proof-of-Work chain interaction
- `beacon:sync` - Block synchronization logic
- `beacon:validator` - Validator-specific beacon operations

**ethereum/statetransition** - State transition logic
- Processes blocks, slots, attestations according to consensus rules
- Validates and applies state changes

**ethereum/executionlayer** - Execution Layer integration
- `ExecutionLayerManager` orchestrates EL interactions
- `ExecutionEngineClient` implements Engine API (newPayload, forkchoiceUpdated)
- `BuilderClient` integrates with MEV-Boost builders
- Supports Web3j and REST transport layers

**storage/** - Database and state management
- Multiple storage modes: ARCHIVE, PRUNE_ANCIENT_STATES, PRUNE_HOT_STATES
- Supports RocksDB and LevelDB backends
- `storage:protoarray` implements fork choice algorithm
- `storage:archive` handles historical data (blobs, etc.)

**data/beaconrestapi** - REST API implementation
- Implements standard Beacon Chain API endpoints
- Separate validator API endpoints

**networking/** - Peer-to-peer networking
- `networking:p2p` - Libp2p integration for gossip and discovery
- `networking:eth2` - Ethereum 2.0 protocol-specific networking
- Handles peer management, attestation/block propagation

**validator/** - Validator client
- `validator:client` - Validator duties, slashing protection
- `validator:api` - REST API for external validator clients
- `validator:beaconnode` - Beacon node client interface

**infrastructure/** - Shared utilities
- async, events, metrics, logging, crypto, BLS, KZG
- SSZ serialization (`infrastructure:ssz`)
- Event channels for inter-service communication

### Event-Driven Communication

The system uses an event-driven architecture via `EventChannels`:

- **SlotEventsChannel** - Fires every slot (12 seconds), drives consensus processing
- **VoteUpdateChannel** - Fork choice updates
- **CombinedStorageChannel** - Storage state updates
- **ExecutionClientEventsChannel** - Execution Layer events
- **Eth1EventsChannel** - PoW chain events (deposits)

Services subscribe to relevant channels and react asynchronously using `SafeFuture` for non-blocking operations.

### State Transition Flow

1. **SlotEventsChannel.onSlot()** fires every 12 seconds
2. **BeaconChainService** processes the slot:
    - Performs per-slot state transition
    - Processes pending attestations
    - Triggers block proposal if validator's duty
3. **ExecutionLayerManager** queried for execution payload (if proposing)
4. **Fork choice** updated via VoteUpdateChannel → BatchingVoteUpdateChannel
5. **Network layer** gossips blocks/attestations to peers
6. **Storage** persists finalized states and blocks

### Consensus Specification Milestones

The codebase supports multiple Ethereum consensus forks. Each milestone has:
- Fork-specific `SpecConfig` (e.g., `AltairSpecConfig`, `CapellaSpecConfig`)
- Milestone-specific data structures (BeaconState, BeaconBlock variants)
- State transition logic adapted for the fork

`ForkSchedule` determines which milestone is active at each epoch, and `Spec` provides version-appropriate implementations.

### Execution Layer Integration

Teku integrates with Execution Layer clients (Geth, Nethermind, Besu, etc.) via:
- **Engine API**: newPayload, forkchoiceUpdated, getPayload
- **Builder API**: MEV-Boost integration for block building
- **Web3 API**: eth_* methods for legacy PoW monitoring

The `ExecutionLayerManager` abstracts these interactions and handles fallback/retry logic.

### Testing Strategy

- **Unit tests**: Test individual components in isolation (run with `./gradlew test`)
- **Integration tests**: Test component interactions (run with `./gradlew integrationTest`)
- **Reference tests**: Official Ethereum consensus spec tests (run with `./gradlew referenceTest`)
- **Acceptance tests**: End-to-end scenarios with real network simulation (run with `./gradlew acceptanceTest`)
- **Property tests**: Randomized property-based tests (run with `./gradlew propertyTest`)

Each module has its own test suite under `src/test/java` and test fixtures under `src/testFixtures/java`.

## Code Conventions

- **Coding style**: Google Java conventions (enforced by Spotless)
- **Imports**: Always use proper import statements instead of fully qualified class names in code
  - Good: `import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationHelper; ... DataColumnSidecarValidationHelper helper = ...`
  - Bad: `tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationHelper helper = ...`
- **Async operations**: Use `SafeFuture` and `AsyncRunner` instead of raw CompletableFuture. Never use `.join()` or `.get()` to block on futures — these block threads and defeat the purpose of async programming. Instead, compose futures using `thenApply`, `thenCompose`, `thenCombine`, `thenComposeCombined`, etc.
- **Immutability**: Prefer immutable data structures (record types, SszData implementations)
- **Error handling**: Use checked exceptions for recoverable errors, unchecked for programming errors
- **Testing**: All code must have automated test coverage (no manual tests)
- **Commit messages**: Imperative mood, present tense ("Add feature" not "Added feature")

See [Hyperledger Besu Coding Conventions](https://wiki.hyperledger.org/display/BESU/Coding+Conventions) for additional guidelines.

## Fork-Aware Development Patterns

When implementing features that vary across Ethereum consensus forks:

1. **Add convenience methods to `Spec.java`** for fork-specific helpers to avoid repetitive `Optional.orElseThrow()` boilerplate. See: `getExecutionRequestsProcessor()`, `getDataColumnSidecarUtil()`

2. **Prefer `@TestSpecContext` for fork-parametrized tests.** Annotate the class with `@TestSpecContext(milestone = {...})`, use `@TestTemplate` on methods, and inject `SpecContext` in `@BeforeEach` to access fork-specific `Spec` and `DataStructureUtil`. See: `AttestationProductionDutyTest`, `AggregationDutyTest`, `PayloadAttestationProductionDutyTest`. Fall back to **abstract test base classes** (e.g. `AbstractDataColumnSidecarGossipValidatorTest`, `AbstractAttestationValidatorTest`) when fork behavior is too divergent to parametrize within a single class — each fork gets its own subclass implementing `createSpec()`. Use **separate test files with fully independent setup** only as a last resort.

3. **Pass `SpecLogic` to validation helpers** instead of fork-specific implementations. Let each helper extract what it needs internally (e.g., casting to `MiscHelpersFulu`). This keeps generic validators fork-agnostic. See: `DataColumnSidecarUtil` hierarchy

4. **Create validation utility hierarchies** for fork-specific validation logic:
   - Abstract base class: `ethereum/spec/src/main/java/tech/pegasys/teku/spec/logic/common/util/`
   - Fork implementations: `ethereum/spec/src/main/java/tech/pegasys/teku/spec/logic/versions/{fork}/util/`
   - Integrate via SpecLogic: Add getter method to SpecLogic interface and implement in fork-specific SpecLogic classes
   - Add convenience method to Spec.java that handles the Optional unwrapping
   - Abstract test base: `ethereum/statetransition/src/test/.../Abstract{Feature}Test.java`

   Example pattern (DataColumnSidecarUtil):
   - Base: `DataColumnSidecarUtil` (abstract class with common methods)
   - Fulu: `DataColumnSidecarUtilFulu` (includes header validation, inclusion proofs)
   - Gloas: `DataColumnSidecarUtilGloas` (simplified, no headers, validates execution payload bids)
   - Integration: `SpecLogic.getDataColumnSidecarUtil()` → `Spec.getDataColumnSidecarUtil(slot)`

## Important Files

- `settings.gradle` - Defines all 75+ submodules
- `build.gradle` - Root build configuration, dependency management
- `CONTRIBUTING.md` - Contribution guidelines and CLA process
- `ethereum/spec/src/main/java/tech/pegasys/teku/spec/Spec.java` - Main consensus spec API
- `teku/src/main/java/tech/pegasys/teku/Teku.java` - Application entry point
- `services/beaconchain/src/main/java/tech/pegasys/teku/services/beaconchain/BeaconChainService.java` - Core beacon chain service

## Module Dependencies

The codebase enforces layered dependencies:
1. **infrastructure/** - Lowest level, no dependencies on other Teku modules
2. **ethereum/spec** - Depends only on infrastructure
3. **ethereum/*** - Can depend on infrastructure and other ethereum modules
4. **services/** - Composes ethereum, storage, networking modules
5. **teku/** - Top-level orchestration, depends on services

Circular dependencies are prevented by build-time dependency checks (DepCheckPlugin).

## Development Workflow

1. Make changes to relevant module(s)
2. Run `./gradlew spotlessApply` to format code
3. Run `./gradlew test` to run unit tests
4. Run module-specific tests: `./gradlew :<module>:test`
5. Run `./gradlew build` for full verification before committing

## Agent Behaviour Rules

- **No git actions without explicit instruction.** Never run `git commit`, `git commit --amend`, or `git push` unless the user explicitly asks. Stage changes and stop — let the user decide when to commit.
- **No co-author lines.** Do not add `Co-Authored-By:` trailers to commit messages.

## Running Teku Locally

After building with `./gradlew installDist`, run:
```bash
./build/install/teku/bin/teku --help
```
