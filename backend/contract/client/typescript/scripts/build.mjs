import { execFileSync } from "node:child_process";
import {
    copyFileSync,
    mkdirSync,
    readFileSync,
    rmSync,
    writeFileSync,
} from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const directory = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const buildDirectory = join(directory, "build");
const output = join(buildDirectory, "dist");
if (relative(buildDirectory, output) !== "dist") {
    throw new Error(
        "The compiled client must remain inside its build directory.",
    );
}
rmSync(output, { recursive: true, force: true });
execFileSync(
    process.execPath,
    [
        join(directory, "node_modules/typescript/bin/tsc"),
        "--project",
        "tsconfig.json",
    ],
    {
        cwd: directory,
        stdio: "inherit",
    },
);
const source = JSON.parse(
    readFileSync(join(directory, "package.json"), "utf8"),
);
const { scripts, devDependencies, ...metadata } = source;
metadata.version = process.env.AIGRAM_CLIENT_VERSION || metadata.version;
mkdirSync(output, { recursive: true });
writeFileSync(
    join(output, "package.json"),
    JSON.stringify(metadata, null, 4) + "\n",
);
copyFileSync(
    join(directory, "templates/NOTICE.md"),
    join(output, "THIRD_PARTY_NOTICES.md"),
);
copyFileSync(
    join(directory, "templates/LICENSE.txt"),
    join(output, "OPENAPI-GENERATOR-LICENSE.txt"),
);
