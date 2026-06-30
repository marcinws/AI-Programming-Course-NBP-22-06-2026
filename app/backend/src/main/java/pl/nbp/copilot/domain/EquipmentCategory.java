package pl.nbp.copilot.domain;

/**
 * Equipment category for the service case.
 * Each value carries a human-readable Polish label exposed via GET /api/metadata.
 * ADR-004 §4; TAC-004-06.
 */
public enum EquipmentCategory {

    SMARTPHONE("Smartfon"),
    TABLET("Tablet"),
    LAPTOP("Laptop"),
    DESKTOP_PC("Komputer stacjonarny"),
    MONITOR("Monitor"),
    TV("Telewizor"),
    HEADPHONES_AUDIO("Słuchawki / audio"),
    CAMERA("Aparat / kamera"),
    PRINTER("Drukarka"),
    NETWORKING("Sprzęt sieciowy"),
    SMARTWATCH_WEARABLE("Smartwatch / urządzenia ubieralne"),
    SMALL_APPLIANCE("Małe AGD"),
    OTHER("Inne");

    private final String labelPl;

    EquipmentCategory(String labelPl) {
        this.labelPl = labelPl;
    }

    /** Returns the Polish display label for this equipment category. */
    public String labelPl() {
        return labelPl;
    }
}
