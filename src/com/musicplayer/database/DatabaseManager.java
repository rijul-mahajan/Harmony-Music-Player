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
     * Loads all songs from the database.
     * 
     * @return A list of Song objects.
     */
    public List<Song> loadPlaylist() {
        List<Song> playlist = new ArrayList<>();
        String sql = "SELECT title, artist, album, file_path FROM Songs ORDER BY title ASC";

        try (Connection conn = connect();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Song song = new Song(
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("album"),
                        rs.getString("file_path"));
                playlist.add(song);
            }

            System.out.println("Loaded " + playlist.size() + " songs from database.");

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
}