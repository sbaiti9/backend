package esprit.subscription.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return stripPort(first);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return stripPort(realIp.trim());
        }
        return Optional.ofNullable(request.getRemoteAddr()).map(ClientIpResolver::stripPort).orElse("");
    }

    private static String stripPort(String host) {
        if (host.startsWith("[") && host.contains("]")) {
            return host.substring(1, host.indexOf(']'));
        }
        int colon = host.indexOf(':');
        if (colon > 0 && host.chars().filter(c -> c == ':').count() == 1) {
            return host.substring(0, colon);
        }
        return host;
    }

    public static boolean isPrivateOrLocal(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                    || addr.isAnyLocalAddress();
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
