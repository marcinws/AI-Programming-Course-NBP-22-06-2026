package pl.nbp.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.nbp.copilot.support.ImageProperties;
import pl.nbp.copilot.support.PolicyProperties;
import pl.nbp.copilot.support.SessionProperties;

@SpringBootApplication
@EnableConfigurationProperties({ImageProperties.class, SessionProperties.class, PolicyProperties.class})
@EnableScheduling
public class HardwareServiceCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(HardwareServiceCopilotApplication.class, args);
    }
}
