package app.bytebrief.ai;

/** A downloadable local model with everything the user needs to decide, and everything needed to verify it. */
public final class ModelDescriptor {
    public final String id;
    public final String name;
    public final String publisher;
    public final String parameters;
    public final String quantization;
    public final String fileName;
    public final long sizeBytes;
    public final String sha256;
    public final String downloadUrl;
    public final String license;
    public final String licenseUrl;
    public final String sourceUrl;
    public final int contextTokens;
    /** Minimum total device RAM (MiB) we recommend; a guideline, not a guarantee. */
    public final int recommendedRamMiB;
    public final String notes;

    public ModelDescriptor(String id, String name, String publisher, String parameters, String quantization,
                           String fileName, long sizeBytes, String sha256, String downloadUrl, String license,
                           String licenseUrl, String sourceUrl, int contextTokens, int recommendedRamMiB, String notes) {
        this.id = id;
        this.name = name;
        this.publisher = publisher;
        this.parameters = parameters;
        this.quantization = quantization;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.downloadUrl = downloadUrl;
        this.license = license;
        this.licenseUrl = licenseUrl;
        this.sourceUrl = sourceUrl;
        this.contextTokens = contextTokens;
        this.recommendedRamMiB = recommendedRamMiB;
        this.notes = notes;
    }
}
