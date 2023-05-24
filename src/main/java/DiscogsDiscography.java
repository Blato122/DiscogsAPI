import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiscogsDiscography {
    final private static String token = "BjfsnFPCnBCxHYagoMgFfJHbRqHoTzRySoVBZIOy";

    private static String getContentsURL(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // https
        conn.setConnectTimeout(5000);
        conn.setRequestMethod("GET");
        if (!conn.getHeaderField("Content-Type").equals("application/json")) {
            System.err.println("connection failed - connect() - getHeaderField(): " + conn.getHeaderField("Content-Type"));
            return null;
        }
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            System.err.println("connection failed - connect() - getResponseCode(): " + conn.getResponseCode());
            return null;
        }

        String result = getContentsConn(conn);
        conn.disconnect();
        return result;
    }

    private static String getContentsConn(HttpURLConnection conn) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }

        reader.close();
        return result.toString();
    }

    public static String getArtistAddr(String artist) throws IOException {
        String searchAddr = "https://api.discogs.com/database/search?q={name}&type=artist&token={token}"
                .replace("{name}", artist)
                .replace("{token}", token);
        System.err.println("searchAddr   - " + searchAddr);
        URL url = new URL(searchAddr);

        String result = getContentsURL(url);
        if (result == null) return null;

        // parse JSON
        JSONObject searchRes = new JSONObject(result);
        String artistAddr = null;
        try {
            // JSONObject 0 is the best match
            String bestMatch = searchRes.getJSONArray("results").getJSONObject(0).getString("title");
            System.err.println("bestMatch    - " + bestMatch);
            artistAddr =  searchRes.getJSONArray("results").getJSONObject(0).getString("resource_url");
        } catch (JSONException e) {
            System.err.println("No matching artists");
            // artistAddr already null
        }

        return artistAddr;
    }

    public static String getDiscography(String artist) throws IOException {
        String artistAddr = getArtistAddr(artist);
        String releasesAddr = null;
        if (artistAddr == null) {
            return null;
        } else {
            releasesAddr = artistAddr.concat("/releases?sort=year&sort_order=asc");
        }

        System.err.println("artistAddr   - " + artistAddr);
        System.err.println("releasesAddr - " + releasesAddr);

        URL url = new URL(releasesAddr);

        String result = getContentsURL(url);
        if (result == null) return null;

        // parse JSON
        JSONObject discography = new JSONObject(result);
        JSONArray releases = discography.getJSONArray("releases");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < releases.length(); ++i) {
            String role = releases.getJSONObject(i).optString("role").trim();
            String title = releases.getJSONObject(i).optString("title").trim();
            int year = releases.getJSONObject(i).optInt("year");
            sb.append(role).append("\t").append(year).append("\t").append(title).append("\n");
        }

        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        String artist = "Pavlovs Dog";
        System.err.println("artist       - " + artist);
        String discography = getDiscography(artist.replaceAll("\\s+",""));
        if (discography != null) {
            System.out.println(discography);
        }
    }
}