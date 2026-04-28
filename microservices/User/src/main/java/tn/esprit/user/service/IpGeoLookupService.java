package tn.esprit.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.esprit.user.util.ClientIpResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Géolocalisation IP via <a href="http://ip-api.com">ip-api.com</a> (gratuit, ~45 req/min).
 */
@Service
public class IpGeoLookupService {

    private static final Logger log = LoggerFactory.getLogger(IpGeoLookupService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public IpGeoLookupService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<GeoPoint> lookup(String ip) {
        if (ClientIpResolver.isPrivateOrLocal(ip)) {
            return Optional.empty();
        }
        String url = "http://ip-api.com/json/" + ip + "?fields=status,lat,lon,city,country";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200 || res.body() == null) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(res.body());
            if (!"success".equalsIgnoreCase(root.path("status").asText())) {
                return Optional.empty();
            }
            double lat = root.path("lat").asDouble(Double.NaN);
            double lon = root.path("lon").asDouble(Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                return Optional.empty();
            }
            String city = root.path("city").asText(null);
            String country = root.path("country").asText(null);
            return Optional.of(new GeoPoint(lat, lon, city, country));
        } catch (Exception e) {
            log.debug("ip-api lookup failed for {}: {}", ip, e.getMessage());
            return Optional.empty();
        }
    }

    public record GeoPoint(double latitude, double longitude, String city, String country) {}
}
