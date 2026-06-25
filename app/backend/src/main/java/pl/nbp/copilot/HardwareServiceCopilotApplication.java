package pl.nbp.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.nbp.copilot.support.ImageProperties;

@SpringBootApplication
@EnableConfigurationProperties(ImageProperties.class)
public class HardwareServiceCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(HardwareServiceCopilotApplication.class, args);
    }
}
