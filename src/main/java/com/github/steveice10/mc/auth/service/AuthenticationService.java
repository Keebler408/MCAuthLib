package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import lombok.Getter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service used for authenticating users.
 */
public abstract class AuthenticationService extends Service {
    @Getter protected String accessToken, username, password;
    @Getter protected GameProfile selectedProfile;
    protected boolean loggedIn;
    protected List<GameProfile.Property> properties = new ArrayList<>();
    protected List<GameProfile> profiles = new ArrayList<>();

    public AuthenticationService() {
        this(URI.create(""));
    }

    public AuthenticationService(URI defaultURI) {
        super(defaultURI);
    }

    /**
     * Sets the access token of the service.
     *
     * @param accessToken Access token to set.
     */
    public void setAccessToken(String accessToken) {
        if (this.loggedIn && this.selectedProfile != null)
            throw new IllegalStateException("Cannot change access token while user is logged in and profile is selected.");
        else this.accessToken = accessToken;
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
     * Sets the username of the service.
     *
     * @param username Username to set.
     */
    public void setUsername(String username) {
        if (this.loggedIn && this.selectedProfile != null)
            throw new IllegalStateException("Cannot change username while user is logged in and profile is selected.");
        else this.username = username;
    }

    /**
     * Sets the password of the service.
     *
     * @param password Password to set.
     */
    public void setPassword(String password) {
        if (this.loggedIn && this.selectedProfile != null)
            throw new IllegalStateException("Cannot change password while user is logged in and profile is selected.");
        else this.password = password;
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
     * Logs the service in.
     * The current access token will be used if set. Otherwise, password-based authentication will be used.
     *
     * @throws RequestException If an error occurs while making the request.
     */
    public abstract void login() throws RequestException;

    /**
     * Logs the service out.
     *
     * @throws RequestException If an error occurs while making the request.
     */
    public void logout() throws RequestException {
        if (!this.loggedIn) throw new IllegalStateException("Cannot log out while not logged in.");

        this.accessToken = null;
        this.loggedIn = false;
        this.properties.clear();
        this.profiles.clear();
        this.selectedProfile = null;
    }
}
