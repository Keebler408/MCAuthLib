package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.exception.request.XboxRequestException;
import com.github.steveice10.mc.auth.util.HTTP;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.net.Proxy;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MsaAuthenticationService extends AuthenticationService {
    private static final URI MS_CODE_ENDPOINT = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode");
    private static final URI MS_CODE_TOKEN_ENDPOINT = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
    private static final URI MS_TOKEN_ENDPOINT = URI.create("https://login.live.com/oauth20_token.srf");
    private static final URI XBL_AUTH_ENDPOINT = URI.create("https://user.auth.xboxlive.com/user/authenticate");
    private static final URI XSTS_AUTH_ENDPOINT = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    private static final URI MC_LOGIN_ENDPOINT = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    public static final URI MC_PROFILE_ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile");
    @Getter private final String clientId;
    private String deviceCode;
    @Getter @Setter private String refreshToken;

    @SuppressWarnings("unused")
    public MsaAuthenticationService(String clientId) {
        this(clientId, null);
    }

    public MsaAuthenticationService(String clientId, String deviceCode) {
        if (clientId == null)
            throw new IllegalArgumentException("ClientId cannot be null.");

        this.clientId = clientId;
        this.deviceCode = deviceCode;
    }

    /**
     * Generate a single use code for Microsoft authentication
     *
     * @return The code along with other returned data
     */
    public MsCodeResponse getAuthCode() throws RequestException {
        if (this.clientId == null)
            throw new InvalidCredentialsException("Invalid client id.");

        var response = HTTP.makeRequestForm(getProxy(),
                MS_CODE_ENDPOINT,
                new MsCodeRequest(this.clientId).toMap(),
                MsCodeResponse.class);

        assert response != null;
        this.deviceCode = response.device_code;
        return response;
    }

    /**
     * Attempt to get the authentication data from the previously
     * generated device code from {@link #getAuthCode()}
     *
     * @return The final Minecraft authentication data
     */
    private McLoginResponse getLoginResponseFromCode() throws RequestException {
        if (this.deviceCode == null)
            throw new InvalidCredentialsException("Invalid device code.");

        var response = HTTP.makeRequestForm(getProxy(),
                MS_CODE_TOKEN_ENDPOINT,
                new MsCodeTokenRequest(this.clientId, this.deviceCode).toMap(),
                MsTokenResponse.class);

        assert response != null;
        this.refreshToken = response.refresh_token;
        return getLoginResponseFromToken("d=".concat(response.access_token), getProxy());
    }

    /**
     * Refreshes the access token and refresh token for further use
     *
     * @return The response containing the refresh token, so the user can store it for later use.
     */
    public MsTokenResponse refreshToken() throws RequestException {
        if (this.refreshToken == null)
            throw new InvalidCredentialsException("Invalid refresh token.");

        var response = HTTP.makeRequestForm(getProxy(),
                MS_TOKEN_ENDPOINT,
                new MsRefreshRequest(this.clientId, this.refreshToken).toMap(),
                MsTokenResponse.class);

        assert response != null;
        this.accessToken = response.access_token;
        this.refreshToken = response.refresh_token;
        return response;
    }

    /**
     * Attempt to sign in using an existing refresh token set by {@link #setRefreshToken(String)}
     */
    private McLoginResponse getLoginResponseFromRefreshToken() throws RequestException {
        return getLoginResponseFromToken("d=".concat(refreshToken().access_token), getProxy());
    }

    /**
     * Get a Minecraft login response from the given
     * Microsoft access token
     *
     * @param accessToken the access token
     * @return The Minecraft login response
     */
    public static McLoginResponse getLoginResponseFromToken(String accessToken, Proxy proxy) throws RequestException {
        var response = HTTP.makeRequest(proxy, XBL_AUTH_ENDPOINT, new XblAuthRequest(accessToken), XblAuthResponse.class);
        response = HTTP.makeRequest(proxy, XSTS_AUTH_ENDPOINT, new XstsAuthRequest(response.Token), XblAuthResponse.class);

        if (response.XErr != 0)
            if (response.XErr == 2148916233L)
                throw new XboxRequestException("Microsoft account does not have an Xbox Live account attached!");
            else if (response.XErr == 2148916235L)
                throw new XboxRequestException("Xbox Live is not available in your country!");
            else if (response.XErr == 2148916238L)
                throw new XboxRequestException("This account is a child account! Please add it to a family in order to log in.");
            else
                throw new XboxRequestException("Error occurred while authenticating to Xbox Live! Error ID: " + response.XErr);

        return HTTP.makeRequest(proxy, MC_LOGIN_ENDPOINT, new McLoginRequest(response.DisplayClaims.xui[0].uhs, response.Token), McLoginResponse.class);
    }

    /**
     * Fetch the profile for the current account
     */
    private void getProfile() throws RequestException {
        var response = HTTP.makeRequest(getProxy(),
                MC_PROFILE_ENDPOINT,
                null,
                McProfileResponse.class,
                Collections.singletonMap("Authorization", "Bearer ".concat(this.accessToken)));

        assert response != null;
        this.selectedProfile = new GameProfile(response.id, response.name);
        this.profiles = Collections.singletonList(this.selectedProfile);
        this.username = response.name;
    }

    @Override
    public void login() throws RequestException {
        boolean token = this.clientId != null && !this.clientId.isEmpty();
        boolean device = this.deviceCode != null && !this.deviceCode.isEmpty();
        boolean refresh = this.refreshToken != null && !this.refreshToken.isEmpty();

        // Username invalid
        if (this.username == null || this.username.isEmpty())
            throw new InvalidCredentialsException("Invalid username.");

        // Token(s) invalid
        if (!token && !refresh)
            throw new InvalidCredentialsException("Invalid access token or refresh token.");

        // Attempt to get device code
        if (!device && !refresh)
            this.deviceCode = getAuthCode().device_code;

        // Try to log in to the users account, using either refresh token or device code
        var response = refresh ? getLoginResponseFromRefreshToken() : getLoginResponseFromCode();
        if (response == null)
            throw new RequestException("Invalid response received.");
        else this.accessToken = response.access_token;

        try {
            getProfile();
        } catch (RequestException ex) {
            ex.printStackTrace(); // this was ignored before
            // We are on a cracked account
            if (this.username == null || this.username.isEmpty())
                this.username = response.username; // Not sure what this username is but its sent back from the API
        }
        this.loggedIn = true;
    }

    @Override
    public String toString() {
        return "MsaAuthenticationService{" +
                "deviceCode='" + this.deviceCode + '\'' +
                ", clientId='" + this.clientId + '\'' +
                ", accessToken='" + this.accessToken + '\'' +
                ", loggedIn=" + this.loggedIn +
                ", username='" + this.username + '\'' +
                ", password='" + this.password + '\'' +
                ", selectedProfile=" + this.selectedProfile +
                ", properties=" + this.properties +
                ", profiles=" + this.profiles +
                '}';
    }

    //#region Requests
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    private static class MsCodeRequest {
        private String client_id;
        private String scope;

        /**
         * Creates a request with no offline access
         */
        protected MsCodeRequest(String clientId) {
            this(clientId, false);
        }

        /**
         * @param offlineAccess Set to true to request offline access for the refresh token, allowing re-authentication.
         */
        protected MsCodeRequest(String clientId, boolean offlineAccess) {
            this.client_id = clientId;
            this.scope = "XboxLive.signin".concat(offlineAccess ? " offline_access" : "");
        }

        public Map<String, String> toMap() {
            var map = new HashMap<String, String>();
            map.put("client_id", client_id);
            map.put("scope", scope);
            return map;
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    private static class MsCodeTokenRequest {
        private final String client_id;
        private final String device_code;

        public Map<String, String> toMap() {
            var map = new HashMap<String, String>();
            map.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            map.put("client_id", client_id);
            map.put("device_code", device_code);
            return map;
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    private static class MsRefreshRequest {
        private final String client_id;
        private final String refresh_token;

        public Map<String, String> toMap() {
            var map = new HashMap<String, String>();
            map.put("client_id", client_id);
            map.put("refresh_token", refresh_token);
            map.put("grant_type", "refresh_token");
            return map;
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class XblAuthRequest {
        private final String RelyingParty = "http://auth.xboxlive.com";
        private final String TokenType = "JWT";
        private final Properties Properties;

        protected XblAuthRequest(String accessToken) {
            this.Properties = new Properties(accessToken);
        }

        @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
        private static class Properties {
            private final String AuthMethod = "RPS";
            private final String SiteName = "user.auth.xboxlive.com";
            private final String RpsTicket;
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class XstsAuthRequest {
        private final String RelyingParty = "rp://api.minecraftservices.com/";
        private final String TokenType = "JWT";
        private final Properties Properties;

        protected XstsAuthRequest(String token) {
            this.Properties = new Properties(token);
        }

        private static class Properties {
            private final String[] UserTokens;
            private final String SandboxId = "RETAIL";

            protected Properties(String token) {
                this.UserTokens = new String[]{token};
            }
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class McLoginRequest {
        private final String identityToken;

        protected McLoginRequest(String uhs, String identityToken) {
            this.identityToken = String.format("XBL3.0 x=%s;%s", uhs, identityToken);
        }
    }
    //#endregion

    //#region Responses
    @SuppressWarnings("unused")
    public static class MsCodeResponse {
        public String user_code;
        public String device_code;
        public URI verification_uri;
        public int expires_in;
        public int interval;
        public String message;
    }

    @SuppressWarnings("unused")
    public static class MsTokenResponse {
        public String token_type;
        public String scope;
        public int expires_in;
        public String access_token;
        public String refresh_token;
    }

    @SuppressWarnings("unused")
    private static class XblAuthResponse {
        /* Only appear in error responses */
        public String Identity;
        public long XErr;
        public String Message;
        public String Redirect;

        public String IssueInstant;
        public String NotAfter;
        public String Token;
        public DisplayClaims DisplayClaims;

        private static class DisplayClaims {
            public Xui[] xui;
        }

        private static class Xui {
            public String uhs;
        }
    }

    @SuppressWarnings("unused")
    public static class McLoginResponse {
        public String username;
        public String[] roles;
        public String access_token;
        public String token_type;
        public int expires_in;
    }

    @SuppressWarnings("unused")
    public static class McProfileResponse {
        public UUID id;
        public String name;
        public Skin[] skins;
        //public String capes; // Not sure on the datatype or response

        private static class Skin {
            public UUID id;
            public String state;
            public URI url;
            public String variant;
            public String alias;
        }
    }
    //#endregion
}
