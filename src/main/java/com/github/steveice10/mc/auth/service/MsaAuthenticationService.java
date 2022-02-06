package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.exception.request.ServiceUnavailableException;
import com.github.steveice10.mc.auth.exception.request.XboxRequestException;
import com.github.steveice10.mc.auth.util.HTTP;
import com.github.steveice10.mc.auth.util.MSALApplicationOptions;
import com.microsoft.aad.msal4j.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MsaAuthenticationService extends Service {
    public enum AuthenticationFlow {

        NOT_SPECIFIED,
        USERNAME_PASSWORD,
        DEVICE_CODE_FLOW,
        REFRESH_TOKEN,
        INTERACTIVE_FLOW
        // AUTHORIZATION_CODE(831),
        //ACQUIRE_TOKEN_SILENTLY
    }

    protected String accessToken;
    protected boolean loggedIn;
    protected String msaUsername;
    protected String msaPassword;
    protected String minecraftUsername;
    protected GameProfile selectedProfile;
    protected List<GameProfile.Property> properties = new ArrayList<>();
    protected List<GameProfile> profiles = new ArrayList<>();

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
    private Consumer<DeviceCode> deviceCodeConsumer;

    /**
     * Create a new {@link MsaAuthenticationService} for Microsoft accounts using default options.
     * <p>
     * The default options include the "consumers" authority (see <a href="https://docs.microsoft.com/en-us/azure/active-directory/develop/msal-client-application-configuration#authority">MSAL documentation</a>),
     * the <code>XboxLive.signin</code> scope, and a token persistence that saves/loads tokens to/from disk.
     */
    public MsaAuthenticationService(String clientId) throws IOException {
        this(clientId, new MSALApplicationOptions.Builder().build());
    }

    /**
     * Create a new {@link MsaAuthenticationService} for Microsoft accounts using the given {@link MSALApplicationOptions}.
     * <p>
     * Anything not specified in the options will be set to the default values. For more control, use the
     * {@link MSALApplicationOptions.Builder} to set your own options.
     */
    public MsaAuthenticationService(String clientId, MSALApplicationOptions msalOptions) throws MalformedURLException {
        this(clientId, msalOptions.scopes, fixBuilderPersistence(
                PublicClientApplication.builder(clientId).authority(msalOptions.authority), msalOptions).build());
    }

    /**
     * Create a new {@link MsaAuthenticationService} for Microsoft accounts with a custom MSAL {@link PublicClientApplication}.
     * <p>
     * This constructor is most useful if you need more granular control over the MSAL client on top of the provided
     * configurable options. Please note that the {@link PublicClientApplication} must be configured with the same client
     * ID as this service.
     * <p>
     * For more information on how to configure MSAL, see <a href="https://github.com/AzureAD/microsoft-authentication-library-for-java/wiki/Client-Applications">MSAL for Java documentation</a>.
     */
    public MsaAuthenticationService(String clientId, Set<String> scopes, PublicClientApplication app) {
        super(URI.create(""));

        if (clientId == null || clientId.isEmpty())
            throw new IllegalArgumentException("clientId cannot be null or empty.");

        this.clientId = clientId;
        this.scopes = scopes;
        this.app = app;
    }

    /**
     * Gets the access token of the service.
     *
     * @return The user's access token.
     */
    public String getAccessToken() {
        return this.accessToken;
    }

    /**
     * Gets whether the service has been used to log in.
     *
     * @return Whether the service is logged in.
     */
    public boolean isLoggedIn() {
        return this.loggedIn;
    }

    /**
     * Gets the Microsoft Account username.
     *
     * @return The Microsoft Account username.
     */
    public String getMsaUsername() {
        return this.msaUsername;
    }

    /**
     * Sets the Microsoft Account username.
     *
     * @param username Username to set.
     */
    public void setMsaUsername(String username) {
        if (this.loggedIn) {
            throw new IllegalStateException("Cannot change username while user is logged in.");
        }
        
        this.msaUsername = username;
    }

    /**
     * Gets the Microsoft account password.
     *
     * @return The user's Microsoft account password.
     */
    public String getPassword() {
        return this.msaPassword;
    }

    /**
     * Sets the Microsoft Account password.
     *
     * @param password Password to set.
     */
    public void setMsaPassword(String password) {
        if(this.loggedIn) {
            throw new IllegalStateException("Cannot change password while user is logged in.");
        } else {
            this.msaPassword = password;
        }
    }

    /**
     * Gets the Minecraft Account username.
     *
     * @return The Minecraft Account username.
     */
    public String getMinecraftUsername() {
        return this.minecraftUsername;
    }

    /**
     * Gets the properties of the user logged in with the service.
     *
     * @return The user's properties.
     */
    public List<GameProfile.Property> getProperties() {
        return Collections.unmodifiableList(this.properties);
    }

    /**
     * Gets the available profiles of the user logged in with the service.
     *
     * @return The user's available profiles.
     */
    public List<GameProfile> getAvailableProfiles() {
        return Collections.unmodifiableList(this.profiles);
    }

    /**
     * Gets the selected profile of the user logged in with the service.
     *
     * @return The user's selected profile.
     */
    public GameProfile getSelectedProfile() {
        return this.selectedProfile;
    }

    /**
     * Assists in creating a {@link PublicClientApplication.Builder} in one of the constructors.
     *
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
    public void setDeviceCodeConsumer(Consumer<DeviceCode> consumer) {
        this.deviceCodeConsumer = consumer;
    }

    private McLoginResponse getLoginResponseFromCreds() throws RequestException {
        // TODO: Migrate alot of this to {@link HTTP}

        String cookie = "";
        String PPFT = "";
        String urlPost = "";

        try {
            HttpURLConnection connection = HTTP.createUrlConnection(this.getProxy(), MS_LOGIN_ENDPOINT);
            connection.setDoInput(true);
            try (InputStream in = connection.getResponseCode() == 200 ? connection.getInputStream() : connection.getErrorStream()) {
                cookie = connection.getHeaderField("set-cookie");
                String body = inputStreamToString(in);
                Matcher m = PPFT_PATTERN.matcher(body);
                if (m.find()) {
                    PPFT = m.group(1);
                } else {
                    throw new ServiceUnavailableException("Could not parse response of '" + MS_LOGIN_ENDPOINT + "'.");
                }

                m = URL_POST_PATTERN.matcher(body);
                if (m.find()) {
                    urlPost = m.group(1);
                } else {
                    throw new ServiceUnavailableException("Could not parse response of '" + MS_LOGIN_ENDPOINT + "'.");
                }
            }
        } catch (IOException e) {
            throw new ServiceUnavailableException("Could not make request to '" + MS_LOGIN_ENDPOINT + "'.", e);
        }

        if (cookie.isEmpty() || PPFT.isEmpty() || urlPost.isEmpty()) {
            throw new RequestException("Invalid response from '" + MS_LOGIN_ENDPOINT + "' missing one or more of cookie, PPFT or urlPost");
        }

        Map<String, String> map = new HashMap<>();

        map.put("login", this.msaUsername);
        map.put("loginfmt", this.msaUsername);
        map.put("passwd", this.msaPassword);
        map.put("PPFT", PPFT);

        String postData = HTTP.formMapToString(map);
        String code;

        try {
            byte[] bytes = postData.getBytes(StandardCharsets.UTF_8);

            HttpURLConnection connection = HTTP.createUrlConnection(this.getProxy(), URI.create(urlPost));
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            connection.setRequestProperty("Cookie", cookie);

            connection.setDoInput(true);
            connection.setDoOutput(true);

            try(OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }

            if (connection.getResponseCode() != 200 || connection.getURL().toString().equals(urlPost)) {
                // TODO: Get and parse the error from the site
                // See https://github.com/XboxReplay/xboxlive-auth/blob/master/src/core/live/index.ts#L115
                throw new InvalidCredentialsException("Invalid username and/or password");
            }

            Matcher m = CODE_PATTERN.matcher(URLDecoder.decode(connection.getURL().toString(), StandardCharsets.UTF_8.name()));
            if (m.find()) {
                code = m.group(1);
            } else {
                throw new ServiceUnavailableException("Could not parse response of '" + urlPost + "'.");
            }
        } catch (IOException e) {
            throw new ServiceUnavailableException("Could not make request to '" + urlPost + "'.", e);
        }

        MsTokenRequest request = new MsTokenRequest(clientId, code);
        MsTokenResponse response = HTTP.makeRequestForm(this.getProxy(), MS_TOKEN_ENDPOINT, request.toMap(), MsTokenResponse.class);
        assert response != null;
        return this.getLoginResponseFromToken(response.access_token);
    }

    private String inputStreamToString(InputStream inputStream) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }

        return textBuilder.toString();
    }

    /**
     * Get an <code>IAccount</code> from the cache (if available) for re-authentication.
     *
     * @return An <code>IAccount</code> matching the username given to this <code>MSALAuthenticationService</code>
     */
    private IAccount getIAccount() {
        return this.app.getAccounts().join().stream()
                .filter(account -> account.username().equalsIgnoreCase(this.getMsaUsername()))
                .findFirst().orElse(null);
    }

    /**
     * Get an access token from MSAL using Device Code flow authentication.
     */
    private CompletableFuture<IAuthenticationResult> getMsalAccessTokenUsingDeviceCode() throws MalformedURLException {
        if (this.deviceCodeConsumer == null)
            throw new IllegalStateException("Device code consumer is not set.");

        return this.app.acquireToken(DeviceCodeFlowParameters.builder(this.scopes, this.deviceCodeConsumer).build());
    }

    /**
     * Get an access token from MSAL using Interactive Request flow authentication.
     * @throws URISyntaxException
     */
    private CompletableFuture<IAuthenticationResult> getMsalAccessTokenUsingInteractiveRequest() throws MalformedURLException, URISyntaxException {
        return this.app.acquireToken(InteractiveRequestParameters.builder(new URI("http://localhost")).scopes(this.scopes).build());
    }

    /**
     * Attempt to sign in using an existing account
     * @throws MalformedURLException
     */
    private CompletableFuture<IAuthenticationResult> getMsalAccessTokenUsingRefreshToken() throws RequestException, MalformedURLException {
        IAccount account = getIAccount();
        if (account == null) {
            throw new RequestException("Account not found in cache:" + this.msaUsername);
        }

        return this.app.acquireTokenSilently(SilentParameters.builder(this.scopes, account).build());
    }

    /**
     * Get a Minecraft login response from the given
     * Microsoft access token
     *
     * @param accessToken the access token
     * @return The Minecraft login response
     */
    private McLoginResponse getLoginResponseFromToken(String accessToken) throws RequestException {
        XblAuthRequest xblRequest = new XblAuthRequest(accessToken);
        XblAuthResponse response = HTTP.makeRequest(this.getProxy(), XBL_AUTH_ENDPOINT, xblRequest, XblAuthResponse.class);

        XstsAuthRequest xstsRequest = new XstsAuthRequest(response.Token);
        response = HTTP.makeRequest(this.getProxy(), XSTS_AUTH_ENDPOINT, xstsRequest, XblAuthResponse.class);

        if (response.XErr != 0) {
            if (response.XErr == 2148916233L) {
                throw new XboxRequestException("Microsoft account does not have an Xbox Live account attached!");
            } else if (response.XErr == 2148916235L) {
                throw new XboxRequestException("Xbox Live is not available in your country!");
            } else if (response.XErr == 2148916238L) {
                throw new XboxRequestException("This account is a child account! Please add it to a family in order to log in.");
            } else {
                throw new XboxRequestException("Error occurred while authenticating to Xbox Live! Error ID: " + response.XErr);
            }
        }

        McLoginRequest mcRequest = new McLoginRequest(response.DisplayClaims.xui[0].uhs, response.Token);
        return HTTP.makeRequest(this.getProxy(), MC_LOGIN_ENDPOINT, mcRequest, McLoginResponse.class);
    }

    /**
     * Finalizes the authentication process using Xbox API's.
     */
    private void getProfile() throws RequestException {
        McProfileResponse response = HTTP.makeRequest(this.getProxy(),
                MC_PROFILE_ENDPOINT,
                null,
                McProfileResponse.class,
                Collections.singletonMap("Authorization", "Bearer ".concat(this.accessToken)));

        assert response != null;
        this.selectedProfile = new GameProfile(response.id, response.name);
        this.profiles = Collections.singletonList(this.selectedProfile);
        this.minecraftUsername = response.name;
    }

    public void login() throws RequestException {
        login(AuthenticationFlow.NOT_SPECIFIED, false);
    }

    public void login(AuthenticationFlow authFlow, boolean forceReauth) throws RequestException {
        if (forceReauth && authFlow == AuthenticationFlow.REFRESH_TOKEN) {
            throw new InvalidParameterException("REFRESH_TOKEN authentication cannot be used when forcing re-authentication");
        }
        
        // Use a refresh token if one is present and re-authorization is not being forced
        if (!forceReauth && this.msaUsername != null && !this.msaUsername.isEmpty()) {
            IAccount account = this.getIAccount();
            if (account != null) {
                authFlow = AuthenticationFlow.REFRESH_TOKEN;
            }
        }

        if (authFlow == AuthenticationFlow.NOT_SPECIFIED) {
            if (deviceCodeConsumer != null) {
                authFlow = AuthenticationFlow.DEVICE_CODE_FLOW;
            } else if (this.msaUsername != null && !this.msaUsername.isEmpty() &&
                this.msaPassword != null && !this.msaPassword.isEmpty()) {
                authFlow = AuthenticationFlow.USERNAME_PASSWORD;
            } else {
                authFlow = AuthenticationFlow.INTERACTIVE_FLOW;
            }
        }

        McLoginResponse response;
        switch (authFlow) {
            case REFRESH_TOKEN:
                response = loginWithRefreshToken();
                break;
            case USERNAME_PASSWORD:
                response = loginWithUsernamePassword();
                break;
            case INTERACTIVE_FLOW:
                response = loginUsingInteractiveFlow();
                break;
            case DEVICE_CODE_FLOW:
                response = loginUsingDeviceCodeFlow();
                break;
            default:
                throw new IllegalStateException("Unexpected authFlow value");
        }

        if (response == null)
            throw new RequestException("Invalid response received.");
        
        this.accessToken = response.access_token;

        // Get the profile to complete the login process
        this.getProfile();

        this.loggedIn = true;
    }

    private boolean msaUsernameSet() throws RequestException {
        // Complain if the username is not set
        return this.msaUsername != null && !this.msaUsername.isEmpty();
    }

    private void checkMsaUsername() throws RequestException {
        if (!msaUsernameSet()) {
            throw new InvalidCredentialsException("Invalid username.");
        }
    }

    private McLoginResponse loginWithUsernamePassword() throws RequestException {
        checkMsaUsername();

        // Always use the official client ID for username / password
        this.clientId = MINECRAFT_CLIENT_ID;
        
        return this.getLoginResponseFromCreds();
    }

    private McLoginResponse loginWithRefreshToken() throws RequestException {
        try {
            checkMsaUsername();
            return this.getLoginResponseFromToken("d=".concat(this.getMsalAccessTokenUsingRefreshToken().get().accessToken()));
        } catch (MalformedURLException | InterruptedException | ExecutionException ex) {
            throw new RequestException(ex);
        }
    }

    private McLoginResponse loginUsingInteractiveFlow() throws RequestException {
        try {
            return this.getLoginResponseFromToken("d=".concat(this.getMsalAccessTokenUsingInteractiveRequest().get().accessToken()));
        } catch (URISyntaxException | MalformedURLException | InterruptedException | ExecutionException ex) {
            throw new RequestException(ex);
        }
    }

    private McLoginResponse loginUsingDeviceCodeFlow() throws RequestException {
        try {
            return this.getLoginResponseFromToken("d=".concat(this.getMsalAccessTokenUsingDeviceCode().get().accessToken()));
        } catch (MalformedURLException | InterruptedException | ExecutionException ex) {
            throw new RequestException(ex);
        }
    }

    public void logout() {
        if(!this.loggedIn) {
            throw new IllegalStateException("Cannot log out while not logged in.");
        }

        this.accessToken = null;
        this.loggedIn = false;
        this.properties.clear();
        this.profiles.clear();
        this.selectedProfile = null;
        this.clientId = null;
    }

    @Override
    public String toString() {
        return "MsaAuthenticationService{" +
                "clientId='" + this.clientId + '\'' +
                ", accessToken='" + this.accessToken + '\'' +
                ", loggedIn=" + this.loggedIn +
                ", msaUsername='" + this.msaUsername + '\'' +
                ", minecraftUsername='" + this.minecraftUsername + '\'' +
                ", password='" + this.msaPassword + '\'' +
                ", selectedProfile=" + this.selectedProfile +
                ", properties=" + this.properties +
                ", profiles=" + this.profiles +
                '}';
    }

    private static class MsTokenRequest {
        private String client_id;
        private String code;
        private String grant_type;
        private String redirect_uri;
        private String scope;

        protected MsTokenRequest(String clientId, String code) {
            this.client_id = clientId;
            this.code = code;
            this.grant_type = "authorization_code";
            this.redirect_uri = "https://login.live.com/oauth20_desktop.srf";
            this.scope = "service::user.auth.xboxlive.com::MBI_SSL";
        }

        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();

            map.put("client_id", client_id);
            map.put("code", code);
            map.put("grant_type", grant_type);
            map.put("redirect_uri", redirect_uri);
            map.put("scope", scope);

            return map;
        }
    }

    private static class MsRefreshRequest {
        private String client_id;
        private String refresh_token;
        private String grant_type;

        protected MsRefreshRequest(String clientId, String refreshToken) {
            this.client_id = clientId;
            this.refresh_token = refreshToken;
            this.grant_type = "refresh_token";
        }

        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();

            map.put("client_id", client_id);
            map.put("refresh_token", refresh_token);
            map.put("grant_type", grant_type);

            return map;
        }
    }

    private static class XblAuthRequest {
        private String RelyingParty;
        private String TokenType;
        private Properties Properties;

        protected XblAuthRequest(String accessToken) {
            this.RelyingParty = "http://auth.xboxlive.com";
            this.TokenType = "JWT";
            this.Properties = new Properties(accessToken);
        }

        private static class Properties {
            private String AuthMethod;
            private String SiteName;
            private String RpsTicket;

            protected Properties(String accessToken) {
                this.AuthMethod = "RPS";
                this.SiteName = "user.auth.xboxlive.com";
                this.RpsTicket = accessToken;
            }
        }
    }

    private static class XstsAuthRequest {
        private String RelyingParty;
        private String TokenType;
        private Properties Properties;

        protected XstsAuthRequest(String token) {
            this.RelyingParty = "rp://api.minecraftservices.com/";
            this.TokenType = "JWT";
            this.Properties = new Properties(token);
        }

        private static class Properties {
            private String[] UserTokens;
            private String SandboxId;

            protected Properties(String token) {
                this.UserTokens = new String[] { token };
                this.SandboxId = "RETAIL";
            }
        }
    }

    private static class McLoginRequest {
        private String identityToken;

        protected McLoginRequest(String uhs, String identityToken) {
            this.identityToken = "XBL3.0 x=" + uhs + ";" + identityToken;
        }
    }

    // Public so users can access the refresh_token for offline access
    public static class MsTokenResponse {
        public String token_type;
        public String scope;
        public int expires_in;
        public String access_token;
        public String refresh_token;
    }

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

    public static class McLoginResponse {
        public String username;
        public String[] roles;
        public String access_token;
        public String token_type;
        public int expires_in;
    }

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
}
