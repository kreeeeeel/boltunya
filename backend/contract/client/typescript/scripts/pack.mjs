import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const directory = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const npm = process.env.npm_execpath;
if (!npm) {
    throw new Error("Run this script through npm run pack.");
}
const buildDirectory = join(directory, "build");
const destination = join(buildDirectory, "package");
if (relative(buildDirectory, destination) !== "package") {
    throw new Error(
        "The client archive must remain inside its build directory.",
    );
}
rmSync(destination, { recursive: true, force: true });
mkdirSync(destination, { recursive: true });
const result = JSON.parse(
    execFileSync(
        process.execPath,
        [
            npm,
            "pack",
            "./build/dist",
            "--pack-destination",
            destination,
            "--json",
        ],
        {
            cwd: directory,
            encoding: "utf8",
        },
    ),
);
if (result.length !== 1 || result[0].name !== "@aigram/api-client") {
    throw new Error("npm did not create the expected Aigram SDK tarball.");
}
const metadata = JSON.parse(
    readFileSync(join(directory, "build/dist/package.json"), "utf8"),
);
if (result[0].version !== metadata.version) {
    throw new Error(
        "The packed client version does not match its source package.",
    );
}
writeFileSync(
    join(destination, "manifest.json"),
    JSON.stringify(result[0], null, 4) + "\n",
);
console.log(join(destination, result[0].filename));
