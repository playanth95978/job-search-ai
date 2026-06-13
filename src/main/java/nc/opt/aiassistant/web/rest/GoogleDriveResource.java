package nc.opt.aiassistant.web.rest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import nc.opt.aiassistant.domain.Authority;
import nc.opt.aiassistant.security.SecurityUtils;
import nc.opt.aiassistant.service.gdrive.GoogleDriveAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/google-drive")
@Slf4j
public class GoogleDriveResource {

    private final GoogleDriveAuthService authService;
    private final JwtEncoder jwtEncoder;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds:0}")
    private long tokenValidityInSeconds;

    public GoogleDriveResource(GoogleDriveAuthService authService, JwtEncoder jwtEncoder) {
        this.authService = authService;
        this.jwtEncoder = jwtEncoder;
    }

    @GetMapping("/oauth/login")
    public ResponseEntity<Void> login() {
        String authUrl = authService.buildAuthorizationUrl();
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, authUrl).build();
    }

    @PostMapping("/oauth/google")
    public Mono<ResponseEntity<AuthenticateController.JWTToken>> authorizeGoogle(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return authService
            .exchangeCodeForTokens(code)
            .flatMap(tokens -> {
                String accessToken = (String) tokens.get("access_token");
                return authService.getUserInfo(accessToken).map(authService::getOrCreateUser);
            })
            .map(user -> {
                String authorities = user.getAuthorities().stream().map(Authority::getName).collect(Collectors.joining(" "));

                var now = Instant.now();
                Instant validity = now.plus(this.tokenValidityInSeconds, ChronoUnit.SECONDS);

                JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                    .issuedAt(now)
                    .expiresAt(validity)
                    .subject(user.getLogin())
                    .claim(SecurityUtils.AUTHORITIES_CLAIM, authorities)
                    .claim(SecurityUtils.USER_ID_CLAIM, user.getId());

                JwsHeader jwsHeader = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
                String jwt = this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, builder.build())).getTokenValue();

                var httpHeaders = new HttpHeaders();
                httpHeaders.setBearerAuth(jwt);
                return new ResponseEntity<>(new AuthenticateController.JWTToken(jwt), httpHeaders, HttpStatus.OK);
            });
    }
}
