package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.exception.request.ServiceUnavailableException;
import com.github.steveice10.mc.auth.exception.request.XboxRequestException;
import com.github.steveice10.mc.auth.util.HTTP;
import com.github.steveice10.mc.auth.util.MSALApplicationOptions;
import com.microsoft.aad.msal4j.*;
import lombok.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class MsaAuthenticationService extends AuthenticationService {
    /**
     * This ID is Microsoft's official Xbox app ID. It will bypass the OAuth grant permission prompts, and also allows
     * child accounts to authenticate. These are not something developers are able to do in custom Azure applications.
     */
    public static final String MINECRAFT_CLIENT_ID = "00000000402b5328";

    private static final URI MS_LOGIN_ENDPOINT = URI.create(String.format("https://login.live.com/oauth20_authorize.srf?redirect_uri=https://login.live.com/oauth20_desktop.srf&scope=service::user.auth.xboxlive.com::MBI_SSL&display=touch&response_type=code&locale=en&client_id=%s", MINECRAFT_CLIENT_ID));
    private static final URI MS_TOKEN_ENDPOINT = URI.create("https://login.live.com/oauth20_token.srf");
    private static final URI XBL_AUTH_ENDPOINT = URI.create("https://user.auth.xboxlive.com/user/authenticate");
    private static final URI XSTS_AUTH_ENDPOINT = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    private static final URI MC_LOGIN_ENDPOINT = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    private static final URI MC_PROFILE_ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile");
    private static final Pattern PPFT_PATTERN = Pattern.compile("sFTTag:[ ]?'.*value=\"(.*)\"/>'");
    private static final Pattern URL_POST_PATTERN = Pattern.compile("urlPost:[ ]?'(.+?(?='))");
    private static final Pattern CODE_PATTERN = Pattern.compile("[?|&]code=([\\w.-]+)");

    private final Set<String> scopes;
    private final PublicClientApplication app;

    private String clientId;
    @Getter @Setter private String refreshToken;
    private Consumer<DeviceCode> deviceCodeConsumer;

    /**
     * Create a new {@link AuthenticationService} for Microsoft accounts using default options.
     * <p>
     * The default options include the "consumers" authority (see <a href="https://docs.microsoft.com/en-us/azure/active-directory/develop/msal-client-application-configuration#authority">MSAL documentation</a>),
     * the <code>XboxLive.signin</code> scope, and a token persistence that saves/loads tokens to/from disk.
     */
    public MsaAuthenticationService(@NonNull String clientId) throws IOException {
        this(clientId, new MSALApplicationOptions.Builder().build());
    }

    /**
     * Create a new {@link AuthenticationService} for Microsoft accounts using the given {@link MSALApplicationOptions}.
     * <p>
     * Anything not specified in the options will be set to the default values. For more control, use the
     * {@link MSALApplicationOptions.Builder} to set your own options.
     */
    public MsaAuthenticationService(@NonNull String clientId, @NonNull MSALApplicationOptions msalOptions) throws MalformedURLException {
        this(clientId, msalOptions.scopes, fixBuilderPersistence(
                PublicClientApplication.builder(clientId).authority(msalOptions.authority), msalOptions).build());
    }

    /**
     * Create a new {@link AuthenticationService} for Microsoft accounts with a custom MSAL {@link PublicClientApplication}.
     * <p>
     * This constructor is most useful if you need more granular control over the MSAL client on top of the provided
     * configurable options. Please note that the {@link PublicClientApplication} must be configured with the same client
     * ID as this service.
     * <p>
     * For more information on how to configure MSAL, see <a href="https://github.com/AzureAD/microsoft-authentication-library-for-java/wiki/Client-Applications">MSAL for Java documentation</a>.
     */
    public MsaAuthenticationService(@NonNull String clientId, @NonNull Set<String> scopes, @NonNull PublicClientApplication app) {
        super(URI.create(""));

        if (clientId.isEmpty())
            throw new IllegalArgumentException("clientId cannot be null or empty.");

        this.clientId = clientId;
        this.scopes = scopes;
        this.app = app;
    }

    /**
     * Assists in creating a {@link PublicClientApplication.Builder} in one of the constructors.
     * <p>
     * Due to the nature of Builders and how MSAL handles null values, we need to do some extra work to ensure that
     * persistence is set correctly.
     */
    private static PublicClientApplication.Builder fixBuilderPersistence(PublicClientApplication.Builder builder, MSALApplicationOptions options) {
        // Set the token persistence, if specified. Necessary step as we cannot pass null to MSAL.
        if (options.tokenPersistence != null)
            builder.setTokenCacheAccessAspect(options.tokenPersistence);
        return builder;
    }

    /**
     * Sets the function to run when a <a href="https://docs.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-device-code">Device Code flow</a> is requested.
     * <p>
     * The provided <code>consumer</code> will be called when Azure is ready for the user to authenticate. Your consumer
     * should somehow get the user to authenticate with the provided URL and user code. How this is implemented is up to
     * you. MSAL automatically handles waiting for the user to authenticate.
     *
     * @param consumer To be called when Azure wants the user to sign in. This involves showing the user the URL to open and the code to enter.
     */
    public void setDeviceCodeConsumer(@NonNull Consumer<DeviceCode> consumer) {
        this.deviceCodeConsumer = consumer;
    }

    // ! this thing
    // todo this thing
    private McLoginResponse getLoginResponseFromCreds() throws RequestException {
        // TODO: Migrate alot of this to {@link HTTP}

        String cookie, PPFT, urlPost;

        try {
            var connection = HTTP.createUrlConnection(getProxy(), MS_LOGIN_ENDPOINT);
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
                    throw new ServiceUnavailableException(String.format("Could not parse response of '%s'.", MS_LOGIN_ENDPOINT));
            }
        } catch (IOException e) {
            throw new ServiceUnavailableException(String.format("Could not make request to '%s'.", MS_LOGIN_ENDPOINT), e);
        }

        if (cookie.isEmpty() || PPFT.isEmpty() || urlPost.isEmpty())
            throw new RequestException(String.format("Invalid response from '%s'. Missing one or more of cookie, PPFT, or urlPost", MS_LOGIN_ENDPOINT));

        var map = new HashMap<String, String>();
        map.put("login", this.username);
        map.put("loginfmt", this.username);
        map.put("passwd", this.password);
        map.put("PPFT", PPFT);

        String code;
        try {
            var bytes = HTTP.formMapToString(map).getBytes(StandardCharsets.UTF_8);
            var connection = HTTP.createUrlConnection(getProxy(), URI.create(urlPost));
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
            else throw new ServiceUnavailableException(String.format("Could not parse response of '%s'.", urlPost));
        } catch (IOException e) {
            throw new ServiceUnavailableException(String.format("Could not make request to '%s'.", urlPost), e);
        }

        return getLoginResponseFromToken(Objects.requireNonNull(HTTP.makeRequestForm(
                getProxy(), MS_TOKEN_ENDPOINT, new MsTokenRequest(this.clientId, code).toMap(), MsTokenResponse.class)).access_token);
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
     * Refreshes the access token and refresh token for further use
     *
     * @return The response containing the refresh token, so the user can store it for later use.
     */
    public MsTokenResponse refreshToken() throws RequestException {
        if (this.refreshToken == null || this.refreshToken.isEmpty())
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
     * Get an <code>IAccount</code> from the cache (if available) for re-authentication.
     *
     * @return An <code>IAccount</code> matching the username given to this <code>MSALAuthenticationService</code>
     */
    private IAccount getIAccount() {
        return this.app.getAccounts().join().stream()
                .filter(account -> account.username().equalsIgnoreCase(getUsername()))
                .findFirst().orElse(null);
    }

    /**
     * Get an access token from MSAL using Device Code flow authentication.
     */
    private CompletableFuture<IAuthenticationResult> getMsalAccessToken() throws MalformedURLException {
        if (this.deviceCodeConsumer == null)
            throw new IllegalStateException("Device code consumer is not set.");

        var account = this.getIAccount();
        return (account == null)
                ? this.app.acquireToken(DeviceCodeFlowParameters.builder(this.scopes, this.deviceCodeConsumer).build())
                : this.app.acquireTokenSilently(SilentParameters.builder(this.scopes, account).build());
    }

    /**
     * Attempt to sign in using an existing refresh token set by {@link #setRefreshToken(String)}
     */
    private McLoginResponse getLoginResponseFromRefreshToken() throws RequestException {
        return getLoginResponseFromToken("d=".concat(refreshToken().access_token));
    }

    /**
     * Get a Minecraft login response from the given Microsoft access token
     */
    private McLoginResponse getLoginResponseFromToken(String accessToken) throws RequestException {
        var response = HTTP.makeRequest(getProxy(), XBL_AUTH_ENDPOINT, new XblAuthRequest(accessToken), XblAuthResponse.class);
        response = HTTP.makeRequest(getProxy(), XSTS_AUTH_ENDPOINT, new XstsAuthRequest(response.Token), XblAuthResponse.class);

        if (response.XErr != 0)
            switch ((int) (response.XErr - 2148916230L)) {
                case 3 -> throw new XboxRequestException("Microsoft account does not have an Xbox Live account attached!");
                case 5 -> throw new XboxRequestException("Xbox Live is not available in your country!");
                case 8 -> throw new XboxRequestException("This account is a child account! Please add it to a family in order to log in.");
                default -> throw new XboxRequestException(String.format("Error occurred while authenticating to Xbox Live! Error ID: %s", response.XErr));
            }

        return HTTP.makeRequest(getProxy(), MC_LOGIN_ENDPOINT, new McLoginRequest(response.DisplayClaims.xui[0].uhs, response.Token), McLoginResponse.class);
    }

    /**
     * Finalizes the authentication process using Xbox API's.
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
        try {
            boolean password = this.password != null && !this.password.isEmpty();
            boolean refresh = this.refreshToken != null && !this.refreshToken.isEmpty();

            // Complain if the username is not set
            if (this.username == null || this.username.isEmpty())
                throw new InvalidCredentialsException("Invalid username.");

            // Fix client ID if a password is set
            if (password)
                this.clientId = MINECRAFT_CLIENT_ID;

            // Try to log in to the users account, using refresh token, credentials, or device code
            var response = refresh ? getLoginResponseFromRefreshToken()
                    : password ? getLoginResponseFromCreds()
                    : getLoginResponseFromToken("d=".concat(getMsalAccessToken().get().accessToken()));

            if (response == null)
                throw new RequestException("Invalid response received.");
            this.accessToken = response.access_token;

            // Get the profile to complete the login process
            getProfile();

            this.loggedIn = true;
        } catch (MalformedURLException | ExecutionException | InterruptedException ex) {
            throw new RequestException(ex);
        }
    }

    @Override
    public String toString() {
        return "MsaAuthenticationService{" +
                "clientId='" + this.clientId + '\'' +
                ", loggedIn=" + this.loggedIn +
                '}';
    }

    //#region Requests
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    private static class MsTokenRequest {
        private final String client_id;
        private final String code;

        public Map<String, String> toMap() {
            var map = new HashMap<String, String>();
            map.put("client_id", client_id);
            map.put("code", code);
            map.put("grant_type", "authorization_code");
            map.put("redirect_uri", "https://login.live.com/oauth20_desktop.srf");
            map.put("scope", "service::user.auth.xboxlive.com::MBI_SSL");
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
