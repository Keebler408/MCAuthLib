package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.util.HTTP;
import com.microsoft.aad.msal4j.DeviceCode;
import com.microsoft.aad.msal4j.DeviceCodeFlowParameters;
import com.microsoft.aad.msal4j.PublicClientApplication;
import lombok.Getter;
import lombok.NonNull;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class MSALAuthenticationService extends AuthenticationService {
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/";
    private static final String[] SCOPES = new String[]{"XboxLive.signin", "XboxLive.offline_access"};

    @Getter private final String clientId;
    private final PublicClientApplication app;
    private String msAccessToken;

    public MSALAuthenticationService(String clientId) throws MalformedURLException {
        super(URI.create(""));

        this.clientId = clientId;
        this.app = PublicClientApplication.builder(clientId).authority(AUTHORITY).build();
    }

    public void getDeviceCode(@NonNull Consumer<DeviceCode> deviceCodeConsumer, @NonNull Consumer<Throwable> failureConsumer) {
        var future = app.acquireToken(DeviceCodeFlowParameters.builder(Set.of(SCOPES), deviceCodeConsumer).build());
        try {
            this.msAccessToken = future.get().accessToken();
        } catch (ExecutionException | InterruptedException ex) {
            failureConsumer.accept(ex);
        }
    }

    private void getProfile() throws RequestException {
        var response = HTTP.makeRequest(this.getProxy(),
                MsaAuthenticationService.MC_PROFILE_ENDPOINT,
                null,
                MsaAuthenticationService.McProfileResponse.class,
                Collections.singletonMap("Authorization", "Bearer " + this.accessToken));

        assert response != null;

        this.selectedProfile = new GameProfile(response.id, response.name);
        this.profiles = Collections.singletonList(this.selectedProfile);
        this.username = response.name;
    }

    @Override
    public void login() throws RequestException {
        this.accessToken = MsaAuthenticationService.getLoginResponseFromToken("d=".concat(this.msAccessToken), this.getProxy()).access_token;
        getProfile();
        this.loggedIn = true;
    }
}
