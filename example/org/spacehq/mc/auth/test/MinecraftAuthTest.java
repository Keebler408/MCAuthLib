package org.spacehq.mc.auth.test;

import org.spacehq.mc.auth.AuthenticationService;
import org.spacehq.mc.auth.GameProfile;
import org.spacehq.mc.auth.GameProfileRepository;
import org.spacehq.mc.auth.SessionService;
import org.spacehq.mc.auth.exception.authentication.AuthenticationException;

import java.net.Proxy;
import java.util.UUID;

public class MinecraftAuthTest {
    private static final String USERNAME = "Username";
    private static final String EMAIL = "Username@mail.com";
    private static final String PASSWORD = "Password";
    private static final String ACCESS_TOKEN = null;

    private static final Proxy PROXY = Proxy.NO_PROXY;

    public static void main(String[] args) {
        profileLookup();
        auth();
    }

    private static void profileLookup() {
        GameProfileRepository repository = new GameProfileRepository(PROXY);
        repository.findProfilesByNames(new String[] { USERNAME }, new GameProfileRepository.ProfileLookupCallback() {
            @Override
            public void onProfileLookupSucceeded(GameProfile profile) {
                System.out.println("Found profile: " + profile);
            }

            @Override
            public void onProfileLookupFailed(GameProfile profile, Exception e) {
                System.out.println("Lookup for profile " + profile.getName() + " failed!");
                e.printStackTrace();
            }
        });
    }

    private static void auth() {
        String clientToken = UUID.randomUUID().toString();
        AuthenticationService auth = new AuthenticationService(clientToken, PROXY);
        auth.setUsername(EMAIL);
        if(ACCESS_TOKEN != null) {
            auth.setAccessToken(ACCESS_TOKEN);
        } else {
            auth.setPassword(PASSWORD);
        }

        try {
            auth.login();
        } catch(AuthenticationException e) {
            System.err.println("Failed to log in!");
            e.printStackTrace();
            return;
        }

        SessionService service = new SessionService();
        for(GameProfile profile : auth.getAvailableProfiles()) {
            try {
                service.fillProfileProperties(profile);
                service.fillProfileTextures(profile, false);
            } catch(Exception e) {
                System.err.println("Failed to get properties and textures of profile " + profile + ".");
                e.printStackTrace();
            }

            System.out.println("Profile: " + profile);
        }
    }
}