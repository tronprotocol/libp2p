# j-libp2p
j-libp2p is a p2p network SDK implemented in java language. The functional modules that have been implemented so far include node discovery, connection management, and so on. Subsequent versions will implement more functions.

# Build
Building j-libp2p requires `git` and `Oracle JDK 1.8` to be installed, other JDK versions are not supported yet. Make sure you operate on `Linux` and `MacOS` operating systems.

Clone the repo and switch to the `main` branch

  ```bash
  $ git clone https://github.com/tronprotocol/libp2p.git
  $ cd libp2p
  $ git checkout -t origin/main
  ```
then run the following command to build libp2p, the `java-p2p.jar` file can be found in `libp2p/build/libs/` after build successful.
```bash
$ ./gradlew clean build -x test
```

# Usage
j-libp2p can run independently or be imported into other projects.

## Run independently
Running java-tron requires Oracle JDK 1.8 to be installed, other JDK versions are not supported yet. Make sure you operate on Linux and MacOS operating systems.
then run the following command to start the node:
```bash
$ nohup java -jar java-p2p.jar >> start.log 2>&1 &
```

## How to include the dependency
### Gradle Setting
Add repo setting:
```bash
repositories {
    ...
    maven { url 'https://jitpack.io' }
}
```
Then add required packages as dependencies. Please add dependencies locally.
```bash
dependencies {
	implementation 'com.github.tronprotocol:libp2p:-SNAPSHOT'
}
```
Or if you are using the jar files as your dependencies:
```bash
dependencies {
    implementation fileTree(dir:'your path', include: '*.jar')
}
```

### Maven Setting
```bash
<repositories>
	<repository>
		<id>jitpack.io</id>
		<url>https://jitpack.io</url>
	</repository>
</repositories>

<dependency>
	<groupId>com.github.tronprotocol</groupId>
	<artifactId>libp2p</artifactId>
	<version>-SNAPSHOT</version>
</dependency>
```

## Example
For some examples please check our [example package](https://github.com/tronprotocol/libp2p/tree/main/src/main/java/org/tron/p2p/example). 