# Contributing to Teku
:+1::tada: First off, thanks for taking the time to contribute! :tada::+1:

Welcome to the Teku repository!  The following is a set of guidelines for contributing to this repo and its packages. These are mostly guidelines, not rules. Use your best judgment, and feel free to propose changes to this document in a pull request.

#### Table Of Contents

[Code of Conduct](#code-of-conduct)

[I just have a quick question](#i-just-have-a-quick-question)

[How to contribute](#how-to-contribute)
  * [Your first code contribution](#your-first-code-contribution)
  * [Reporting bugs](#reporting-bugs)

[Style Guides](#style-guides)
  * [Java Style Guide](#java-code-style-guide)
  * [Coding Conventions](#coding-conventions)
  * [Git Commit Messages & Pull Request Messages](#git-commit-messages--pull-request-messages)

## Code of Conduct

This project and everyone participating in it is governed by the [Teku Code of Conduct](CODE-OF-CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to [private-quorum@consensys.net].

## I just have a quick question

You'll find us on [Discord] and that's the fastest way to get an answer.

## How To Contribute

### Your first code contribution
Start by looking through the 'good first issue' and 'help wanted' issues:
* [Good First Issue][search-label-good-first-issue] - issues which should only require a few lines of code, and a test or two.
* [Help wanted issues][search-label-help-wanted] - issues that are a bit more involved than `good first issue` issues.

Please reach out in discord if you're looking to help out, and we can assist you in finding a good candidate ticket to work on, or discuss the idea you have. 

We have a [Teku](https://discord.gg/teku) channel, and also a [Teku Contributors](https://discord.gg/teku) channel.

#### Scope of accepted pull requests

To keep review effort sustainable, **we only accept pull requests that are tied to an issue that is either labelled `good first issue`/`help wanted`, or has been explicitly assigned to you by a maintainer.**

If you have an idea for a fix or improvement that isn't already covered by one of those issues:
1. Open an issue describing the problem or idea (not the PR itself).
2. Wait for a maintainer to triage it. If we agree it's worth doing, we'll label it appropriately or assign it to you.
3. Only then open the pull request.

PRs that aren't tied to a qualifying issue will be closed with a link to this section, regardless of the quality of the change itself. This isn't a judgment on the contribution; it's about keeping our review queue tied to work we've already agreed is worth doing.

#### Airdrop Farming

Due to the prevalence of 'airdrop farming' type practices, this unfortunately puts heightened scrutiny on first-time contributors, but if you're genuinely looking to help out, we'd really love to assist you in any way we can.
Examples of contributions we will generally reject outright, even if opened as an issue first, since they add no value we've identified as needed:
* code replacement of TODOs that are not well tested or justified by performance and regression tests to prove their worth.
* typos, even if valid, will be worked into other PRs or just ignored completely if they're from first-time contributors with no substantive value.
* things like replacing RuntimeException with a new exception type that's not well tested and adding value.
* rewording of comments

Minimal discussion will be given in PR's due to the volume we're needing to deal with currently of this type of PR, which is taking away from actual development time, so please don't be offended if you're genuinely trying to help out; and we say 'see contribution guidelines'.

Please note that in your first contribution, you will be required to sign a Contributor License Agreement (CLA). All you need to do is add a comment to your Pull Request like this:

> I have read the CLA Document and I hereby sign the CLA

### Local Development
The codebase is maintained using the "*contributor workflow*" where everyone without exception contributes patch proposals using "*pull-requests*". This facilitates social contribution, easy testing and peer review.

To contribute a patch, the workflow is as follows:

* Fork repository
* Create topic branch
* Commit patch
* Create pull-request, adhering to the coding conventions herein set forth

In general a commit serves a single purpose and diffs should be easily comprehensible. For this reason do not mix any formatting fixes or code moves with actual code changes.

### Architectural Best Practices

Questions on architectural best practices will be guided by the principles set forth in Effective Java by Joshua Bloch

### Automated Test coverage
All code submissions must be accompanied by appropriate automated tests.  The goal is to provide confidence in the code’s robustness, while avoiding redundant tests.

### Pull Requests

The process described here has several goals:

- Maintain Product quality
- Fix problems that are important to users
- Engage the community in working toward the best possible product
- Enable a sustainable system for maintainers to review contributions
- Further explanation on PR & commit messages can be found in this post: [How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/).

Please follow these steps to have your contribution considered by the approvers:

1. Complete the CLA, as described in [CLA.md]
2. Follow all instructions in [PULL-REQUEST-TEMPLATE.md](.github/pull_request_template.md)
3. Include appropriate test coverage.  Testing is 100% automated.  There is no such thing as a manual test.
4. Follow the [Style Guides](#style-guides)
5. After you submit your pull request, verify that all [status checks](https://help.github.com/articles/about-status-checks/) are passing <details><summary>What if the status checks are failing?</summary>If a status check is failing, and you believe that the failure is unrelated to your change, please leave a comment on the pull request explaining why you believe the failure is unrelated. A maintainer will re-run the status check for you. If we conclude that the failure was a false positive, then we will open an issue to track that problem with our status check suite.</details>

While the prerequisites above must be satisfied prior to having your pull request reviewed, the reviewer(s) may ask you to complete additional design work, tests, or other changes before your pull request can be ultimately accepted.  Please refer to [Code Reviews].

## Reporting Bugs

Following these guidelines helps maintainers and the community understand your report, reproduce the behavior, and find related reports.

Explain the problem and include additional details to help maintainers reproduce the problem:

* **Use a clear and descriptive title** for the issue to identify the problem.
* **Describe the exact steps which reproduce the problem** in as many details as possible. For example, start by explaining how you started Teku, e.g. which command exactly you used in the terminal, or how you started it otherwise.
* **Provide specific examples to demonstrate the steps**. Include links to files or GitHub projects, or copy/pasteable snippets, which you use in those examples. If you're providing snippets in the issue, use [Markdown code blocks](https://help.github.com/articles/markdown-basics/#multiple-lines).
* **Describe the behavior you observed after following the steps** and point out what exactly is the problem with that behavior.
* **Explain which behavior you expected to see instead and why.**
* **Include screenshots** which show you following the described steps and clearly demonstrate the problem.

Provide more context by answering these questions:

* **Did the problem start happening recently** (e.g. after updating to a new version of the software) or was this always a problem?
* If the problem started happening recently, **can you reproduce the problem in an older version of the software?** What's the most recent version in which the problem doesn't happen?
* **Can you reliably reproduce the issue?** If not, provide details about how often the problem happens and under which conditions it normally happens.

Include details about your configuration and environment:

* **Which version of the software are you using?** You can get the exact version by running `teku -v` in your terminal.
* **What OS & Version are you running?**
  * **For Linux - What kernel are you running?** You can get the exact version by running `uname -a` in your terminal.
* **Are you running in a virtual machine?** If so, which VM software are you using and which operating systems and versions are used for the host and the guest?
* **Are you running in a docker container?** If so, what version of docker?
* **Are you running in a Cloud?** If so, which one, and what type/size of VM is it?
* **What version of Java are you running?** You can get the exact version by looking at the Teku logfile during startup.

# Style Guides

## Java Code Style Guide

We use Google's Java coding conventions for the project. To reformat code, run:

```
./gradlew spotlessApply
```

Code style will be checked automatically during a build.

## Coding Conventions
We have a set of [coding conventions](https://wiki.hyperledger.org/display/BESU/Coding+Conventions) to which we try to adhere.  These are not strictly enforced during the build, but should be adhered to and called out in code reviews.

## Git Commit Messages & Pull Request Messages
* Use the present tense ("Add feature" not "Added feature")
* Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
* Provide a summary on the first line with more details on additional lines as needed
* Reference issues and pull requests liberally

[private-quorum@consensys.net]: mailto:private-quorum@consensys.net
[Discord]: https://discord.gg/teku
[CLA.md]: CLA.md
