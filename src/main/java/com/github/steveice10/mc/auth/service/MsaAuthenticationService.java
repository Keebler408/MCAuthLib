package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.exception.request.ServiceUnavailableException;
import com.github.steveice10.mc.auth.exception.request.XboxRequestException;
import com.github.steveice10.mc.auth.util.HTTP;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class MsaAuthenticationService extends AuthenticationService {
    private static final URI MS_CODE_ENDPOINT = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode");
    private static final URI MS_CODE_TOKEN_ENDPOINT = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
    private static final URI MS_LOGIN_ENDPOINT = URI.create("https://login.live.com/oauth20_authorize.srf?redirect_uri=https://login.live.com/oauth20_desktop.srf&scope=service::user.auth.xboxlive.com::MBI_SSL&display=touch&response_type=code&locale=en&client_id=00000000402b5328");
    private static final URI MS_TOKEN_ENDPOINT = URI.create("https://login.live.com/oauth20_token.srf");
    private static final URI XBL_AUTH_ENDPOINT = URI.create("https://user.auth.xboxlive.com/user/authenticate");
    private static final URI XSTS_AUTH_ENDPOINT = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    private static final URI MC_LOGIN_ENDPOINT = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    private static final URI MC_PROFILE_ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile");
    private static final Pattern PPFT_PATTERN = Pattern.compile("sFTTag:[ ]?'.*value=\"(.*)\"/>'");
    private static final Pattern URL_POST_PATTERN = Pattern.compile("urlPost:[ ]?'(.+?(?='))");
    private static final Pattern CODE_PATTERN = Pattern.compile("[?|&]code=([\\w.-]+)");
    @Getter private final String clientId;
    private String deviceCode;

    @SuppressWarnings("unused")
    public MsaAuthenticationService(String clientId) {
        this(clientId, null);
    }

    public MsaAuthenticationService(String clientId, String deviceCode) {
        super(URI.create(""));
        if (clientId == null) throw new IllegalArgumentException("ClientId cannot be null.");
        this.clientId = clientId;
        this.deviceCode = deviceCode;
    }

    /**
     * Generate a single use code for Microsoft authentication
     *
     * @return The code along with other returned data
     */
    public MsCodeResponse getAuthCode() throws RequestException {
        if (this.clientId == null) throw new InvalidCredentialsException("Invalid client id.");
        var request = new MsCodeRequest(this.clientId);
        var response = HTTP.makeRequestForm(this.getProxy(), MS_CODE_ENDPOINT, request.toMap(), MsCodeResponse.class);

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
        if (this.deviceCode == null) throw new InvalidCredentialsException("Invalid device code.");
        var request = new MsCodeTokenRequest(this.clientId, this.deviceCode);
        var response = HTTP.makeRequestForm(this.getProxy(), MS_CODE_TOKEN_ENDPOINT, request.toMap(), MsTokenResponse.class);

        assert response != null;
        return getLoginResponseFromToken("d=".concat(response.access_token));
    }

    // ! this thing
    // todo this thing
    private McLoginResponse getLoginResponseFromCreds() throws RequestException {
        // TODO: Migrate alot of this to {@link HTTP}

        String cookie, PPFT, urlPost;

        try {
            var connection = HTTP.createUrlConnection(this.getProxy(), MS_LOGIN_ENDPOINT);
            connection.setDoInput(true);

            try (var in = connection.getResponseCode() == 200 ? connection.getInputStream() : connection.getErrorStream()) {
                cookie = connection.getHeaderField("set-cookie");

                var body = inputStreamToString(in);
                var mPPFT = PPFT_PATTERN.matcher(body);
                var mUrlPost = URL_POST_PATTERN.matcher(body);

                if (mPPFT.find() && mUrlPost.find()) {
                    PPFT = mPPFT.group(1);
                    urlPost = mUrlPost.group(1);
                } else
                    throw new ServiceUnavailableException("Could not parse response of '" + MS_LOGIN_ENDPOINT + "'.");
            }
        } catch (IOException e) {
            throw new ServiceUnavailableException("Could not make request to '" + MS_LOGIN_ENDPOINT + "'.", e);
        }

        if (cookie.isEmpty() || PPFT.isEmpty() || urlPost.isEmpty())
            throw new RequestException("Invalid response from '" + MS_LOGIN_ENDPOINT + "' missing one or more of cookie, PPFT or urlPost");

        var map = new HashMap<String, String>();
        map.put("login", this.username);
        map.put("loginfmt", this.username);
        map.put("passwd", this.password);
        map.put("PPFT", PPFT);

        String code;
        try {
            var bytes = HTTP.formMapToString(map).getBytes(StandardCharsets.UTF_8);
            var connection = HTTP.createUrlConnection(this.getProxy(), URI.create(urlPost));
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            connection.setRequestProperty("Cookie", cookie);

            connection.setDoInput(true);
            connection.setDoOutput(true);

            try (var out = connection.getOutputStream()) {
                out.write(bytes);
            }

            if (connection.getResponseCode() != 200 || connection.getURL().toString().equals(urlPost))
                // todo: Get and parse the error from the site
                // See https://github.com/XboxReplay/xboxlive-auth/blob/master/src/core/live/index.ts#L115
                throw new InvalidCredentialsException("Invalid username and/or password");

            var m = CODE_PATTERN.matcher(URLDecoder.decode(connection.getURL().toString(), StandardCharsets.UTF_8.name()));
            if (m.find()) code = m.group(1);
            else throw new ServiceUnavailableException("Could not parse response of '" + urlPost + "'.");
        } catch (IOException e) {
            throw new ServiceUnavailableException("Could not make request to '" + urlPost + "'.", e);
        }

        return getLoginResponseFromToken(Objects.requireNonNull(HTTP.makeRequestForm(this.getProxy(), MS_TOKEN_ENDPOINT, new MsTokenRequest(code).toMap(), MsTokenResponse.class)).access_token);
    }

    private String inputStreamToString(InputStream inputStream) throws IOException {
        var textBuilder = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c;
            while ((c = reader.read()) != -1) textBuilder.append((char) c);
        }
        return textBuilder.toString();
    }

    /**
     * Get a Minecraft login response from the given
     * Microsoft access token
     *
     * @param accessToken the access token
     * @return The Minecraft login response
     */
    private McLoginResponse getLoginResponseFromToken(String accessToken) throws RequestException {
        var response = HTTP.makeRequest(this.getProxy(), XBL_AUTH_ENDPOINT, new XblAuthRequest(accessToken), XblAuthResponse.class);
        response = HTTP.makeRequest(this.getProxy(), XSTS_AUTH_ENDPOINT, new XstsAuthRequest(response.Token), XblAuthResponse.class);

        if (response.XErr != 0)
            if (response.XErr == 2148916233L)
                throw new XboxRequestException("Microsoft account does not have an Xbox Live account attached!");
            else if (response.XErr == 2148916235L)
                throw new XboxRequestException("Xbox Live is not available in your country!");
            else if (response.XErr == 2148916238L)
                throw new XboxRequestException("This account is a child account! Please add it to a family in order to log in.");
            else
                throw new XboxRequestException("Error occurred while authenticating to Xbox Live! Error ID: " + response.XErr);

        return HTTP.makeRequest(this.getProxy(), MC_LOGIN_ENDPOINT, new McLoginRequest(response.DisplayClaims.xui[0].uhs, response.Token), McLoginResponse.class);
    }

    /**
     * Fetch the profile for the current account
     */
    private void getProfile() throws RequestException {
        var response = HTTP.makeRequest(this.getProxy(), MC_PROFILE_ENDPOINT, null, McProfileResponse.class, Collections.singletonMap("Authorization", "Bearer " + this.accessToken));
        assert response != null;
        this.selectedProfile = new GameProfile(response.id, response.name);
        this.profiles = Collections.singletonList(this.selectedProfile);
        this.username = response.name;
    }

    @Override
    public void login() throws RequestException {
        boolean token = this.clientId != null && !this.clientId.isEmpty();
        boolean device = this.deviceCode != null && !this.deviceCode.isEmpty();
        boolean password = this.password != null && !this.password.isEmpty();

        if (!token && !password) throw new InvalidCredentialsException("Invalid password or access token.");
        if (password && (this.username == null || this.username.isEmpty()))
            throw new InvalidCredentialsException("Invalid username.");

        // Attempt to get device code
        if (!password && !device) this.deviceCode = getAuthCode().device_code;

        // Get the response
        var response = password ? getLoginResponseFromCreds() : getLoginResponseFromCode();
        if (response == null) throw new RequestException("Invalid response received.");
        else this.accessToken = response.access_token;

        try {
            getProfile();
        } catch (RequestException ignored) {
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
        private final String client_id;

        public Map<String, String> toMap() {
            var map = new HashMap<String, String>();
            map.put("client_id", client_id);
            map.put("scope", "XboxLive.signin offline_access");
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
    private static class MsTokenRequest {
        private final String code;

        public Map<String, String> toMap() {
            var map = new HashMap<String, String>();
            map.put("client_id", "00000000402b5328");
            map.put("code", code);
            map.put("grant_type", "authorization_code");
            map.put("redirect_uri", "https://login.live.com/oauth20_desktop.srf");
            map.put("scope", "service::user.auth.xboxlive.com::MBI_SSL");
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
    private static class MsTokenResponse {
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
    private static class McLoginResponse {
        public String username;
        public String[] roles;
        public String access_token;
        public String token_type;
        public int expires_in;
    }

    @SuppressWarnings("unused")
    private static class McProfileResponse {
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
