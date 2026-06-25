package pl.nbp.copilot.application;

import org.springframework.stereotype.Service;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.support.ImageProperties;
import pl.nbp.copilot.web.dto.ImageConstraints;
import pl.nbp.copilot.web.dto.MetadataResponse;
import pl.nbp.copilot.web.dto.Option;

import java.util.Arrays;
import java.util.List;

/**
 * Assembles the metadata payload for GET /api/metadata.
 * Reads domain enums for options and image config from {@link ImageProperties}.
 * ADR-001 §4; ADR-002 §6.
 */
@Service
public class MetadataService {

    private final ImageProperties imageProperties;

    public MetadataService(ImageProperties imageProperties) {
        this.imageProperties = imageProperties;
    }

    public MetadataResponse getMetadata() {
        List<Option> caseTypes = Arrays.stream(CaseType.values())
                .map(ct -> new Option(ct.name(), caseTypeLabelPl(ct)))
                .toList();

        List<Option> equipmentCategories = Arrays.stream(EquipmentCategory.values())
                .map(ec -> new Option(ec.name(), ec.labelPl()))
                .toList();

        ImageConstraints imageConstraints = new ImageConstraints(
                imageProperties.acceptedTypes(),
                imageProperties.maxUploadBytes()
        );

        return new MetadataResponse(caseTypes, equipmentCategories, imageConstraints);
    }

    /** Returns the Polish label for a case type. */
    private static String caseTypeLabelPl(CaseType caseType) {
        return switch (caseType) {
            case COMPLAINT -> "Reklamacja";
            case RETURN -> "Zwrot";
        };
    }
}
