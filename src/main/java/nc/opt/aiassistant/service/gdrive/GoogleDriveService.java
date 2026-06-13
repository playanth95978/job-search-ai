package nc.opt.aiassistant.service.gdrive;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GoogleDriveService {

    private final WebClient webClient;

    public GoogleDriveService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://www.googleapis.com").build();
    }

    public Mono<Map<String, Object>> getUserInfo(String accessToken) {
        return webClient
            .get()
            .uri("/oauth2/v2/userinfo")
            .headers(h -> h.setBearerAuth(accessToken))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
