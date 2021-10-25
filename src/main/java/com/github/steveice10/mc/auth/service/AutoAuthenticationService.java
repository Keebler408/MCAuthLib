package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.util.List;

/**
 * Acts as an automatic wrapper for signing in either a Mojang account or a Microsoft account. Supports MSA Device Code Flow.
 */
public class AutoAuthenticationService extends AuthenticationService {
    @Getter private final MojangAuthenticationService mojangAuth;
    @Getter private final MsaAuthenticationService msaAuth;
    @Getter private AuthType authType;
    @Getter @Setter private String username;
    @Getter @Setter private String password;
    /**
     * Allow MCAuthLib to automatically log authentication progress to the console. False by default.
     */
    @Setter private boolean allowLogging = false;

    /**
     * Creates an automatic wrapper for {@link MojangAuthenticationService} and {@link MsaAuthenticationService}
     */
    @SuppressWarnings("unused")
    public AutoAuthenticationService(String clientId) {
        this(clientId, null);
    }

    /**
     * Creates an automatic wrapper for {@link MojangAuthenticationService} and {@link MsaAuthenticationService}
     */
    public AutoAuthenticationService(String clientId, String deviceCode) {
        super(URI.create(""));
        mojangAuth = new MojangAuthenticationService(clientId);
        msaAuth = new MsaAuthenticationService(clientId, deviceCode);
    }

    /**
     * Automatically determines which auth method was used and returns the corresponding {@link AuthenticationService} (either {@link MojangAuthenticationService} or {@link MsaAuthenticationService})
     */
    private AuthenticationService getAuth() {
        return authType == AuthType.Mojang ? mojangAuth : msaAuth;
    }

    //#region Generic overrides
    @Override
    public String getAccessToken() {
        return getAuth().getAccessToken();
    }

    @Override
    public void setAccessToken(String accessToken) {
        getAuth().setAccessToken(accessToken);
    }

    @Override
    public boolean isLoggedIn() {
        return getAuth().isLoggedIn();
    }

    @Override
    public List<GameProfile.Property> getProperties() {
        return getAuth().getProperties();
    }

    @Override
    public List<GameProfile> getAvailableProfiles() {
        return getAuth().getAvailableProfiles();
    }

    @Override
    public GameProfile getSelectedProfile() {
        return getAuth().getSelectedProfile();
    }

    @Override
    public void logout() throws RequestException {
        getAuth().logout();
    }
    //#endregion

    @Override
    public void login() throws RequestException {
        try {
            authType = attemptAuth(mojangAuth, AuthType.Mojang);
        } catch (InvalidCredentialsException ex) {
            authType = attemptAuth(msaAuth, AuthType.Microsoft);
        }
    }

    /**
     * Triggers a Device Code Flow for authentication. This function should be called twice:
     * First, to get the information needed to let the user sign in using their browser;
     * Second, to complete the login after the user authorized the client.
     *
     * @param userContinued Must be <code>false</code> for the first call. Set to <code>true</code> for the second call after the user authorized the client.
     * @return An {@link com.github.steveice10.mc.auth.service.MsaAuthenticationService.MsCodeResponse} containing the info required to continue the sign-in flow
     */
    @SuppressWarnings("unused")
    public MsaAuthenticationService.MsCodeResponse loginNoPassword(boolean userContinued) throws RequestException {
        if (!userContinued) return msaAuth.getAuthCode();
        else attemptAuth(msaAuth, AuthType.Microsoft);
        return null;
    }

    /**
     * Attempts to sign in to the given {@link AuthenticationService}
     *
     * @param service The {@link AuthenticationService} that sign-in is being attempted on
     * @param type    The {@link AuthType} for this attempt
     */
    private AuthType attemptAuth(AuthenticationService service, AuthType type) throws RequestException {
        if (allowLogging)
            System.out.printf("Attempting %s auth...%n", type);

        service.setUsername(username);

        // If password is empty when a Microsoft account is used, the Device Code Flow will be attempted for sign-in
        if (password != null && !password.isEmpty())
            service.setPassword(password);

        service.login();

        if (allowLogging)
            System.out.printf("Authenticated using: %s%s%n", type, password != null && !password.isEmpty() ? " (using device code)" : "");

        return type;
    }

    public enum AuthType {
        Mojang, Microsoft
    }
}
