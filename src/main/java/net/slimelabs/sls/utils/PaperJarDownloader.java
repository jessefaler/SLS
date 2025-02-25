package net.slimelabs.sls.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import net.slimelabs.sls.SLS;
import org.json.JSONArray;
import org.json.JSONObject;

public class PaperJarDownloader {

    /**
     * Downloads the Paper jar for the given Minecraft version to the specified target directory,
     * saving the file as "server.jar".
     *
     * @param version    the Minecraft version, e.g., "1.21"
     * @param targetPath the directory where the jar should be saved
     */
    public static boolean downloadPaperJar(String version, String targetPath) {
        // Get the latest build number for the version.
        String downloadUrl = "";
        try {
            int latestBuild = getLatestBuildForVersion(version);
            // Construct the jar name as required by the API.
            String jarName = "paper-" + version + "-" + latestBuild + ".jar";
            // Build the download URL.
            downloadUrl = "https://api.papermc.io/v2/projects/paper/versions/"
                    + version + "/builds/" + latestBuild + "/downloads/" + jarName;

            System.out.println("Downloading from: " + downloadUrl);
            // Ensure the target directory exists.
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            // Save the file as "server.jar" regardless of the jarName from the API.
            String saveFilePath = targetDir.getAbsolutePath() + File.separator + "server.jar";

            downloadFile(downloadUrl, saveFilePath);
            return true;
        } catch (IOException e) {
            throw new PaperDownloadException("Failed to download paper jar " + version + " from " + downloadUrl);
        }
    }

    /**
     * Queries the PaperMC API for the given version and returns the latest build number.
     *
     * @param version the Minecraft version, e.g., "1.21"
     * @return the latest build number as an integer
     * @throws IOException if an I/O error occurs while querying the API
     */
    private static int getLatestBuildForVersion(String version) throws IOException {
        String apiUrl = "https://api.papermc.io/v2/projects/paper/versions/" + version;
        URL url = new URL(apiUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        try (InputStream is = con.getInputStream()) {
            String jsonText = new String(is.readAllBytes());
            JSONObject json = new JSONObject(jsonText);
            JSONArray builds = json.getJSONArray("builds");
            // Return the last element (latest build).
            return builds.getInt(builds.length() - 1);
        }
    }

    /**
     * Downloads a file from the given URL and saves it to the specified file path.
     *
     * @param fileURL      the URL to download the file from
     * @param saveFilePath the path where the file should be saved
     * @throws IOException if an error occurs during the download
     */
    private static void downloadFile(String fileURL, String saveFilePath) throws IOException {
        URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (InputStream inputStream = new BufferedInputStream(httpConn.getInputStream());
                 FileOutputStream outputStream = new FileOutputStream(saveFilePath)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } else {
            throw new IOException("No file to download. Server returned HTTP code: " + responseCode);
        }
        httpConn.disconnect();
    }

    // Example usage:
    public static void main(String[] args) {
        // Provide the version string and the target directory path.
        downloadPaperJar("1.21", "/home/jesse/Desktop/network/proxy/plugins/sls/servers/sls-paper");
    }
}

