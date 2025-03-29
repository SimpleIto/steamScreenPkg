package org.simpleito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.*;

public class SteamScreenshotOrganizer {
    private static final String APP_JSON_PATH = "app.json";
    private static final String STEAM_API_URL = "https://api.steampowered.com/ISteamApps/GetAppList/v2/";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Scanner scanner = new Scanner(System.in);
    private static final double BYTES_PER_MB = 1024 * 1024;

    public static void main(String[] args) {
        try {
            Map<String, String> appIdToName = loadOrDownloadAppData();
            int folderType = getFolderType();
            organizeScreenshots(appIdToName, folderType);
            System.out.println("\nOrganization completed!");
        } catch (Exception e) {
            handleError("An error occurred during program execution: " + e.getMessage(), e);
        } finally {
            waitForKeyPress();
        }
    }

    private static Map<String, String> loadOrDownloadAppData() throws IOException {
        File appJsonFile = new File(APP_JSON_PATH);
        if (!appJsonFile.exists()) {
            System.out.println("app.json file not found. Downloading data from Steam API...");
            try {
                downloadAppData();
            } catch (Exception e) {
                handleError("Unable to download data from Steam API: " + e.getMessage(), e);
            }
        }

        try {
            return loadAppData(appJsonFile);
        } catch (Exception e) {
            System.out.println("Consider delete app.json and try again.");
            throw e;
        }
    }

