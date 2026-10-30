package jwtc.android.chess.links;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned, self-contained links. Fragments keep shared content out of server requests. */
public final class AppLink {
    public static final String HOME = "https://jwtc.nl/app/";
    public static final int MAX_LENGTH = 8000;
    public final String route;
    public final String content;
    public final int[] path;

    private AppLink(String route, String content, int[] path) {
        this.route = route;
        this.content = content;
        this.path = path.clone();
    }

    /** Lichess usernames: 2 to 30 letters, digits, underscores or hyphens. */
    public static boolean isLichessUsername(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{2,30}");
    }

    public static String position(String fen) {
        return build("position", "fen", fen, "");
    }

    public static String game(String pgn, int[] path) {
        StringBuilder selected = new StringBuilder();
        for (int index : path) {
            if (index < 0) {
                throw new IllegalArgumentException("Invalid move path");
            }
            if (selected.length() > 0) {
                selected.append('.');
            }
            selected.append(index);
        }
        return build("game", "pgn", pgn, "&path=" + selected);
    }

    public static String join(String ip) {
        return build("join", "host", ip, "");
    }

    public static String online(String user) {
        return build("online", "user", user, "");
    }

    private static String build(String route, String key, String value, String extra) {
        try {
            String url =
                    HOME
                            + route
                            + "/#v=1&"
                            + key
                            + "="
                            + URLEncoder.encode(value, "UTF-8").replace("+", "%20")
                            + extra;
            parse(url);
            return url;
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public static AppLink parse(String url) {
        if (url == null || url.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Link exceeds size limit");
        }
        try {
            URI uri = new URI(url);
            if (!"https".equals(uri.getScheme())
                    || !"jwtc.nl".equals(uri.getHost())
                    || uri.getPort() != -1
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null) {
                throw new IllegalArgumentException("Invalid link origin");
            }
            String route = uri.getRawPath();
            if ("/app".equals(route) || "/app/".equals(route)) {
                if (uri.getRawFragment() != null) {
                    throw new IllegalArgumentException("Unexpected home payload");
                }
                return new AppLink("home", "", new int[0]);
            }
            if (route == null || !route.matches("/app/(position|game|join|online)/?")) {
                throw new IllegalArgumentException("Unknown link route");
            }
            route = route.split("/")[2];
            Map<String, String> values = new LinkedHashMap<>();
            String fragment = uri.getRawFragment();
            if (fragment == null) {
                throw new IllegalArgumentException("Missing link payload");
            }
            for (String pair : fragment.split("&", -1)) {
                int equal = pair.indexOf('=');
                if (equal < 1) {
                    throw new IllegalArgumentException("Malformed link payload");
                }
                String key = pair.substring(0, equal);
                String value = URLDecoder.decode(pair.substring(equal + 1), "UTF-8");
                if (values.put(key, value) != null
                        || value.indexOf('\uFFFD') >= 0
                        || value.indexOf('\0') >= 0) {
                    throw new IllegalArgumentException("Invalid link parameter");
                }
            }
            if (!"1".equals(values.remove("v"))) {
                throw new IllegalArgumentException("Unsupported link version");
            }
            String key =
                    "position".equals(route)
                            ? "fen"
                            : "game".equals(route) ? "pgn" : "join".equals(route) ? "host" : "user";
            String content = values.remove(key);
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing link content");
            }
            int[] path = new int[0];
            if ("game".equals(route)) {
                String selected = values.remove("path");
                if (selected == null) {
                    throw new IllegalArgumentException("Missing move path");
                }
                if (!selected.isEmpty()) {
                    String[] indices = selected.split("\\.", -1);
                    path = new int[indices.length];
                    for (int i = 0; i < indices.length; i++) {
                        if (!indices[i].matches("[0-9]{1,10}")) {
                            throw new IllegalArgumentException("Invalid move path");
                        }
                        path[i] = Integer.parseInt(indices[i]);
                    }
                }
            } else if ("join".equals(route)) {
                if (!content.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}")) {
                    throw new IllegalArgumentException("Invalid IPv4 address");
                }
                for (String octet : content.split("\\.")) {
                    if (Integer.parseInt(octet) > 255
                            || (octet.length() > 1 && octet.startsWith("0"))) {
                        throw new IllegalArgumentException("Invalid IPv4 address");
                    }
                }
            } else if ("online".equals(route) && !isLichessUsername(content)) {
                throw new IllegalArgumentException("Invalid Lichess user ID");
            }
            if (!values.isEmpty()) {
                throw new IllegalArgumentException("Unexpected link parameter");
            }
            return new AppLink(route, content, path);
        } catch (java.net.URISyntaxException | java.io.UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Invalid link", e);
        }
    }
}
