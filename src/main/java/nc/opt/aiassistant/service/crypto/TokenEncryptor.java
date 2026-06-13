//package nc.opt.aiassistant.service.crypto;
//
//import lombok.extern.slf4j.Slf4j;
//import nc.opt.aiassistant.config.ApplicationProperties;
//import org.springframework.security.crypto.encrypt.Encryptors;
//import org.springframework.security.crypto.encrypt.TextEncryptor;
//import org.springframework.stereotype.Service;
//
///**
// * Encrypts / decrypts the user's GitHub OAuth access token before it is
// * persisted in the database. Uses Spring Security's {@link Encryptors#delux}
// * (AES with HMAC-SHA1 PRF, IV randomised per call) so that the encrypted
// * payload is never deterministic.
// *
// * <p>Configuration (under {@code application.github}):
// * <ul>
// *   <li>{@code token-encryption-password}: master password used to derive the
// *       AES key. Falls back to {@code client-secret} so dev/test environments
// *       keep working without extra ceremony — but production must provision a
// *       dedicated value.</li>
// *   <li>{@code token-encryption-salt}: hex-encoded salt for key derivation.
// *       A non-secret static default is provided.</li>
// * </ul>
// */
//@Service
//@Slf4j
//public class TokenEncryptor {
//
//    private final TextEncryptor encryptor;
//
//    public TokenEncryptor(ApplicationProperties properties) {
//        ApplicationProperties.Github github = properties.getGithub();
//        String password = github.getTokenEncryptionPassword();
//        if (password == null || password.isBlank()) {
//            password = github.getClientSecret();
//        }
//        if (password == null || password.isBlank()) {
//            log.warn(
//                "No application.github.token-encryption-password (and no client-secret) configured — " +
//                    "GitHub access tokens cannot be persisted. The application will run but repo selection will be unavailable."
//            );
//            this.encryptor = null;
//            return;
//        }
//        String salt = github.getTokenEncryptionSalt();
//        if (salt == null || salt.isBlank()) {
//            salt = "deadbeefcafef00d";
//        }
//        this.encryptor = Encryptors.delux(password, salt);
//    }
//
//    public boolean isReady() {
//        return encryptor != null;
//    }
//
//    public String encrypt(String plain) {
//        if (plain == null || plain.isBlank()) {
//            return null;
//        }
//        if (encryptor == null) {
//            throw new IllegalStateException("GitHubTokenEncryptor is not configured (missing encryption password)");
//        }
//        return encryptor.encrypt(plain);
//    }
//
//    public String decrypt(String cipher) {
//        if (cipher == null || cipher.isBlank()) {
//            return null;
//        }
//        if (encryptor == null) {
//            throw new IllegalStateException("GitHubTokenEncryptor is not configured (missing encryption password)");
//        }
//        return encryptor.decrypt(cipher);
//    }
//}
