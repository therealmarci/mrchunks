package at.marcelr.mrchunks.util;

import java.util.Locale;

/**
 * Builds the public address of the web map that players get from /chunkloader map.
 * <ol>
 *   <li>{@code address} with a scheme ("https://example.com/map/") is used exactly as written.</li>
 *   <li>{@code address} without a scheme is a host name or IP ("map.example.com").</li>
 *   <li>Empty: squaremap's own web-address if the admin changed it, otherwise the server-ip from
 *       server.properties, otherwise the detected public IP.</li>
 * </ol>
 * A host becomes {@code https://host/} behind a reverse proxy, otherwise {@code http://host:port/}
 * with squaremap's internal web server port.
 */
public final class MapAddress {

    /** squaremap's default web-address - useless for players, so it is ignored. */
    private static final String SQUAREMAP_DEFAULT = "http://localhost:8080";

    private MapAddress() {
    }

    /**
     * @param address             squaremap.address from the config ("" = automatic)
     * @param reverseProxy        squaremap.reverse-proxy
     * @param squaremapWebAddress settings.web-address from squaremap's config ("" if unknown)
     * @param serverIp            server-ip from server.properties ("" if not set)
     * @param publicIp            detected public IP ("" if not detected)
     * @param port                port of squaremap's internal web server
     * @return the address, or "" if none is known
     */
    public static String build(String address, boolean reverseProxy, String squaremapWebAddress,
                               String serverIp, String publicIp, int port) {
        String configured = clean(address);
        if (hasScheme(configured)) {
            return withSlash(configured);
        }
        String host = configured;
        if (host.isEmpty()) {
            String webAddress = clean(squaremapWebAddress);
            if (hasScheme(webAddress) && !isLocal(hostOf(webAddress))
                    && !webAddress.replaceAll("/+$", "").equalsIgnoreCase(SQUAREMAP_DEFAULT)) {
                return withSlash(webAddress);
            }
            host = usable(serverIp) ? clean(serverIp) : clean(publicIp);
        }
        if (host.isEmpty()) {
            return "";
        }
        host = host.replaceAll("/+$", "");
        if (host.contains(":") && !host.startsWith("[") && host.indexOf(':') != host.lastIndexOf(':')) {
            host = "[" + host + "]"; // IPv6
        }
        if (reverseProxy) {
            return "https://" + host + "/";
        }
        boolean hasPort = host.matches(".*:\\d+$");
        return "http://" + host + (hasPort || port == 80 ? "" : ":" + port) + "/";
    }

    /** A server-ip that players can reach (not empty, not a wildcard, not loopback). */
    public static boolean usable(String ip) {
        String value = clean(ip);
        return !value.isEmpty() && !value.equals("0.0.0.0") && !value.equals("::") && !isLocal(value);
    }

    private static boolean isLocal(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.startsWith("127.") || h.equals("::1") || h.equals("[::1]");
    }

    private static boolean hasScheme(String value) {
        return value.matches("(?i)^https?://.+");
    }

    private static String hostOf(String url) {
        String rest = url.replaceFirst("(?i)^https?://", "");
        int end = rest.indexOf('/');
        String hostPort = end < 0 ? rest : rest.substring(0, end);
        return hostPort.startsWith("[") ? hostPort : hostPort.replaceFirst(":\\d+$", "");
    }

    private static String withSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
