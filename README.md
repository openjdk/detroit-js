# javax.script Engine for JavaScript Based on V8

This is a [V8](https://v8.dev/)-based [javax.script](https://docs.oracle.com/en/java/javase/25/docs/api/java.scripting/javax/script/package-summary.html) engine for Java.

## Prerequisites to build

* `git`
* `make`
* **JDK 25** (set `JAVA_HOME` env. var to that JDK)
* Typical build utilities: `bash`, `tar`, `zip`, `curl` (often already present)
* **Python 3** (required by V8 tooling)
* C/C++ toolchain for your OS
  * **Linux**: `gcc/g++` or `clang`, plus common build packages (varies by distro)
  * **macOS**: Xcode Command Line Tools (`xcode-select --install`)

## Quick Checks

```bash
git --version
make --version
python3 --version
${JAVA_HOME}/bin/java -version
```

## Build and Test

* Clone the depot_tools into a directory outside this repo. This provides key tools used to fetch and build V8 (gclient, fetch, gn, ninja...).

  ```sh
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools
  ```

* Set the `depot_tools` repo directory in `PATH`

  ```sh
  export PATH="<your-depot-tools-dir>:$PATH"
  ```

* To fetch/sync the V8 sources, specifically version 14.4.221 and dependencies:

  ```sh
  make get-v8
  ```

* To do a clean build:

  ```sh
  make clean all
  ```

* (Optional) To run [jtreg](https://openjdk.org/jtreg/) tests:

  ```sh
  export JT_HOME=<your_jtreg_home_dir>
  make test
  ```

* To run the interactive shell:

  ```sh
  bash jjs.sh
  v8> java.lang.System.out.println("hello world")
  hello world
  v8> java.lang.System.exit(0)
  ```

* There are samples in the `samples` directory. To run a sample script:

  ```sh
  bash jjs.sh samples/helloworld.js
  ```

* There are also Java samples. To run a Java sample:

  ```sh
  bash run-java-sample.sh samples/HelloWorld.java
  ```

* To create a distribution bundle:

  ```sh
  make dist bundles
  ```

  The resulting .tar.gz bundle is written to `build/<platform-specific-dir>/bundles/`.

## Enabling V8 flags via environment variable

**JVMV8_FLAGS** environment variable can be set with
[V8 flags](https://chromium.googlesource.com/v8/v8/+/master/src/flags/flag-definitions.h)

### To enable dumping heap snapshot on out of memory crash

```sh
export JVMV8_FLAGS=--heap-snapshot-on-oom
```

#### To enable tracing/logging,

```sh
export JVMV8_FLAGS=--trace
```

```sh
export JVMV8_FLAGS=--trace-exception
```

```sh
export JVMV8_FLAGS=--log-all
```
