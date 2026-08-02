package gg.vape.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Desktop;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Local, offline storage for Vape settings and client state. */
public final class VapeStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private VapeStorage() {
    }

    public static Path root() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.trim().isEmpty()) {
            appData = System.getProperty("user.home", ".");
        }
        return Paths.get(appData, "Vape");
    }

    public static Path settingsFile() {
        return root().resolve("settings.json");
    }

    public static Path profileFile(Profile profile) {
        return root().resolve(profileFileName(profile != null ? profile.getName() : null));
    }

    /**
     * Opens the local configuration directory in the Windows file explorer.
     * The directory is created first so the action also works on a fresh install.
     */
    public static boolean openFolder() {
        Path directory = root();
        try {
            Files.createDirectories(directory);
        }
        catch (IOException ignored) {
            return false;
        }

        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory.toFile());
                return true;
            }
        }
        catch (IOException | SecurityException | UnsupportedOperationException ignored) {
            // Fall through to the Windows explorer command below.
        }

        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("explorer.exe", directory.toAbsolutePath().toString()).start();
                return true;
            }
            catch (IOException | SecurityException ignored) {
                // The caller can safely ignore a failed open request.
            }
        }
        return false;
    }

    public static JsonObject loadSettings() {
        return readJsonObject(settingsFile());
    }

    /**
     * Loads every standalone profile JSON in the config directory. The main
     * settings.json file is intentionally excluded because it stores only
     * shared client settings after the per-profile migration.
     */
    public static JsonObject loadProfiles() {
        JsonObject profiles = new JsonObject();
        Path directory = root();
        if (!Files.isDirectory(directory)) {
            return profiles;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase(settingsFile().getFileName().toString()))
                    .sorted()
                    .forEach(path -> {
                        JsonObject profile = readJsonObject(path);
                        if (!isProfileJson(profile)) {
                            return;
                        }
                        String uuid = profileUuid(profile);
                        String key = uuid != null
                                ? uuid
                                : "file-" + path.getFileName().toString();
                        if (!profiles.has(key)) {
                            profiles.add(key, profile);
                        }
                    });
        }
        catch (IOException | RuntimeException ignored) {
            // A single unreadable file must not prevent the client from loading.
        }
        return profiles;
    }

    /**
     * Saves one JSON file per profile and removes stale profile JSON files.
     */
    public static void saveProfiles(Collection<Profile> profiles) throws IOException {
        Files.createDirectories(root());
        Set<String> activeFiles = new HashSet<String>();
        for (Profile profile : profiles) {
            if (profile == null || profile.getLocalId() == null) {
                continue;
            }
            saveProfile(profile);
            activeFiles.add(profileFile(profile).getFileName().toString().toLowerCase(Locale.ROOT));
        }
        removeStaleProfileFiles(activeFiles);
    }

    public static void saveProfile(Profile profile) throws IOException {
        if (profile == null || profile.getLocalId() == null) {
            return;
        }
        Path target = profileFile(profile);
        JsonObject serializedProfile = profile.toJson(false);
        removeProfileFilesForId(profile.getLocalId(), target);
        saveJson(target, serializedProfile);
    }

    /**
     * Deletes the JSON file for a profile, including an imported file whose
     * filename did not use the normal profile-name naming convention.
     */
    public static void deleteProfile(UUID localId) throws IOException {
        if (localId == null) {
            return;
        }
        Path directory = root();
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase(settingsFile().getFileName().toString()))
                    .forEach(path -> {
                        JsonObject profile = readJsonObject(path);
                        if (isProfileJson(profile) && hasUuid(profile, localId)) {
                            try {
                                Files.deleteIfExists(path);
                            }
                            catch (IOException ignored) {
                                // The next save will retry the cleanup.
                            }
                        }
                    });
        }
    }

    public static void saveSettings(JsonObject settings) throws IOException {
        saveJson(settingsFile(), settings);
    }

    private static void removeStaleProfileFiles(Set<String> activeFiles) throws IOException {
        Path directory = root();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase(settingsFile().getFileName().toString()))
                    .forEach(path -> {
                        JsonObject profile = readJsonObject(path);
                        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (isProfileJson(profile) && !activeFiles.contains(fileName)) {
                            try {
                                Files.deleteIfExists(path);
                            }
                            catch (IOException ignored) {
                                // The next save will retry the cleanup.
                            }
                        }
                    });
        }
    }

    private static void removeProfileFilesForId(UUID localId, Path keep) throws IOException {
        Path directory = root();
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase(settingsFile().getFileName().toString()))
                    .forEach(path -> {
                        JsonObject profile = readJsonObject(path);
                        if (isProfileJson(profile) && hasUuid(profile, localId)
                                && !path.toAbsolutePath().equals(keep.toAbsolutePath())) {
                            try {
                                Files.deleteIfExists(path);
                            }
                            catch (IOException ignored) {
                                // The next save will retry the cleanup.
                            }
                        }
                    });
        }
    }

    private static String profileFileName(String profileName) {
        String source = profileName == null || profileName.isEmpty() ? "profile" : profileName;
        StringBuilder sanitized = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); ++i) {
            char character = source.charAt(i);
            if (Character.isWhitespace(character)) {
                sanitized.append('_');
            }
            else if (character < 32 || "<>:\"/\\|?*".indexOf(character) >= 0) {
                sanitized.append('_');
            }
            else {
                sanitized.append(character);
            }
        }
        String baseName = sanitized.toString();
        while (baseName.endsWith(".")) {
            baseName = baseName.substring(0, baseName.length() - 1) + "_";
        }
        if (baseName.isEmpty() || baseName.equals(".") || baseName.equals("..")) {
            baseName = "profile";
        }
        String reservedName = baseName.toUpperCase(Locale.ROOT);
        if (reservedName.equals("CON") || reservedName.equals("PRN")
                || reservedName.equals("AUX") || reservedName.equals("NUL")
                || reservedName.matches("COM[1-9]") || reservedName.matches("LPT[1-9]")) {
            baseName = "profile_" + baseName;
        }
        if (baseName.equalsIgnoreCase("settings")) {
            baseName = "profile_settings";
        }
        if (baseName.length() > 120) {
            baseName = baseName.substring(0, 120);
        }
        return baseName + ".json";
    }

    private static boolean isProfileJson(JsonObject object) {
        return object != null
                && object.has("name")
                && object.has("data")
                && object.get("data") != null
                && object.get("data").isJsonObject();
    }

    private static boolean hasUuid(JsonObject object, UUID localId) {
        String uuid = profileUuid(object);
        return uuid != null && uuid.equalsIgnoreCase(localId.toString());
    }

    private static String profileUuid(JsonObject object) {
        if (object == null) {
            return null;
        }
        JsonElement uuid = object.get("uuid");
        if (uuid == null || uuid.isJsonNull() || !uuid.isJsonPrimitive()) {
            return null;
        }
        return uuid.getAsString();
    }

    private static JsonObject readJsonObject(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        }
        catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static void saveJson(Path file, JsonObject object) throws IOException {
        Path directory = file.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(object, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }
}
