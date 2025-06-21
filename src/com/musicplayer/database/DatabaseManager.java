package src.com.musicplayer.database;

import src.com.musicplayer.model.Song;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    public DatabaseManager() {
    }

    /**
     * Gets the robust database path in the user's home directory.
     * Creates the directory structure if it doesn't exist.
     * 
     * @return The absolute path to the database file
     */
    private String getDatabasePath() {
        // Get the user's home directory (e.g., "C:/Users/YourName")
        String userHome = System.getProperty("user.home");
        // Create a path for our application data folder and the database file
        // Using a hidden folder (starting with a dot) is a common convention
        Path dbPath = Paths.get(userHome, ".HarmonyMusicPlayer", "musicplayer.db");

        // IMPORTANT: Ensure the parent directory exists.
        // If it doesn't, this will create it.
        try {
            Files.createDirectories(dbPath.getParent());
            System.out.println("Database will be stored at: " + dbPath.toString());
        } catch (IOException e) {
            System.err.println("Could not create database directory: " + e.getMessage());
            e.printStackTrace();
        }

        // Return the full, absolute path to the database file
        return dbPath.toString();
    }

    /**
     * Creates a database connection using the robust path.
     * 
     * @return Connection object or null if connection fails
     */
    public Connection connect() {
        Connection conn = null;
        try {
            // The new, robust database URL using the correct path
            String dbUrl = "jdbc:sqlite:" + getDatabasePath();
            conn = DriverManager.getConnection(dbUrl);
            System.out.println("Database connection established successfully.");
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
        return conn;
    }

    /**
     * Initializes the database and creates the Songs table if it doesn't exist.
     */
    public void initializeDatabase() {
        try (Connection conn = connect();
                Statement stmt = conn.createStatement()) {

            // Create Songs table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS Songs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "title VARCHAR(255) NOT NULL, " +
                            "artist VARCHAR(255), " +
                            "album VARCHAR(255), " +
                            "file_path VARCHAR(512) NOT NULL UNIQUE, " +
                            "added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Add index on file_path for faster lookups
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_songs_file_path ON Songs(file_path)");

            System.out.println("Database initialized successfully.");

        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Enhanced version of loadPlaylist that filters out invalid files automatically
     * 
     * @return A list of valid Song objects only
     */
    public List<Song> loadValidPlaylist() {
        List<Song> playlist = new ArrayList<>();
        List<String> invalidPaths = new ArrayList<>();
        String sql = "SELECT title, artist, album, file_path FROM Songs ORDER BY title ASC";

        try (Connection conn = connect();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String filePath = rs.getString("file_path");
                File file = new File(filePath);

                if (file.exists() && file.canRead()) {
                    Song song = new Song(
                            rs.getString("title"),
                            rs.getString("artist"),
                            rs.getString("album"),
                            filePath);
                    playlist.add(song);
                } else {
                    invalidPaths.add(filePath);
                    System.out.println("Found invalid file path, will be removed: " + filePath);
                }
            }

            // Remove invalid paths from database
            if (!invalidPaths.isEmpty()) {
                String deleteSql = "DELETE FROM Songs WHERE file_path = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                    conn.setAutoCommit(false);
                    for (String invalidPath : invalidPaths) {
                        pstmt.setString(1, invalidPath);
                        pstmt.executeUpdate();
                    }
                    conn.commit();
                    System.out.println(
                            "Automatically removed " + invalidPaths.size() + " invalid entries from database.");
                }
            }

            System.out.println("Loaded " + playlist.size() + " valid songs from database.");

        } catch (SQLException e) {
            System.err.println("Error loading playlist from database: " + e.getMessage());
            e.printStackTrace();
        }
        return playlist;
    }

    /**
     * Adds a list of music files to the database, extracting title from filename.
     * 
     * @param files Array of File objects representing the music files.
     * @return The number of songs successfully added to the database.
     */
    public int addSongsToDatabase(File[] files) {
        String sql = "INSERT INTO Songs (title, artist, album, file_path) VALUES (?, ?, ?, ?)";
        int songsAddedCount = 0;

        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (File file : files) {
                // Check if file exists and is readable
                if (!file.exists() || !file.canRead()) {
                    System.err.println("File not accessible (skipped): " + file.getAbsolutePath());
                    continue;
                }

                String fileName = file.getName();
                String songTitle = fileName.substring(0,
                        fileName.lastIndexOf('.') > 0 ? fileName.lastIndexOf('.') : fileName.length());

                try {
                    pstmt.setString(1, songTitle);
                    pstmt.setString(2, "Unknown Artist");
                    pstmt.setString(3, "Unknown Album");
                    pstmt.setString(4, file.getAbsolutePath());
                    pstmt.executeUpdate();
                    songsAddedCount++;
                    System.out.println("Added song: " + songTitle + " from " + file.getAbsolutePath());
                } catch (SQLException e) {
                    if (e.getErrorCode() == 19 && e.getMessage().contains("UNIQUE constraint failed")) {
                        System.err.println("Song already exists in database (skipped): " + file.getAbsolutePath());
                    } else {
                        System.err.println("Error adding song '" + songTitle + "' to database: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }

            conn.commit();
            System.out.println(songsAddedCount + " song(s) added to database successfully.");

        } catch (SQLException e) {
            System.err.println("Error during batch song insertion: " + e.getMessage());
            e.printStackTrace();
        }
        return songsAddedCount;
    }

    /**
     * Validates all songs in the database and removes entries for files that no
     * longer exist.
     * 
     * @return The number of invalid entries removed
     */
    public int cleanupInvalidSongs() {
        List<Song> allSongs = loadValidPlaylist();
        int removedCount = 0;
        String deleteSql = "DELETE FROM Songs WHERE file_path = ?";

        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {

            conn.setAutoCommit(false);

            for (Song song : allSongs) {
                File file = new File(song.getFilePath());
                if (!file.exists() || !file.canRead()) {
                    try {
                        pstmt.setString(1, song.getFilePath());
                        pstmt.executeUpdate();
                        removedCount++;
                        System.out.println("Removed invalid song from database: " + song.getTitle());
                    } catch (SQLException e) {
                        System.err.println("Error removing invalid song: " + e.getMessage());
                    }
                }
            }

            conn.commit();
            System.out.println("Database cleanup completed. Removed " + removedCount + " invalid entries.");

        } catch (SQLException e) {
            System.err.println("Error during database cleanup: " + e.getMessage());
            e.printStackTrace();
        }

        return removedCount;
    }

    /**
     * Checks if a file path exists in the database and is still valid
     * 
     * @param filePath The file path to check
     * @return true if the file exists both in database and filesystem
     */
    public boolean isValidSongPath(String filePath) {
        String sql = "SELECT COUNT(*) FROM Songs WHERE file_path = ?";

        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, filePath);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next() && rs.getInt(1) > 0) {
                File file = new File(filePath);
                return file.exists() && file.canRead();
            }

        } catch (SQLException e) {
            System.err.println("Error checking song path validity: " + e.getMessage());
        }

        return false;
    }
}