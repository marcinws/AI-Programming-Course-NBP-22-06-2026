package pl.nbp.copilot.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nbp.copilot.application.MetadataService;
import pl.nbp.copilot.web.dto.MetadataResponse;

/**
 * Exposes GET /api/metadata — drives form selectors and image constraints on the frontend.
 * ADR-001 §4/§5; ADR-002 §6.
 */
@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    /**
     * Returns available case types, equipment categories with Polish labels, and image constraints.
     *
     * @return 200 MetadataResponse
     */
    @GetMapping
    public ResponseEntity<MetadataResponse> getMetadata() {
        return ResponseEntity.ok(metadataService.getMetadata());
    }
}
