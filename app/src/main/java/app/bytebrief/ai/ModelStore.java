package app.bytebrief.ai;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Layout of downloaded models inside app-private storage. A model counts as installed only if its
 * file has the exact expected size and a verification marker written after a successful SHA-256
 * check; a partial download lives in a separate {@code .part} file.
 */
public final class ModelStore {
    public enum State { NOT_DOWNLOADED, PARTIAL, INSTALLED }

    private final File dir;

    public ModelStore(File modelsDir) {
        this.dir = modelsDir;
    }

    public File dir() {
        return dir;
    }

    public File fileFor(ModelDescriptor m) {
        return new File(dir, m.fileName);
    }

    public File partFor(ModelDescriptor m) {
        return new File(dir, m.fileName + ".part");
    }

    File etagFor(ModelDescriptor m) {
        return new File(dir, m.fileName + ".part.etag");
    }

    private File markerFor(ModelDescriptor m) {
        return new File(dir, m.fileName + ".verified");
    }

    public State stateOf(ModelDescriptor m) {
        File f = fileFor(m);
        if (f.isFile() && f.length() == m.sizeBytes && markerMatches(m)) return State.INSTALLED;
        return partFor(m).isFile() && partFor(m).length() > 0 ? State.PARTIAL : State.NOT_DOWNLOADED;
    }

    public long partialBytes(ModelDescriptor m) {
        return partFor(m).isFile() ? partFor(m).length() : 0;
    }

    void markVerified(ModelDescriptor m) throws IOException {
        Files.write(markerFor(m).toPath(), (m.sha256 + "\n" + m.sizeBytes).getBytes(StandardCharsets.UTF_8));
    }

    private boolean markerMatches(ModelDescriptor m) {
        try {
            File marker = markerFor(m);
            return marker.isFile() && new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).equals(m.sha256 + "\n" + m.sizeBytes);
        } catch (IOException e) {
            return false;
        }
    }

    /** Removes the model, any partial download and the verification marker. */
    public void delete(ModelDescriptor m) {
        fileFor(m).delete();
        partFor(m).delete();
        etagFor(m).delete();
        markerFor(m).delete();
    }
}
