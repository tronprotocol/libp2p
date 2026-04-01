# libp2p
libp2p is a p2p network SDK implemented in java language. The functional modules that have been implemented so far include
* node discovery by p2p, include ipv4 and ipv6,
* tcp connection management,
* publish nodes on dns domain and node discovery by dns,
* support compressed message transmission among nodes

# Note
Starting from version 2.2.7, `libp2p` has removed the logback component and adopted the logger facade. If logging is required, you need to introduce a logging framework manually.  
Here’s how to include logback in your project using Gradle:

1. Uncomment the ch.qos.logback:logback-classic dependency in build.gradle file
  ```
  dependencies {
      implementation("ch.qos.logback:logback-classic:1.2.13") {
         ...
      }
  }
  ```
2.  Refresh the dependency verification metadata to prevent verification failures
```bash
$ ./gradlew clean --refresh-dependencies --write-verification-metadata sha256
```
3. Rename or copy logback.xml.example in src/main/resources to logback.xml.


# Build
Building libp2p requires `git`, `JDK 17` or `Oracle JDK 1.8` to be installed, other JDK versions are not supported yet. Make sure you operate on `Linux` and `MacOS` operating systems.

Clone the repo and switch to the `main` branch

  ```bash
  $ git clone https://github.com/tronprotocol/libp2p.git
  $ cd libp2p
  $ git checkout -t origin/main
  ```
Then, run the following command to build libp2p, the `libp2p.jar` file can be found in `libp2p/build/libs/` after being built successfully.
```bash
$ ./gradlew clean build -x test
```

# Usage
libp2p can run independently or be imported into other projects.

## Run independently
Running libp2p requires JDK 17 or Oracle JDK 1.8 to be installed, other JDK versions are not supported yet. Make sure you operate on Linux and MacOS operating systems.
then run the following command to start the node:
```bash
$ nohup java -jar libp2p.jar [options] >> start.log 2>&1 &
```
See the manual for details on [options](src/main/java/org/tron/p2p/example/README.md)

## How to include the dependency
### Gradle Setting
Add repo setting:
```bash
repositories {
    ...
    mavenCentral()
}
```
Then add the required packages as dependencies. Please add dependencies locally.
```bash
dependencies {
    implementation group: 'io.github.tronprotocol', name: 'libp2p', version: '2.2.7'
}
```
Or if you are using the jar files as your dependencies:
```bash
dependencies {
    implementation fileTree(dir:'libs', include: 'libp2p.jar')
}
```

### Maven Setting
```bash
<repositories>
    <repository>
        <id>central-repos</id>
        <name>Central Repository</name>
        <url>https://repo.maven.apache.org/maven2</url>
    </repository>
</repositories>

<dependency>
    <groupId>io.github.tronprotocol</groupId>
    <artifactId>libp2p</artifactId>
    <version>2.2.7</version>
</dependency>
```

## Example
For some examples please check our [example package](src/main/java/org/tron/p2p/example).

# Integrity Check
* After February 21, 2023， releases are signed the gpg key:
  ```
  pub: 1254 F859 D2B1 BD9F 66E7 107D F859 BCB4 4A28 290B
  uid: build@tron.network
  ```
