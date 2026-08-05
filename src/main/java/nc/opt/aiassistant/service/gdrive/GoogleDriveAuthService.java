package nc.opt.aiassistant.service.gdrive;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.config.ApplicationProperties;
import nc.opt.aiassistant.domain.Authority;
import nc.opt.aiassistant.domain.User;
import nc.opt.aiassistant.repository.AuthorityRepository;
import nc.opt.aiassistant.repository.UserRepository;
import nc.opt.aiassistant.security.AuthoritiesConstants;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GoogleDriveAuthService {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String SCOPES = " https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile";

    private final ApplicationProperties.GoogleDrive config;
    private final WebClient webClient;
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    public GoogleDriveAuthService(
        ApplicationProperties applicationProperties,
        WebClient.Builder webClientBuilder,
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.config = applicationProperties.getGoogleDrive();
        this.webClient = webClientBuilder.build();
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public String buildAuthorizationUrl() {
        if (config.getClientId() == null || config.getRedirectUri() == null) {
            throw new IllegalStateException(
                "Google OAuth non configuré : GOOGLE_CLIENT_ID et GOOGLE_REDIRECT_URI doivent être définis en variables d'environnement"
            );
        }
        return (
            AUTH_URL +
            "?client_id=" +
            encode(config.getClientId()) +
            "&redirect_uri=" +
            encode(config.getRedirectUri()) +
            "&response_type=code" +
            "&scope=" +
            encode(SCOPES) +
            "&access_type=offline" +
            "&prompt=select_account"
        );
    }

    public Mono<Map<String, Object>> exchangeCodeForTokens(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        form.add("redirect_uri", config.getRedirectUri());
        form.add("grant_type", "authorization_code");

        return webClient
            .post()
            .uri(TOKEN_URL)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(form))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    public Mono<GoogleUser> getUserInfo(String accessToken) {
        return webClient
            .get()
            .uri(USERINFO_URL)
            .headers(h -> h.setBearerAuth(accessToken))
            .retrieve()
            .bodyToMono(GoogleUser.class);
    }

    public User getOrCreateUser(GoogleUser googleUser) {
        String login = googleUser.email().toLowerCase();

        // Look up by login first, then by email: a user with this email may already exist under a
        // different login (e.g. created via classic signup), and jhi_user.email is uniquely constrained
        // (ux_user_email), so creating a second row would violate it.
        return userRepository
            .findOneWithAuthoritiesByLogin(login)
            .or(() -> userRepository.findOneWithAuthoritiesByEmailIgnoreCase(login))
            .orElseGet(() -> {
                log.info("Creating new Google user: {}", login);
                User newUser = new User();
                newUser.setLogin(login);
                // Google users never sign in with a password, but the column requires a 60-char bcrypt
                // hash (@Size(min=60,max=60), not null) — set an unguessable random one.
                newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                newUser.setFirstName(googleUser.given_name());
                newUser.setLastName(googleUser.family_name());
                newUser.setEmail(login);
                newUser.setActivated(true);
                newUser.setLangKey("fr");

                authorityRepository
                    .findById(AuthoritiesConstants.USER)
                    .ifPresent(authority -> newUser.setAuthorities(Collections.singleton(authority)));

                return userRepository.save(newUser);
            });
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record GoogleUser(
        String id,
        String email,
        boolean verified_email,
        String name,
        String given_name,
        String family_name,
        String picture,
        String locale
    ) {}
}
