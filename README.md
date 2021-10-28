# MCAuthLib

[![Release](https://jitpack.io/v/tycrek/MCAuthLib.svg?style=flat-square)](https://jitpack.io/#tycrek/MCAuthLib)

MCAuthLib is a library for authentication with Minecraft accounts. It is used in projects such as [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) to handle authenticating users.

## Example code

See [example/com/github/steveice10/mc/auth/test/MinecraftAuthTest.java](https://github.com/tycrek/MCAuthLib/blob/master/example/com/github/steveice10/mc/auth/test/MinecraftAuthTest.java) for example usage.

## Installing as a dependency

The recommended way of installing MCAuthLib is through [JitPack](https://jitpack.io). For more details, [see MCAuthLib on JitPack](https://jitpack.io/#tycrek/MCAuthLib).

### Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```
```groovy
dependencies {
    implementation 'com.github.tycrek:MCAuthLib:__version'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```
```xml
<dependency>
    <groupId>com.github.tycrek</groupId>
    <artifactId>MCAuthLib</artifactId>
    <version>__version__</version>
</dependency>
```

## Building the source

~~MCAuthLib uses Maven to manage dependencies. To build the source code, run `mvn clean install` in the project root directory.~~ Now uses Gradle, will need to update this section.

## Support and development

Please join [the GeyserMC Discord server](https://discord.gg/geysermc) and visit the **#mcprotocollib** channel for discussion and support for this project.

## License

MCAuthLib is licensed under the **[MIT license](http://www.opensource.org/licenses/mit-license.html)**.
