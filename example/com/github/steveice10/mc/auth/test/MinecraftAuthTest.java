package com.github.steveice10.mc.auth.test;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.MsaAuthenticationService;
import com.github.steveice10.mc.auth.service.ProfileService;
import com.github.steveice10.mc.auth.service.SessionService;

import java.net.Proxy;
import java.util.UUID;

public class MinecraftAuthTest {
    // private static final String USERNAME = "Username";
    // private static final String EMAIL = "Username@mail.com";
    // private static final String PASSWORD = "Password";
    // private static final boolean REQUIRE_SECURE_TEXTURES = true;

    // private static final Proxy PROXY = Proxy.NO_PROXY;

    public static void main(String[] args) {
        // profileLookup();
        // auth();
        // authMicrosoftMsal();
    }

    // private static void profileLookup() {
    //     ProfileService repository = new ProfileService();
    //     repository.setProxy(PROXY);
    //     repository.findProfilesByName(new String[] {USERNAME}, new ProfileService.ProfileLookupCallback() {
    //         @Override
    //         public void onProfileLookupSucceeded(GameProfile profile) {
    //             System.out.println("Found profile: " + profile);
    //         }

    //         @Override
    //         public void onProfileLookupFailed(GameProfile profile, Exception e) {
    //             System.out.println("Lookup for profile " + profile.getName() + " failed!");
    //             e.printStackTrace();
    //         }
    //     });
    // }

    // private static void auth() {
    //     SessionService service = new SessionService();
    //     service.setProxy(PROXY);

    //     String clientToken = UUID.randomUUID().toString();
    //     AuthenticationService auth = login(clientToken, PASSWORD, false);
    //     for(GameProfile profile : auth.getAvailableProfiles()) {
    //         try {
    //             service.fillProfileProperties(profile);

    //             System.out.println("Profile: " + profile);
    //             System.out.println("Profile Textures: " + profile.getTextures(REQUIRE_SECURE_TEXTURES));
    //         } catch(Exception e) {
    //             System.err.println("Failed to get properties and textures of profile " + profile + ".");
    //             e.printStackTrace();
    //         }
    //     }

    //     auth = login(clientToken, auth.getAccessToken(), true);
    //     GameProfile profile = auth.getSelectedProfile();
    //     try {
    //         service.fillProfileProperties(profile);

    //         System.out.println("Selected Profile: " + profile);
    //         System.out.println("Selected Profile Textures: " + profile.getTextures(REQUIRE_SECURE_TEXTURES));
    //     } catch(Exception e) {
    //         System.err.println("Failed to get properties and textures of selected profile " + profile + ".");
    //         e.printStackTrace();
    //     }
    // }

    // private static AuthenticationService login(String clientToken, String with, boolean token) {
    //     AuthenticationService auth = new MojangAuthenticationService(clientToken);
    //     auth.setProxy(PROXY);
    //     auth.setUsername(EMAIL);
    //     if(token) {
    //         auth.setAccessToken(with);
    //     } else {
    //         auth.setPassword(with);
    //     }

    //     try {
    //         auth.login();
    //     } catch(RequestException e) {
    //         System.err.println("Failed to log in with " + (token ? "token" : "password") + "!");
    //         e.printStackTrace();
    //     }

    //     return auth;
    // }

    // private static void authMicrosoftMsal() {
    //     MSALAuthenticationService auth = new MSALAuthenticationService(AZURE_CLIENT_ID);
    //     auth.setUsername(EMAIL);

    //     auth.getDeviceCode((deviceCode) -> {
    //         if (deviceCode != null) System.out.println(deviceCode.message());
    //         else System.out.println("Authenticating using cached tokens...");
    //     });

    //     auth.login();

    //     System.out.printf("Minecraft username: %s%n", auth.getSelectedProfile().getName());
    // }
}
