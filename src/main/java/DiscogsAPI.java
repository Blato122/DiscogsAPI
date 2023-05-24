import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscogsAPI {
    final private static String tokenFile = "token.txt";

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

    private static String getToken() throws IOException {
        File file = new File(tokenFile);
        if(!file.exists()) {
            file.createNewFile();
            System.err.println("token.txt created - insert your token there");
            System.exit(1);
        }

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String token = reader.readLine();
        reader.close();
        return token;
    }

    public static String getArtistAddr(String artist) throws IOException {
        artist = artist.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", "");
        String searchAddr = "https://api.discogs.com/database/search?q={name}&type=artist&token={token}"
                .replace("{name}", artist)
                .replace("{token}", getToken());
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
        artist = artist.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", "");
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
        if (result == null) {
            return null;
        }

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

    public static List<String> getActiveGroupMembers(String group) throws IOException {
        String groupAddr = getArtistAddr(group);
        if (groupAddr == null) {
            return null;
        }

        System.err.println("groupAddr    - " + groupAddr);

        URL url = new URL(groupAddr);
        String result = getContentsURL(url);
        if (result == null) {
            return null;
        }

        // parse JSON
        JSONObject groupInfo = new JSONObject(result);
        JSONArray members = null;
        try {
            members = groupInfo.getJSONArray("members");
        } catch (JSONException e) {
            System.err.println("getActiveGroupMembers() - not a group!");
            System.exit(1);
        }

        List<String> activeMembers = new ArrayList<>();
        boolean isActive;
        String name;

        for (int i = 0; i < members.length(); ++i) {
            isActive = members.getJSONObject(i).optBoolean("active");
            if (isActive) {
                name = members.getJSONObject(i).optString("name");
                activeMembers.add(name);
            }
        }

        return activeMembers;
    }

    public static Map<String, List<String>> getArtistsCommonGroups(List<String> artists) throws IOException {
        if (artists.size() <= 1) {
            System.err.println("getArtistsCommonGroups() - not a group!");
        }

        Map<String, List<String>> map = new HashMap<>();
        Map<String, List<String>> invMap = new HashMap<>(); // inverted: key = artist, value = groups
        // List<String> commonGroups = new ArrayList<>();

        for (String artist : artists) {
            String artistAddr = getArtistAddr(artist.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", ""));
            if (artistAddr == null) {
                continue;
            }
            URL url = new URL(artistAddr);
            String result = getContentsURL(url);
            if (result == null) {
                continue;
            }

            // parse JSON
            JSONObject artistInfo = new JSONObject(result);
            JSONArray groups;
            try {
                groups = artistInfo.optJSONArray("groups");
            } catch (JSONException e) {
                continue;
            }

            // ...
            List<String> tmpList = new ArrayList<>();
            String groupName = null;
            for (int i = 0; i < groups.length(); ++i) {
                groupName = groups.getJSONObject(i).getString("name");
                tmpList.add(groupName);
                for (var entry : invMap.entrySet()) {
                    if (entry.getValue().contains(groupName)) {
                        List<String> tmp = new ArrayList<>();
                        tmp.add(artist);
                        tmp.add(entry.getKey());

                        map.put(groupName, tmp);
                    }
                }
            }
            invMap.put(artist, tmpList);
        }
        System.out.println(invMap);
        System.out.println(map);

        return map;
    }

    private static void printArtistsCommonGroups(String group, List<String> artists) {
        System.out.print(group + ": ");
        String allArtists = String.join(", ", artists); // zamiast streama
        System.out.println(allArtists);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("pass artist name as an argument");
            System.exit(1);
        }
        String name = args[0];

        String discography = getDiscography(name);
        if (discography != null) {
            System.out.println(discography);
        }

        // var czy zrobić z tego arraylist i hashmap???
        var members = getActiveGroupMembers(name);
        var membersInfo = getArtistsCommonGroups(members);
        membersInfo.forEach(DiscogsAPI::printArtistsCommonGroups); // lambda (k, v) -> print(k, v);
        // moze dac w takim razie do wyboru gdzie to wypisac? jako 3 argument

        // haszmapy arraylisty to nie problem?
        // nie użyje się tego poza javą... (xd)

        //jest sens zwracac te interfejsy..? tylko komplikuje to wszystko
        System.exit(0);
    }
}