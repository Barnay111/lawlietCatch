package kz.behaviorguard.backend.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/**
 * Dev-учётные данные пишутся в файл с правами 600, а не в лог: логи уходят в агрегаторы и чаты поддержки.
 */
public final class DevCredentialsFile {

    private DevCredentialsFile() {
    }

    public static Path write(List<String> lines) throws IOException {
        Path dir = Path.of(System.getProperty("user.home"), ".behaviorguard");
        Files.createDirectories(dir);
        Path file = dir.resolve("dev-credentials.txt");
        Files.write(file, lines);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // не POSIX-система: права задаёт ОС
        }
        return file;
    }
}
