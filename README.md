<div align="center">

MCAuthLib (tycrek edition)
===

[![Image]][Jitpack]

*A Minecraft authentication library for MCProtocolLib*

[Image]: https://jitpack.io/v/tycrek/MCAuthLib.svg?style=flat-square
[Jitpack]: https://jitpack.io/#tycrek/MCAuthLib/
</div>

---

**MCAuthLib** is a library for authentication with Minecraft accounts. It is used in projects such as [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) to handle authenticating users. Further, projects such as [ttRMS](https://ttrms.io/) use MCProtocolLib for interacting with Minecraft servers & clients.

## tycrek edition

I use MCAuthLib (and MCProtocolLib) in one of my other projects, [ttRMS](https://ttrms.io/). I've made some changes to better suit my needs.

The biggest differences between my repo and the [GeyserMC repo](https://github.com/GeyserMC/MCAuthLib) are:

- Project now uses **Gradle** instead of Maven
- Target JDK is now **Java 16**, instead of Java 7
- My repo utilizes **Lombok** to reduce manual boilerplate

Some other changes to improve the library's functionality:

- **Two new `AuthenticationService`'s** (see [Authentication Types](#authentication-types))
  - `AutoAuthenticationService`
  - `MSALAuthenticationService`
- Refresh tokens for `MsaAuthenticationService`
- Finished Device Code Flow implementation for `MsaAuthenticationService`
- Removed password authentication from `MsaAuthenticationService` as it was broken and not secure

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
    implementation 'com.github.tycrek:MCAuthLib:__version__'
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

## Authentication Types

Visit [wiki.vg](https://wiki.vg/) for documentation on [Mojang API authentication](https://wiki.vg/Authentication) and [Microsoft's API authentication](https://wiki.vg/Microsoft_Authentication_Scheme).

| `AuthenticationService` | Usage |
| :---: | --- |
| `MojangAuthenticationService` | Used for authenticating Mojang accounts. Supports regular Mojang accounts (email) and legacy accounts (username). |
| `MsaAuthenticationService` | Used for authenticating Microsoft accounts. This service is a custom implementation using a combination of Microsoft, Mojang, and Xbox API's. |
| `AutoAuthenticationService` | Wrapper service combining `Mojang` and `Msa` services. Automatically determines which service to sign in with. Can be complex to figure out. |
| `MSALAuthenticationService` | Alternative service for authenticating Microsoft accounts. This service uses the [Microsoft Authentication Library (MSAL) for Java](https://github.com/AzureAD/microsoft-authentication-library-for-java) to authenticate. |

## Support and development

Please join [the GeyserMC Discord server](https://discord.gg/geysermc) and visit the **#mcprotocollib** channel for discussion and support for this project.

## License

MCAuthLib is licensed under the **[MIT license](http://www.opensource.org/licenses/mit-license.html)**.
