# teku

 [![Build Status](https://github.com/Consensys-Incorporated/teku/actions/workflows/ci.yml/badge.svg)](https://github.com/Consensys-Incorporated/teku/actions/workflows/ci.yml?query=branch%3Amaster)
 [![GitHub License](https://img.shields.io/github/license/Consensys/teku.svg?logo=apache)](https://github.com/Consensys-Incorporated/teku/blob/master/LICENSE)
 [![Documentation](https://img.shields.io/badge/docs-readme-blue?logo=readme&logoColor=white)](https://docs.teku.consensys.io/)
 [![consensus-specs](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2FConsensys%2Fteku%2Frefs%2Fheads%2Fmaster%2Fbuild.gradle&search=refTestVersion.*%22(v%5B%5E%22%5D%2B)%22&replace=%241&label=consensus-specs)](https://github.com/ethereum/consensus-specs/releases)
 [![Discord](https://img.shields.io/badge/Chat-on%20Discord-%235865F2?logo=discord&logoColor=white)](https://discord.gg/teku)
 [![Twitter Follow](https://img.shields.io/twitter/follow/Teku_Consensys)](https://twitter.com/Teku_Consensys)

Teku is an open-source Ethereum consensus client written in Java and containing a full beacon node and validator client implementation.

See the [Changelog](https://github.com/Consensys-Incorporated/teku/releases) for details of the latest releases and upcoming breaking changes.

## Useful links

* [Ethereum Beacon Chain specification](https://github.com/ethereum/consensus-specs/blob/master/specs/phase0/beacon-chain.md)
* [Teku user documentation](https://docs.teku.consensys.io/)
* [Teku REST API reference documentation](https://docs.teku.consensys.io/reference/rest)
* [Teku issues](https://github.com/Consensys-Incorporated/teku/issues)
* [Contribution guidelines](CONTRIBUTING.md)
* [Teku Changelog](https://github.com/Consensys-Incorporated/teku/releases)

## Teku users

See our [user documentation](https://docs.teku.consensys.io/).

Raise a [documentation issue](https://github.com/Consensys/doc.teku/issues) or get in touch in
the #teku channel on [Discord](https://discord.gg/teku) if you've got questions or feedback.

## Teku developers

* [Contribution Guidelines](CONTRIBUTING.md)
* [Coding Conventions](https://wiki.hyperledger.org/display/BESU/Coding+Conventions)

## Binary Releases

Binary releases are available from the [releases page](https://github.com/Consensys-Incorporated/teku/releases).
Binary builds that track the latest changes on the master branch are available on
[Dockerhub](https://hub.docker.com/r/consensys/teku) using the `develop` version or as binary
downloads ([tar.gz format](https://artifacts.consensys.net/public/teku/raw/names/teku.tar.gz/versions/develop/teku-develop.tar.gz)
or [zip format](https://artifacts.consensys.net/public/teku/raw/names/teku.zip/versions/develop/teku-develop.zip)).

We recommend only using release versions for Mainnet, but `develop` builds are useful for testing
the latest changes on testnets.

Release notifications are available via:
* Sign up to our [release announcements](https://pages.consensys.net/teku-sign-up) email list (release and important announcements only, no marketing)
* Follow us on [Twitter](https://twitter.com/Teku_Consensys)
* `teku` in [Consensys Discord](https://discord.gg/teku),
* Subscribe to release notifications on github for [teku](https://github.com/Consensys-Incorporated/teku)

## Build Instructions

### Install Prerequisites

* Java 25+

Note: Official builds of Teku are performed with Java 25.
Building on a more recent version of Java is supported, but the resulting build will not work on earlier versions of Java.


### Build and Dist

To create a ready to run distribution:

```shell script
git clone https://github.com/Consensys-Incorporated/teku.git
cd teku && ./gradlew distTar installDist
```

This produces:
- Fully packaged distribution in `build/distributions` 
- Expanded distribution, ready to run in `build/install/teku`

### Build and Test

To build, clone this repo and run with `gradle`:

```shell script
git clone https://github.com/Consensys-Incorporated/teku.git
cd teku && ./gradlew

```

After a successful build, distribution packages are available in `build/distributions`.

### Other Useful Gradle Targets

| Target      | Builds                                                  |
|-------------|---------------------------------------------------------|
| distTar     | Full distribution in build/distributions (as `.tar.gz`) |
| distZip     | Full distribution in build/distributions (as `.zip`)    |
| installDist | Expanded distribution in `build/install/teku`           |
| distDocker  | The `consensys/teku` docker image                       |

## Code Style

We use Google's Java coding conventions for the project. To reformat code, run:

```shell script
./gradlew spotlessApply
```

Code style is checked automatically during a build.

## Testing

All the unit tests are run as part of the build, but can be explicitly triggered with:

```shell script
./gradlew test
```

## Special thanks

**YourKit**

![YourKit Logo](https://www.yourkit.com/images/yklogo.png)

For providing us free open source licenses for their profiler.

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

**OrbStack**

![OrbStack Logo](https://orbstack.dev/_next/image?url=%2Fimg%2Ficon128.png&w=64&q=75)

For providing us free open source licenses for their application.

OrbStack delivers a fast, light, and easy way to run Docker containers and Linux. Check it out on https://orbstack.dev/.
