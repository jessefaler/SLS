package net.slimelabs.vsls.utils;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.entities.ServerOverrides;
import com.protoxon.S4J.entities.Blueprint;
import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.requests.SLSActionImpl;
import org.json.JSONObject;

import java.util.regex.Pattern;

public class VersionFetcher {

    private static final String PAPER_VERSIONS_URL = "https://fill.papermc.io/v3/projects/paper";
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+");
    private static final SimpleRequester REQUESTER = new SimpleRequester();

    /**
     * Resolves the version from overrides or blueprint. Returns an SLSAction that yields the version string.
     * When the version is "latest", fetches from the Paper API (async); otherwise yields immediately.
     */
    public static SLSAction<String> resolveVersion(ServerOverrides overrides, Blueprint blueprint, S4J api) {
        String versionOverride = overrides != null ? overrides.getVersion() : null;
        String blueprintVersion = blueprint != null ? blueprint.getServerVersion() : null;
        String software = blueprint != null ? blueprint.getServerSoftware() : null;
        String rawVersion = (versionOverride != null && !versionOverride.isEmpty())
                ? versionOverride
                : blueprintVersion;

        if (rawVersion == null || rawVersion.isEmpty()) {
            return SLSActionImpl.onExecute(api, () -> "null");
        }
        if ("latest".equalsIgnoreCase(rawVersion)) {
            if(software == null) return SLSActionImpl.onExecute(api, () -> rawVersion);
            if(software.equalsIgnoreCase("paper")) {
                return fetchLatestPaperRelease(api);
            }
        }
        return SLSActionImpl.onExecute(api, () -> rawVersion);
    }

    /**
     * Fetches the latest Paper release version from the Paper API. Returns an SLSAction that yields the version string.
     */
    public static SLSAction<String> fetchLatestPaperRelease(S4J api) {
        return REQUESTER.get(api, PAPER_VERSIONS_URL)
                .map(body -> body != null ? parseLatestVersion(body) : null);
    }

    private static String parseLatestVersion(String body) {
        try {
            JSONObject json = new JSONObject(body);
            JSONObject versions = json.getJSONObject("versions");
            if (versions == null || versions.isEmpty()) {
                return null;
            }
            String latestMajor = versions.keySet().stream()
                    .max(VersionFetcher::compareVersionStrings)
                    .orElse(null);
            if (latestMajor == null) {
                return null;
            }
            return versions.getJSONArray(latestMajor).getString(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Compares two version strings (e.g. "1.21" vs "1.20"). Returns negative if a &lt; b, positive if a &gt; b, 0 if equal.
     */
    private static int compareVersionStrings(String a, String b) {
        int[] partsA = parseVersionParts(a);
        int[] partsB = parseVersionParts(b);
        int maxLen = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < maxLen; i++) {
            int numA = i < partsA.length ? partsA[i] : 0;
            int numB = i < partsB.length ? partsB[i] : 0;
            if (numA != numB) {
                return Integer.compare(numA, numB);
            }
        }
        return 0;
    }

    private static int[] parseVersionParts(String version) {
        return VERSION_PATTERN.matcher(version).results()
                .mapToInt(m -> Integer.parseInt(m.group()))
                .toArray();
    }
}
