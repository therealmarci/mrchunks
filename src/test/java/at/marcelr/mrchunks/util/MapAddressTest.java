package at.marcelr.mrchunks.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapAddressTest {

    private static String build(String address, boolean proxy, String webAddress, String serverIp, String publicIp) {
        return MapAddress.build(address, proxy, webAddress, serverIp, publicIp, 8080);
    }

    @Test
    void fqdnWithoutProxyGetsPort() {
        assertEquals("http://map.example.com:8080/", build("map.example.com", false, "", "", ""));
    }

    @Test
    void fqdnBehindReverseProxyUsesHttpsWithoutPort() {
        assertEquals("https://map.example.com/", build("map.example.com", true, "", "", ""));
    }

    @Test
    void fullUrlIsUsedAsWritten() {
        assertEquals("https://example.com/karte/", build("https://example.com/karte/", false, "", "1.2.3.4", ""));
        assertEquals("https://example.com/karte/", build("https://example.com/karte", true, "", "", ""));
    }

    @Test
    void hostWithOwnPortKeepsIt() {
        assertEquals("http://map.example.com:25580/", build("map.example.com:25580", false, "", "", ""));
    }

    @Test
    void automaticPrefersChangedSquaremapAddress() {
        assertEquals("https://map.example.com/", build("", false, "https://map.example.com", "1.2.3.4", "5.6.7.8"));
    }

    @Test
    void automaticIgnoresSquaremapDefaultAndUsesServerIp() {
        assertEquals("http://1.2.3.4:8080/", build("", false, "http://localhost:8080", "1.2.3.4", "5.6.7.8"));
    }

    @Test
    void automaticFallsBackToPublicIp() {
        assertEquals("http://5.6.7.8:8080/", build("", false, "http://localhost:8080", "", "5.6.7.8"));
        assertEquals("http://5.6.7.8:8080/", build("", false, "", "127.0.0.1", "5.6.7.8"));
        assertEquals("http://5.6.7.8:8080/", build("", false, "", "0.0.0.0", "5.6.7.8"));
    }

    @Test
    void nothingKnown() {
        assertEquals("", build("", false, "http://localhost:8080", "", ""));
    }

    @Test
    void ipv6GetsBrackets() {
        assertEquals("http://[2001:db8::1]:8080/", build("2001:db8::1", false, "", "", ""));
    }
}
