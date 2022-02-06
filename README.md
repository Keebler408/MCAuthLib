# MCAuthLib
MCAuthLib is a library for authentication with Minecraft accounts. It is used in projects such as MCProtocolLib to handle authenticating users.

## Example Code
See [example/com/github/steveice10/mc/auth/test/MinecraftAuthTest.java](https://github.com/Steveice10/MCAuthLib/blob/master/example/com/github/steveice10/mc/auth/test/MinecraftAuthTest.java)

## Authentication Types

Visit [wiki.vg](https://wiki.vg/) for documentation on [Microsoft's API authentication](https://wiki.vg/Microsoft_Authentication_Scheme).

The `MsaAuthenticationService` class supports the following Microsoft account authentication types:
- **Device Code**. Uses the [Microsoft Authentication Library (MSAL) for Java](https://github.com/AzureAD/microsoft-authentication-library-for-java).
- **Username/password**. Uses a custom implementation that combines Microsoft and Xbox API's.

## Building the Source
MCAuthLib uses Maven to manage dependencies. Simply run 'mvn clean install' in the source's directory.

## Support and development

Please join us at https://discord.gg/geysermc under #mcprotocollib for discussion and support for this project.

## License
MCAuthLib is licensed under the **[MIT license](http://www.opensource.org/licenses/mit-license.html)**.

