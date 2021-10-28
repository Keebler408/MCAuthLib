package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.util.HTTP;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MojangAuthenticationService extends AuthenticationService {
    private static final URI DEFAULT_BASE_URI = URI.create("https://authserver.mojang.com/");
    private static final String AUTHENTICATE_ENDPOINT = "authenticate";
    private static final String REFRESH_ENDPOINT = "refresh";
    private static final String INVALIDATE_ENDPOINT = "invalidate";
    @Getter private final String clientToken;
    @Getter private String id;

    /**
     * Creates a new AuthenticationService instance.
     */
    @SuppressWarnings("unused")
    public MojangAuthenticationService() {
        this(UUID.randomUUID().toString());
    }

    /**
     * Creates a new AuthenticationService instance.
     *
     * @param clientToken Client token to use when making authentication requests.
     */
    public MojangAuthenticationService(String clientToken) {
        super(DEFAULT_BASE_URI);
        if (clientToken == null) throw new IllegalArgumentException("ClientToken cannot be null.");
        this.clientToken = clientToken;
    }

    @Override
    public void login() throws RequestException {
        if (this.username == null || this.username.isEmpty())
            throw new InvalidCredentialsException("Invalid username.");

        boolean token = this.accessToken != null && !this.accessToken.isEmpty();
        boolean password = this.password != null && !this.password.isEmpty();
        if (!token && !password) throw new InvalidCredentialsException("Invalid password or access token.");

        var response = HTTP.makeRequest(
                this.getProxy(),
                token ? this.getEndpointUri(REFRESH_ENDPOINT) : this.getEndpointUri(AUTHENTICATE_ENDPOINT),
                token ? new RefreshRequest(this.clientToken, this.accessToken, null) : new AuthenticationRequest(this.username, this.password, this.clientToken),
                AuthenticateRefreshResponse.class);

        if (response == null) throw new RequestException("Server returned invalid response.");
        else if (!response.clientToken.equals(this.clientToken))
            throw new RequestException("Server responded with incorrect client token.");

        if (response.user != null && response.user.id != null) this.id = response.user.id;
        else this.id = this.username;

        this.accessToken = response.accessToken;
        this.profiles = response.availableProfiles != null ? Arrays.asList(response.availableProfiles) : Collections.emptyList();
        this.selectedProfile = response.selectedProfile;
        this.properties.clear();

        if (response.user != null && response.user.properties != null) this.properties.addAll(response.user.properties);

        this.loggedIn = true;
    }

    public void logout() throws RequestException {
        MojangAuthenticationService.InvalidateRequest request = new MojangAuthenticationService.InvalidateRequest(this.clientToken, this.accessToken);
        HTTP.makeRequest(this.getProxy(), this.getEndpointUri(INVALIDATE_ENDPOINT), request);
        super.logout();
        this.id = null;
    }

    /**
     * Selects a game profile.
     *
     * @param profile Profile to select.
     * @throws RequestException If an error occurs while making the request.
     */
    @SuppressWarnings("unused")
    public void selectGameProfile(GameProfile profile) throws RequestException {
        if (!this.loggedIn) throw new RequestException("Cannot change game profile while not logged in.");
        else if (this.selectedProfile != null)
            throw new RequestException("Cannot change game profile when it is already selected.");
        else if (profile == null || !this.profiles.contains(profile))
            throw new IllegalArgumentException("Invalid profile '" + profile + "'.");

        var request = new RefreshRequest(this.clientToken, this.accessToken, profile);
        var response = HTTP.makeRequest(this.getProxy(), this.getEndpointUri(REFRESH_ENDPOINT), request, AuthenticateRefreshResponse.class);

        if (response == null) throw new RequestException("Server returned invalid response.");
        else if (!response.clientToken.equals(this.clientToken))
            throw new RequestException("Server responded with incorrect client token.");

        this.accessToken = response.accessToken;
        this.selectedProfile = response.selectedProfile;
    }

    @Override
    public String toString() {
        return "MojangUserAuthentication{clientToken=" + this.clientToken + ", username=" + this.username + ", accessToken=" + this.accessToken + ", loggedIn=" + this.loggedIn + ", profiles=" + this.profiles + ", selectedProfile=" + this.selectedProfile + "}";
    }

    @SuppressWarnings("unused")
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    private static class Agent {
        private final String name;
        private final int version;
    }

    private static class User {
        public String id;
        public List<GameProfile.Property> properties;
    }

    @SuppressWarnings("unused")
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    private static class AuthenticationRequest {
        private final boolean requestUser = true;
        private final Agent agent = new Agent("Minecraft", 1);
        private final String username;
        private final String password;
        private final String clientToken;
    }

    @SuppressWarnings("unused")
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    private static class RefreshRequest {
        private final boolean requestUser = true;
        private final String clientToken;
        private final String accessToken;
        private final GameProfile selectedProfile;
    }

    @SuppressWarnings("unused")
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    private static class InvalidateRequest {
        private final String clientToken;
        private final String accessToken;
    }

    private static class AuthenticateRefreshResponse {
        public String accessToken;
        public String clientToken;
        public GameProfile selectedProfile;
        public GameProfile[] availableProfiles;
        public User user;
    }
}
