import fs from "fs";
import path from "path";
import yaml from "js-yaml";
import GitUrlParse from "git-url-parse";

const distDir = process.env.OA_DIST_DIR || "./dist";
const specDir = process.env.OA_SPEC_DIR || "./spec";
const gitUrl = process.env.OA_GIT_URL || "https://github.com/Consensys/teku.git";
const branch = process.env.OA_GH_PAGES_BRANCH || "gh-pages";
const versionsFileName = process.env.OA_VERSIONS_FILE_NAME || "versions.json";

export default  {
  getConfig,
};

function getConfig() {
  const repo = GitUrlParse(gitUrl);
  const specs = calculateSpecs();
  if (specs.length === 0) {
    throw new Error("Unable to parse specs in dist" + distDir);
  }

  return {
    specs: specs,
    distDir: distDir,
    versions: calculateVersionDetails(repo, branch),
  };
}

function calculateSpecs() {
  const extension = ".json";
  const specFiles = fs.readdirSync(specDir);
  var specArr = [];
  specFiles.forEach((file) => {
    if (path.extname(file).toLowerCase() === extension) {
      specArr.push(calculateSpecDetails(path.join(specDir, file)));
    }
  });

  return specArr;
}

function calculateSpecDetails(specFile) {
  const specVersion = calculateSpecVersion(specFile);
  const release = isReleaseVersion(specVersion);
  const latestDist = destinationPath(true, specFile, "latest");
  const latestDistCompat = destinationPath(false, specFile, "latest");
  const releaseDist = destinationPath(true, specFile, specVersion);

  return {
    path: specFile,
    version: specVersion,
    isReleaseVersion: release,
    latestDist: latestDist,
    latestDistCompat: latestDistCompat,
    releaseDist: releaseDist,
  };
}

function calculateSpecVersion(specFile) {
  return yaml.load(fs.readFileSync(specFile, "utf8")).info.version;
}

function isReleaseVersion(specVersion) {
  // our main project's gradle's build calculateVersion adds "+<new commits since stable>-<hash>"
  // after the version for dev builds
  return !specVersion.includes("+");
}

function destinationPath(usePrefix, specFile, suffix) {
  const prefix = usePrefix ? `${path.parse(specFile).name}-` : '';
  const extension = path.extname(specFile);
  return path.join(distDir, `${prefix}${suffix}${extension}`);
}

function calculateVersionDetails(repo, branch) {
  const versionsFileUrl = `https://${repo.source}/${repo.owner}/${repo.name}/raw/${branch}/${versionsFileName}`;
  const versionsFileDist = path.join(distDir, versionsFileName);
  return {
    url: versionsFileUrl,
    dist: versionsFileDist,
  };
}
