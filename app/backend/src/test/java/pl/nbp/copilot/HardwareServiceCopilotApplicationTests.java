package pl.nbp.copilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Dymny test startowy — weryfikuje, że kontekst Spring Boot ładuje się poprawnie.
 * Uruchamiany przez TAC-005-01: {@code ./mvnw verify} na czystym scaffoldzie.
 */
@SpringBootTest
@ActiveProfiles("test")
class HardwareServiceCopilotApplicationTests {

    @Test
    void contextLoads() {
        // Weryfikacja: kontekst aplikacji uruchamia się bez błędów.
    }
}
