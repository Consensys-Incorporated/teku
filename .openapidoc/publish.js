import fs from "fs";
import path from "path";
import fetch from "node-fetch";
import config from "./config.js";

const log = (...args) => console.log(...args); // eslint-disable-line no-console

async function main() {
  try {
    const cfg = config.getConfig();
    const {distDir, specs, versions} = cfg;

    prepareDistDir(distDir);

    copyIndexHtml(distDir);

    specs.forEach(function (spec) {
      copySpecFileToDist(spec);
    });

    if (specs[0].isReleaseVersion) {
      const versionsJson = await fetchVersions(versions.url);
      const updatedVersionsJson = updateVersions(
          versionsJson,
          specs[0].version
      );
      saveVersionsJson(updatedVersionsJson, versions.dist);
    }

    log("Prepared following files for publishing: ");
    fs.readdirSync(distDir).forEach((file) => {
      log(file);
    });
  } catch (err) {
    log(`ERROR: OpenAPI spec failed to prepare: ${err.message}`);
    log(config);
    process.exit(1);
  }
}

function prepareDistDir(dirPath) {
  if (fs.existsSync(dirPath)) {
    fs.rmdirSync(dirPath, {recursive: true});
  }
  fs.mkdirSync(dirPath, {recursive: true});
}

function copyIndexHtml(distDir) {
  fs.copyFileSync(new URL("./index.html", import.meta.url).pathname, path.join(distDir, "index.html"));
}

function copySpecFileToDist(spec) {
  fs.copyFileSync(spec.path, spec.latestDist);
  fs.copyFileSync(spec.path, spec.latestDistCompat);
  if (spec.isReleaseVersion) {
    fs.copyFileSync(spec.path, spec.releaseDist);
  }
}

async function fetchVersions(versionsUrl) {
  const response = await fetch(versionsUrl);
  if (response.ok) {
    const versionsJson = await response.json();
    return versionsJson;
  }

  throw new Error(
      `${versionsUrl} fetch failed with status: ${response.statusText}`
  );
}

function updateVersions(versionsJson, specVersion) {
  versionsJson[specVersion] = {
    spec: specVersion,
    source: specVersion,
  };
  versionsJson["stable"] = {
    spec: specVersion,
    source: specVersion,
  };
  return versionsJson;
}

function saveVersionsJson(versionsJson, versionsDist) {
  fs.writeFileSync(versionsDist, JSON.stringify(versionsJson, null, 1));
}

main();