    private static void downloadAppData() throws IOException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(STEAM_API_URL))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            throw new IOException("Failed to send request to Steam API: " + e.getMessage(), e);
        }

        try (InputStream in = response.body();
             FileOutputStream fos = new FileOutputStream(APP_JSON_PATH)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            long lastOutputTime = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                long currentTime = System.currentTimeMillis();
                if (currentTime - lastOutputTime >= 2500) {
                    System.out.printf("Downloaded: %.2f MB%n", totalBytesRead / BYTES_PER_MB);
                    lastOutputTime = currentTime;
                }
            }

            System.out.printf("app.json download completed. Total size: %.2f MB%n", totalBytesRead / BYTES_PER_MB);

            // 验证下载的数据是否是有效的JSON
            try (FileReader reader = new FileReader(APP_JSON_PATH)) {
                objectMapper.readTree(reader);
            } catch (Exception e) {
                // 如果不是有效的JSON，删除文件并抛出异常
                Files.deleteIfExists(Path.of(APP_JSON_PATH));
                throw new IOException("Downloaded data is not valid JSON", e);
            }
        } catch (Exception e) {
            // 如果下载过程中出错，确保删除可能不完整的文件
            Files.deleteIfExists(Path.of(APP_JSON_PATH));
            throw new IOException("Failed to download app data: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> loadAppData(File appJsonFile) throws IOException {
        if (!appJsonFile.exists() || !appJsonFile.canRead()) {
            throw new IOException("Cannot read app.json file");
        }

        JsonNode root = objectMapper.readTree(appJsonFile);
        JsonNode apps = root.path("applist").path("apps");

        if (!apps.isArray()) {
            throw new IOException("Invalid app.json format: 'apps' is not an array");
        }
        Map<String, String> appIdToName = new HashMap<>();

        for (JsonNode app : apps) {
            if (!app.has("appid") || !app.has("name")) {
                continue; // Skip invalid entries
            }

            String appId = app.path("appid").asText();
            String name = app.path("name").asText().trim();

            // Only add valid entries
            if (isValidAppId(appId) && !name.isEmpty()) {
                // Sanitize game name for use as directory name
                name = sanitizeDirectoryName(name);
                appIdToName.put(appId, name);
            }
        }

        return appIdToName;
    }

    static int unknownSec = 1;

    private static String sanitizeDirectoryName(String name) {
        // Remove or replace invalid characters for directory names
        String r = name.replaceAll("[\\\\/:*?\"<>|]", "_")  // Replace invalid Windows filename chars
                .replaceAll("\\s+", " ")               // Normalize whitespace
                .trim();

        if (r.isBlank()) {
            return "Empty GameName " + unknownSec++;
        }
        return r;
    }

    private static int getFolderType() {
        while (true) {
            try {
                System.out.println("\nPlease select the screenshot folder type:");
                System.out.println("1: Screenshots are directly in the current directory (format: appid_timestamp_sequence.png)");
                System.out.println("2: Screenshots are in the 'screenshots' subdirectory of each game");
                System.out.print("Enter your choice (1 or 2): ");
                String input = scanner.nextLine();
                int type = Integer.parseInt(input);
                if (type == 1 || type == 2) {
                    return type;
                }
                System.out.println("Invalid input. Please enter 1 or 2");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number: 1 or 2");
            }
        }
    }

    private static void organizeScreenshots(Map<String, String> appIdToName, int folderType) throws IOException {
        Path currentDir = Paths.get(".");
        ExecutorService executor = ForkJoinPool.commonPool();

        try {
            if (folderType == 1) {
                organizeTypeOne(currentDir, appIdToName, executor);
            } else {
                organizeTypeTwo(currentDir, appIdToName, executor);
            }

            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                System.out.println("Warning: Some file processing timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("File processing was interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean isValidAppId(String appId) {
        return appId != null && appId.matches("\\d+");
    }

    private static boolean isValidSteamScreenshotName(String fileName) {
        // Check if filename matches pattern: appid_timestamp_sequence.extension
        return fileName.matches("\\d+_\\d+_\\d+\\.(png|jpg|jpeg|avif)");
    }

    private static String defaultIfBlank(String str, String def) {
        return str == null || str.isBlank() ? def : str;
    }

    private static void organizeTypeOne(Path currentDir, Map<String, String> appIdToName, ExecutorService executor) throws IOException {
        // Create a filter for image files
        DirectoryStream.Filter<Path> filter = file -> {
            String fileName = file.getFileName().toString();
            // Only process regular files that are images and match Steam screenshot pattern
            return Files.isRegularFile(file) &&
                    isValidSteamScreenshotName(fileName);
        };

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir, filter)) {
            for (Path file : stream) {
                CompletableFuture.runAsync(() -> {
                    try {
                        String fileName = file.getFileName().toString();
                        String[] parts = fileName.split("_");
                        String appId = parts[0];

                        // Additional safety check for appId
                        if (isValidAppId(appId)) {
                            String gameName = defaultIfBlank(appIdToName.get(appId), appId);
                            Path targetDir = currentDir.resolve(gameName);

                            moveFile(file, targetDir, fileName);
                        } else {
                            System.err.println("Skipped file with invalid appId: " + fileName);
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing file " + file + ": " + e.getMessage());
                    }
                }, executor);
            }
        }
    }

    private static void organizeTypeTwo(Path currentDir, Map<String, String> appIdToName, ExecutorService executor) throws IOException {
        // Create a filter for valid Steam game directories
        DirectoryStream.Filter<Path> filter = dir -> {
            if (!Files.isDirectory(dir) || Files.isHidden(dir)) {
                return false;
            }
            String dirName = dir.getFileName().toString();
            return isValidAppId(dirName);
        };

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir, filter)) {
            Path pkgDir = currentDir.resolve("screenPacks");
            Files.createDirectories(pkgDir);

            for (Path dir : stream) { // dir 外层文件夹，比如 123456
                CompletableFuture.runAsync(() -> {
                    try {
                        String dirName = dir.getFileName().toString();
                        Path screenshotsDir = dir.resolve("screenshots");
                        // 不管外层文件夹名如何，只要子文件夹名为screenshots，就尝试
                        if (Files.exists(screenshotsDir) && Files.isDirectory(screenshotsDir)) {
                            String gameName = defaultIfBlank(appIdToName.get(dirName), dirName);

                            Path targetDir = pkgDir.resolve(gameName);
                            try (DirectoryStream<Path> screenshots = Files.newDirectoryStream(screenshotsDir)) {
                                for (Path screenshot : screenshots) {
                                    moveFile(screenshot, targetDir, screenshot.getFileName().toString());
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing directory " + dir + ": " + e.getMessage());
                    }
                }, executor);
            }
        }
    }

    private static void moveFile(Path source, Path targetDir, String fileName) throws IOException {
        try {
            // Create target directory if it doesn't exist
            Files.createDirectories(targetDir);

            // Final safety check for target directory
            if (!Files.isDirectory(targetDir)) {
                throw new IOException("Target is not a directory: " + targetDir);
            }

            // Move the file
            Files.move(source, targetDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            System.out.printf("Moved: %s -> %s%n", fileName, targetDir.getFileName());
        } catch (IOException e) {
            throw new IOException("Failed to move file: " + source + " -> " + targetDir, e);
        }
    }

    private static void handleError(String message, Exception e) {
        System.err.println("\nError: " + message);
        if (e != null && e.getMessage() != null) {
            System.err.println("Details: " + e.getMessage());
        }
    }

    private static void waitForKeyPress() {
        System.out.println("\nPress Enter to exit...");
        try {
            System.in.read();
        } catch (IOException e) {
            // Ignore exception
        }
    }
}