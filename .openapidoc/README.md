# Teku OpenAPI Spec Publish

This directory contains NodeJS project which publishes Teku OpenAPI specifications to
[`gh-pages`](https://github.com/Consensys-Incorporated/teku/tree/gh-pages) branch via CI job after tests are green.
See `publishOpenApiSpec` job in `.circleci/config.yml`.

The actual up-to-date generated doc is available at https://docs.teku.consensys.io/reference/rest

## Procedure

The script performs following tasks:

* Extract spec from Teku latest docker image by reaching the spec endpoint
* Create `dist` directory (this folder will be pushed to `gh-pages` branch)
* Copy the specs to `dist` as `teku-latest.json` (and latest.json for retro-compatibility on the name).
* Update the spec version file depending on tag existence for the build

For release version (when tagged with CalVer version), it performs following additional steps:

* Copy the spec to `dist` as `teku-<version>.json`
* Fetch `https://github.com/Consensys-Incorporated/teku/raw/gh-pages/versions.json`
* Update versions' json with release versions by updating `stable.spec` and `stable.source` to the release version and adding a new entry
for it. For example after adding spec version `20.11.0`, the `versions.json` would look like:

~~~
{
 "latest": {
  "spec": "latest",
  "source": "master"
 },
 "stable": {
  "spec": "20.11.1",
  "source": "20.11.1"
 },
 "20.11.0": {
  "spec": "20.11.0",
  "source": "20.11.0"
 }
 "20.11.1": {
  "spec": "20.11.1",
  "source": "20.11.1"
 }
}
~~~

* Save updated `versions.json` to `dist` folder.
* Push the `dist` folder to `gh-pages` branch. The script is using [gh-pages](https://www.npmjs.com/package/gh-pages)
npm module to automate this step.

## Environment variables

Following environment variables can be used to override defaults

* `OA_GIT_URL`            (default: `git@github.com:Consensys/teku.git`)
* `OA_GH_PAGES_BRANCH`    (default: `gh-pages`)
* `OA_GIT_USERNAME`       (default: `CircleCI Build`)
* `OA_GIT_EMAIL`          (default: `ci-build@consensys.net`)

Following should only be overridden if changing the project

* `OA_VERSIONS_FILE_NAME` (default: `versions.json`)
* `OA_DIST_DIR`           (default: `./dist`)
* `OA_SPEC_URL`          (default: `http://localhost:5051/swagger-docs`)
* `OA_SPEC_DIR`          (default: `./openapidoc/spec`)
