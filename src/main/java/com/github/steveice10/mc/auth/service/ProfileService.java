package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.profile.ProfileNotFoundException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.util.HTTP;
import com.github.steveice10.mc.auth.util.Sleep;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for looking up profiles by name.
 */
public class ProfileService extends Service {
    private static final URI DEFAULT_BASE_URI = URI.create("https://api.mojang.com/profiles/");
    private static final String SEARCH_ENDPOINT = "minecraft";

    private static final int MAX_FAIL_COUNT = 3;
    private static final int DELAY_BETWEEN_PAGES = 100;
    private static final int DELAY_BETWEEN_FAILURES = 750;
    private static final int PROFILES_PER_REQUEST = 100;

    /**
     * Creates a new ProfileService instance.
     */
    @SuppressWarnings("unused")
    public ProfileService() {
        super(DEFAULT_BASE_URI);
    }

    private static Set<Set<String>> partition(Set<String> set, int size) {
        var list = new ArrayList<>(set);
        var ret = new HashSet<Set<String>>();
        for (int i = 0; i < list.size(); i += size)
            ret.add(new HashSet<>(list.subList(i, Math.min(i + size, list.size()))));
        return ret;
    }

    /**
     * Locates profiles by their names.
     *
     * @param names    Names to look for.
     * @param callback Callback to pass results to.
     */
    @SuppressWarnings("unused")
    public void findProfilesByName(String[] names, ProfileLookupCallback callback) {
        this.findProfilesByName(names, callback, false);
    }

    /**
     * Locates profiles by their names.
     *
     * @param names    Names to look for.
     * @param callback Callback to pass results to.
     * @param async    Whether to perform requests asynchronously.
     */
    public void findProfilesByName(final String[] names, final ProfileLookupCallback callback, final boolean async) {
        var criteria = new HashSet<String>();
        for (var name : names)
            if (name != null && !name.isEmpty())
                criteria.add(name.toLowerCase());

        Runnable runnable = () -> {
            for (var request : partition(criteria, PROFILES_PER_REQUEST)) {

                var failCount = 0;
                var tryAgain = true;

                while (failCount < MAX_FAIL_COUNT && tryAgain) {
                    tryAgain = false;
                    try {
                        var profiles = HTTP.makeRequest(getProxy(), getEndpointUri(SEARCH_ENDPOINT), request, GameProfile[].class);
                        failCount = 0;
                        var missing = new HashSet<>(request);

                        for (var profile : profiles) {
                            missing.remove(profile.getName().toLowerCase());
                            callback.onProfileLookupSucceeded(profile);
                        }

                        for (var name : missing)
                            callback.onProfileLookupFailed(new GameProfile((UUID) null, name), new ProfileNotFoundException("Server could not find the requested profile."));

                        Sleep.ms(DELAY_BETWEEN_PAGES);
                    } catch (RequestException ex) {
                        failCount++;
                        if (failCount >= MAX_FAIL_COUNT)
                            for (var name : request)
                                callback.onProfileLookupFailed(new GameProfile((UUID) null, name), ex);
                        else {
                            Sleep.ms(DELAY_BETWEEN_FAILURES);
                            tryAgain = true;
                        }
                    }
                }
            }
        };

        if (async) new Thread(runnable, "ProfileLookupThread").start();
        else runnable.run();
    }

    /**
     * Callback for reporting profile lookup results.
     */
    public interface ProfileLookupCallback {
        /**
         * Called when a profile lookup request succeeds.
         *
         * @param profile Profile resulting from the request.
         */
        void onProfileLookupSucceeded(GameProfile profile);

        /**
         * Called when a profile lookup request fails.
         *
         * @param profile Profile that failed to be located.
         * @param e       Exception causing the failure.
         */
        void onProfileLookupFailed(GameProfile profile, Exception e);
    }
}
