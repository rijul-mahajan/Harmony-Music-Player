package src.com.musicplayer;

// Import libraries
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.InputStream;

// Import classes
import src.com.musicplayer.model.Song;
import src.com.musicplayer.audio.AudioPlayer;
import src.com.musicplayer.database.DatabaseManager;
import src.com.musicplayer.ui.CustomButton;
import src.com.musicplayer.ui.CustomSlider;

public class MusicPlayer extends JFrame {

    // Main panels
    private JPanel mainPanel;
    private JPanel headerPanel;
    private JPanel centerPanel;
    private JPanel controlPanel;

    // Database
    private DatabaseManager databaseManager;

    // UI Components
    private JLabel titleLabel;
    private JLabel artistLabel;
    private JLabel albumArtLabel;
    private CustomButton playPauseButton;
    private CustomButton previousButton;
    private CustomButton nextButton;
    private CustomSlider seekBar;
    private CustomSlider volumeSlider;
    private JLabel currentTimeLabel;
    private JLabel totalTimeLabel;
    private JLabel volumeIcon;
    private ImageIcon cachedAlbumArt;
    private BufferedImage cachedNoteImage;

    // Music player components
    private AudioPlayer audioPlayer;
    private List<Song> playlist;
    private int currentSongIndex = 0;
    private boolean isPlaying = false;
    private Timer progressTimer;
    private JPanel sidebarPanel;
    private JList<Song> playlistView;
    private DefaultListModel<Song> playlistModel;
    private CustomButton shuffleButton;
    private CustomButton loopButton;
    private boolean isShuffling = false;
    private boolean isLooping = false;
    private List<Integer> shuffleOrder;
    private Random random;

    // Colors
    private static final Color BACKGROUND_COLOR = new Color(18, 18, 18);
    private static final Color HEADER_COLOR = new Color(24, 24, 24);
    public static final Color CONTROL_PANEL_COLOR = new Color(24, 24, 24);
    public static final Color TEXT_COLOR = new Color(230, 230, 230);
    private static final Color SECONDARY_TEXT_COLOR = new Color(179, 179, 179);
    public static final Color ACCENT_COLOR = new Color(30, 215, 96);
    public static final Color BUTTON_HOVER_COLOR = new Color(40, 40, 40);

    // Font
    private Font TITLE_FONT;
    private Font ARTIST_FONT;
    private Font REGULAR_FONT;

    public MusicPlayer() {
        setTitle("Music Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setMinimumSize(new Dimension(600, 500));
        setLocationRelativeTo(null);
        setUndecorated(true);

        // Set application icon
        try (InputStream is = getClass().getResourceAsStream("/images/icon.png")) {
            if (is != null) {
                setIconImage(ImageIO.read(is));
            } else {
                System.err.println("Application icon not found at /images/icon.png");
            }
        } catch (IOException e) {
            System.err.println("Error loading application icon: " + e.getMessage());
        }

        // Initialize DatabaseManager
        databaseManager = new DatabaseManager();

        // Initialize empty playlist and playlistModel
        playlist = new ArrayList<>();
        playlistModel = new DefaultListModel<>();

        // Initialize audio player
        audioPlayer = new AudioPlayer();

        // Initialize shuffle
        shuffleOrder = new ArrayList<>();
        random = new Random();

        // Load Inter font for all users
        loadInterFontFromResources();

        // Initialize font constants
        initializeFonts();

        // Initialize database
        databaseManager.initializeDatabase();

        // Load playlist from database
        refreshPlaylistFromDatabase();

        // Set up the layout
        setupUI();

        // Initialize current song after UI is ready
        initializeCurrentSong();

        // Make the frame draggable
        makeDraggable();

        // Set up timer for progress updates
        setupProgressTimer();

        // Make the frame visible
        setVisible(true);

        // Add window listener for proper cleanup
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                performCleanShutdown();
                System.exit(0);
            }
        });
    }

    private void cleanup() {
        if (progressTimer != null) {
            progressTimer.stop();
        }
        if (audioPlayer != null) {
            audioPlayer.dispose();
        }
    }

    private void loadInterFontFromResources() {
        try {
            // Load the font from the resources
            InputStream is = getClass().getResourceAsStream("/fonts/Inter-Regular.ttf");
            if (is == null) {
                System.err.println("Could not find font resource");
                return;
            }

            // Register the font
            Font interFont = Font.createFont(Font.TRUETYPE_FONT, is);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(interFont);

            System.out.println("Inter fonts loaded successfully!");
        } catch (IOException | FontFormatException e) {
            System.err.println("Error loading Inter font: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    private static Font INTER_REGULAR;

    private void initializeFonts() {
        try {
            // Try to create font objects with the loaded Inter font
            INTER_REGULAR = new Font("Inter", Font.PLAIN, 12);

            // Update the static font constants
            TITLE_FONT = new Font("Inter", Font.BOLD, 24);
            ARTIST_FONT = new Font("Inter", Font.PLAIN, 16);
            REGULAR_FONT = new Font("Inter", Font.PLAIN, 12);
        } catch (Exception e) {
            System.err.println("Error initializing Inter fonts: " + e.getMessage());
            e.printStackTrace();

            // Fallback to system fonts if Inter fails to load
            TITLE_FONT = new Font("Segoe UI", Font.BOLD, 24);
            ARTIST_FONT = new Font("Segoe UI", Font.PLAIN, 16);
            REGULAR_FONT = new Font("Segoe UI", Font.PLAIN, 12);
        }
    }

    private CustomButton createUploadButton() {
        // Create upload button with custom styling
        CustomButton uploadButton = new CustomButton(
                "<html><div style='margin-bottom: 5px'><span style='font-size:14px'>+</span> <span style='font-size:11px'>Add Songs</span></div></html>");
        uploadButton.setFont(REGULAR_FONT);
        uploadButton.setForeground(TEXT_COLOR);
        uploadButton.setPreferredSize(new Dimension(120, 30));
        uploadButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Add hover effects
        uploadButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                uploadButton.setBackground(ACCENT_COLOR);
            }
        });

        // Add action to open file chooser
        uploadButton.addActionListener(_ -> openFileChooser());

        return uploadButton;
    }

    private void refreshPlaylistFromDatabase() {
        SwingWorker<List<Song>, Void> worker = new SwingWorker<List<Song>, Void>() {
            @Override
            protected List<Song> doInBackground() throws Exception {
                // Use the new method that automatically cleans up invalid entries
                return databaseManager.loadValidPlaylist();
            }

            @Override
            protected void done() {
                try {
                    List<Song> songsFromDB = get();
                    int previousSize = playlist.size();
                    int previousIndex = currentSongIndex;

                    playlist.clear();
                    playlistModel.clear();
                    playlist.addAll(songsFromDB);

                    for (Song song : playlist) {
                        playlistModel.addElement(song);
                    }

                    // Handle playlist changes intelligently
                    if (!playlist.isEmpty() && playlistView != null) {
                        // If playlist size changed, validate current index
                        if (playlist.size() != previousSize) {
                            if (previousIndex >= playlist.size()) {
                                currentSongIndex = 0;
                            } else if (previousIndex < 0) {
                                currentSongIndex = 0;
                            } else {
                                // Try to maintain current song if possible
                                currentSongIndex = previousIndex;
                            }

                            // If the current song file no longer exists, reset to first song
                            if (currentSongIndex < playlist.size()) {
                                Song currentSong = playlist.get(currentSongIndex);
                                File currentFile = new File(currentSong.getFilePath());
                                if (!currentFile.exists()) {
                                    currentSongIndex = 0;
                                    isPlaying = false;
                                    playPauseButton.setText("▶");
                                    loadCurrentSong();
                                }
                            }
                        }

                        playlistView.setSelectedIndex(currentSongIndex);
                    } else if (playlist.isEmpty() && playlistView != null) {
                        currentSongIndex = -1;
                        isPlaying = false;
                        playPauseButton.setText("▶");
                        updateUIForEmptyPlaylist();
                    }

                    // Update shuffle order if shuffling is enabled
                    if (isShuffling && !playlist.isEmpty()) {
                        rebuildShuffleOrder();
                    }

                } catch (Exception e) {
                    System.err.println("Error loading playlist: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void refreshPlaylist() {
        // Show loading indicator
        titleLabel.setText("Refreshing playlist...");
        artistLabel.setText("Please wait");

        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                // Clean up invalid entries and get count
                return databaseManager.cleanupInvalidSongs();
            }

            @Override
            protected void done() {
                try {
                    int removedCount = get();

                    // Refresh the playlist
                    refreshPlaylistFromDatabase();

                    // Show notification if songs were removed
                    if (removedCount > 0) {
                        JOptionPane.showMessageDialog(MusicPlayer.this,
                                "Removed " + removedCount + " song(s) that no longer exist on disk.",
                                "Playlist Cleaned",
                                JOptionPane.INFORMATION_MESSAGE);
                    }

                } catch (Exception e) {
                    System.err.println("Error during playlist refresh: " + e.getMessage());
                    JOptionPane.showMessageDialog(MusicPlayer.this,
                            "Error refreshing playlist: " + e.getMessage(),
                            "Refresh Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // Add this helper method
    private void updateUIForEmptyPlaylist() {
        titleLabel.setText("No songs loaded");
        artistLabel.setText("Add songs to begin");
        seekBar.setValue(0);
        seekBar.setMaximum(0);
        currentTimeLabel.setText("0:00");
        totalTimeLabel.setText("0:00");
    }

    // Replace addFilesToPlaylist() method
    private void addFilesToPlaylist(File[] files) {
        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return databaseManager.addSongsToDatabase(files);
            }

            @Override
            protected void done() {
                try {
                    int songsAddedCount = get();

                    if (songsAddedCount > 0) {
                        refreshPlaylistFromDatabase();

                        if (playlist.size() == songsAddedCount && songsAddedCount > 0) {
                            currentSongIndex = 0;
                            loadCurrentSong();
                        }

                        JOptionPane.showMessageDialog(MusicPlayer.this,
                                "Added " + songsAddedCount + " song(s) to playlist.",
                                "Songs Added",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else if (files.length > 0 && songsAddedCount == 0) {
                        JOptionPane.showMessageDialog(MusicPlayer.this,
                                "No new songs were added. They might already exist in the playlist.",
                                "Songs Not Added",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MusicPlayer.this,
                            "Error adding songs: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void openFileChooser() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select MP3 Files");
        fileChooser.setMultiSelectionEnabled(true);

        // Set default directory to the user's music folder
        File musicFolder = new File(System.getProperty("user.home") + File.separator + "Music");
        File defaultDir = musicFolder.exists() ? musicFolder : new File(System.getProperty("user.home"));
        fileChooser.setCurrentDirectory(defaultDir);

        FileNameExtensionFilter filter = new FileNameExtensionFilter("MP3 Files", "mp3");
        fileChooser.setFileFilter(filter);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            if (selectedFiles.length > 0) {
                addFilesToPlaylist(selectedFiles);
            }
        }
    }

    private void setupUI() {
        // Set up main panel with BorderLayout
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BACKGROUND_COLOR);
        mainPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Add sidebar panel
        setupSidebarPanel();
        mainPanel.add(sidebarPanel, BorderLayout.WEST);

        // Add header panel
        setupHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Add center panel
        setupCenterPanel();
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Add control panel
        setupControlPanel();
        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        // Add main panel to frame
        setContentPane(mainPanel);
    }

    // Replace the setupSidebarPanel() method with this updated version:

    private void setupSidebarPanel() {
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BorderLayout(0, 0));
        sidebarPanel.setBackground(BACKGROUND_COLOR);
        sidebarPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        sidebarPanel.setPreferredSize(new Dimension(275, getHeight()));

        // Playlist title and buttons panel
        JPanel titlePanel = new JPanel(new BorderLayout(0, 0));
        titlePanel.setBackground(BACKGROUND_COLOR);
        titlePanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        // Left side components (label and upload button)
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setBackground(BACKGROUND_COLOR);

        JLabel playlistLabel = new JLabel("Playlist");
        playlistLabel.setFont(new Font("Inter", Font.BOLD, 18));
        playlistLabel.setForeground(TEXT_COLOR);
        playlistLabel.setBorder(new EmptyBorder(0, 0, 0, 20));
        CustomButton uploadButton = createUploadButton();

        leftPanel.add(playlistLabel);
        leftPanel.add(uploadButton);

        // Right side component (refresh button)
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setBackground(BACKGROUND_COLOR);
        rightPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        CustomButton refreshButton = new CustomButton("🔄");
        refreshButton.setToolTipText("Refresh Playlist");
        refreshButton.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 12));
        refreshButton.setForeground(TEXT_COLOR);
        refreshButton.setPreferredSize(new Dimension(45, 30));
        refreshButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        refreshButton.addActionListener(_ -> refreshPlaylist());

        rightPanel.add(refreshButton);

        // Add both panels to titlePanel
        titlePanel.add(leftPanel, BorderLayout.WEST);
        titlePanel.add(rightPanel, BorderLayout.EAST);

        // Playlist view
        playlistView = new JList<>(playlistModel);
        playlistView.setBackground(new Color(28, 28, 28));
        playlistView.setForeground(TEXT_COLOR);
        playlistView.setFont(REGULAR_FONT);
        playlistView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlistView.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !playlistModel.isEmpty()) {
                int selectedIndex = playlistView.getSelectedIndex();
                if (selectedIndex >= 0) { // Only update if valid index
                    currentSongIndex = selectedIndex;
                    loadCurrentSong();
                    if (isPlaying) {
                        audioPlayer.play();
                    }
                }
            }
        });

        // Custom cell renderer
        playlistView.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                JPanel cellPanel = new JPanel(new BorderLayout()) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if (isSelected || (index == playlistView.getLeadSelectionIndex() && !isSelected)) {
                            Graphics2D g2d = (Graphics2D) g.create();
                            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g2d.setColor(isSelected ? ACCENT_COLOR : new Color(40, 40, 40));
                            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                            g2d.dispose();
                        }
                    }
                };
                cellPanel.setOpaque(false);

                JLabel label = new JLabel();
                Song song = (Song) value;
                String title = song.getTitle();

                FontMetrics fm = label.getFontMetrics(REGULAR_FONT);
                int maxWidth = list.getWidth() - 30;
                if (maxWidth <= 0)
                    maxWidth = 200;
                String displayText = fm.stringWidth(title) > maxWidth
                        ? truncateWithEllipsis(title, fm, maxWidth)
                        : title;

                label.setText(displayText);
                label.setFont(REGULAR_FONT);
                label.setForeground(isSelected ? Color.BLACK : TEXT_COLOR);
                label.setOpaque(false);
                label.setBorder(new EmptyBorder(5, 5, 5, 10));

                cellPanel.add(label, BorderLayout.WEST);
                cellPanel.setForeground(isSelected ? Color.BLACK : TEXT_COLOR);

                return cellPanel;
            }

            private String truncateWithEllipsis(String text, FontMetrics fm, int maxWidth) {
                String ellipsis = "...";
                int ellipsisWidth = fm.stringWidth(ellipsis);
                if (fm.stringWidth(text) <= maxWidth)
                    return text;

                StringBuilder truncated = new StringBuilder();
                int availableWidth = maxWidth - ellipsisWidth;
                for (char c : text.toCharArray()) {
                    if (fm.stringWidth(truncated.toString() + c) <= availableWidth) {
                        truncated.append(c);
                    } else {
                        break;
                    }
                }
                return truncated.toString() + ellipsis;
            }
        });

        playlistView.setOpaque(false);
        playlistView.setBorder(new EmptyBorder(5, 5, 5, 5));

        JScrollPane scrollPane = new JScrollPane(playlistView);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel roundedContainer = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(28, 28, 28));
                g2d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                g2d.dispose();
            }
        };
        roundedContainer.setLayout(new BorderLayout());
        roundedContainer.setOpaque(false);
        roundedContainer.add(scrollPane, BorderLayout.CENTER);

        sidebarPanel.add(titlePanel, BorderLayout.NORTH);
        sidebarPanel.add(roundedContainer, BorderLayout.CENTER);
    }

    private void setupHeaderPanel() {
        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(HEADER_COLOR);
        headerPanel.setBorder(new EmptyBorder(10, 15, 10, 5));
        headerPanel.setPreferredSize(new Dimension(getWidth(), 50));

        // Logo/App name
        JLabel logoLabel = new JLabel("HARMONY");
        logoLabel.setFont(new Font("Inter", Font.BOLD, 18));
        logoLabel.setForeground(TEXT_COLOR);
        headerPanel.add(logoLabel, BorderLayout.WEST);

        // Window controls
        JPanel windowControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        windowControls.setBackground(HEADER_COLOR);

        // Maximize/Restore button
        JLabel maximizeButton = new JLabel("●");
        maximizeButton.setForeground(new Color(0, 202, 78));
        maximizeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        maximizeButton.setFont(new Font("Inter", Font.PLAIN, 18));
        maximizeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (getExtendedState() == JFrame.MAXIMIZED_BOTH) {
                    setExtendedState(JFrame.NORMAL);
                } else {
                    setExtendedState(JFrame.MAXIMIZED_BOTH);
                }
            }
        });

        // Minimize button
        JLabel minimizeButton = new JLabel("●");
        minimizeButton.setForeground(new Color(255, 189, 68));
        minimizeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        minimizeButton.setFont(new Font("Inter", Font.PLAIN, 18));
        minimizeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setState(JFrame.ICONIFIED);
            }
        });

        // Close button
        JLabel closeButton = new JLabel("●");
        closeButton.setForeground(new Color(255, 96, 92));
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.setFont(new Font("Inter", Font.PLAIN, 18));
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                cleanup();
                System.exit(0);
            }
        });

        windowControls.add(maximizeButton);
        windowControls.add(minimizeButton);
        windowControls.add(closeButton);

        headerPanel.add(windowControls, BorderLayout.EAST);
    }

    private void setupCenterPanel() {
        centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(BACKGROUND_COLOR);
        centerPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Album art placeholder
        ImageIcon albumArtIcon = createPlaceholderAlbumArt(300, 300);
        albumArtLabel = new JLabel(albumArtIcon);
        albumArtLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        albumArtLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        // Title and artist info
        String defaultTitle = playlist.isEmpty() ? "No songs loaded" : playlist.get(currentSongIndex).getTitle();
        String defaultArtist = playlist.isEmpty() ? "Add songs to begin" : playlist.get(currentSongIndex).getArtist();

        titleLabel = new JLabel(defaultTitle);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        artistLabel = new JLabel(defaultArtist);
        artistLabel.setFont(ARTIST_FONT);
        artistLabel.setForeground(SECONDARY_TEXT_COLOR);
        artistLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Add components to center panel
        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(albumArtLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        centerPanel.add(titleLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        centerPanel.add(artistLabel);
        centerPanel.add(Box.createVerticalGlue());
    }

    private void setupControlPanel() {
        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBackground(CONTROL_PANEL_COLOR);
        controlPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        // --- Seek bar panel (top) ---
        JPanel seekBarPanel = new JPanel(new BorderLayout(10, 0));
        seekBarPanel.setBackground(CONTROL_PANEL_COLOR);

        currentTimeLabel = new JLabel("0:00");
        currentTimeLabel.setForeground(SECONDARY_TEXT_COLOR);
        currentTimeLabel.setFont(REGULAR_FONT);

        totalTimeLabel = new JLabel("0:00");
        totalTimeLabel.setForeground(SECONDARY_TEXT_COLOR);
        totalTimeLabel.setFont(REGULAR_FONT);

        seekBar = new CustomSlider(0, 100, 0);
        seekBar.setBackground(CONTROL_PANEL_COLOR);
        seekBar.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (!seekBar.getValueIsAdjusting() && audioPlayer.isLoaded()) {
                    int value = seekBar.getValue();
                    audioPlayer.seek(value);
                    updateTimeLabels();
                    playPauseButton.setText("❚❚");
                    playPauseButton.setFont(new Font("Segoe UI Symbol", Font.BOLD, 20));
                    isPlaying = true;
                }
            }
        });

        seekBarPanel.add(currentTimeLabel, BorderLayout.WEST);
        seekBarPanel.add(seekBar, BorderLayout.CENTER);
        seekBarPanel.add(totalTimeLabel, BorderLayout.EAST);

        // --- Buttons ---
        shuffleButton = new CustomButton("🔀");
        shuffleButton.setToolTipText("Shuffle");
        shuffleButton.addActionListener(_ -> toggleShuffle());

        previousButton = new CustomButton("⏮");
        previousButton.addActionListener(_ -> previousSong());

        playPauseButton = new CustomButton("▶");
        playPauseButton.setFont(new Font("Segoe UI Symbol", Font.BOLD, 20));
        playPauseButton.setPreferredSize(new Dimension(60, 60));
        playPauseButton.addActionListener(_ -> togglePlayPause());

        nextButton = new CustomButton("⏭");
        nextButton.addActionListener(_ -> nextSong());

        loopButton = new CustomButton("🔁");
        loopButton.setToolTipText("Loop");
        loopButton.addActionListener(_ -> toggleLoop());

        // --- Button panel (centered) ---
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setBackground(CONTROL_PANEL_COLOR);
        buttonPanel.add(shuffleButton);
        buttonPanel.add(previousButton);
        buttonPanel.add(playPauseButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(loopButton);

        // --- Volume panel (right-aligned) ---
        JPanel volumePanel = new JPanel();
        volumePanel.setLayout(new BoxLayout(volumePanel, BoxLayout.X_AXIS));
        volumePanel.setBackground(CONTROL_PANEL_COLOR);
        volumePanel.setAlignmentY(Component.CENTER_ALIGNMENT);

        volumeIcon = new JLabel("🔊");
        volumeIcon.setForeground(SECONDARY_TEXT_COLOR);
        volumeIcon.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 18));

        volumeSlider = new CustomSlider(0, 100, 80);
        volumeSlider.setPreferredSize(new Dimension(100, 26));
        volumeSlider.setBackground(CONTROL_PANEL_COLOR);
        volumeSlider.addChangeListener(_ -> {
            if (audioPlayer != null) {
                audioPlayer.setVolume(volumeSlider.getValue() / 100.0f);
                if (volumeSlider.getValue() == 0) {
                    volumeIcon.setText("🔇");
                } else if (volumeSlider.getValue() < 50) {
                    volumeIcon.setText("🔉");
                } else {
                    volumeIcon.setText("🔊");
                }
            }
        });

        volumePanel.add(volumeIcon);
        volumePanel.add(Box.createRigidArea(new Dimension(5, 0)));
        volumePanel.add(volumeSlider);

        // --- Horizontal alignment panel (buttons + volume) ---
        JPanel centerRow = new JPanel();
        centerRow.setLayout(new BorderLayout());
        centerRow.setBackground(CONTROL_PANEL_COLOR);

        // Wrapper to shift buttons to the right
        JPanel buttonWrapper = new JPanel();
        buttonWrapper.setLayout(new BoxLayout(buttonWrapper, BoxLayout.X_AXIS));
        buttonWrapper.setBackground(CONTROL_PANEL_COLOR);
        buttonWrapper.add(Box.createRigidArea(new Dimension(125, 0)));
        buttonWrapper.add(buttonPanel);

        centerRow.add(buttonWrapper, BorderLayout.CENTER);
        centerRow.add(volumePanel, BorderLayout.EAST);

        // --- Add all to controlPanel ---
        controlPanel.add(seekBarPanel);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        controlPanel.add(centerRow);
    }

    private ImageIcon createPlaceholderAlbumArt(int width, int height) {
        // Return cached version if available
        if (cachedAlbumArt != null) {
            return cachedAlbumArt;
        }

        SwingWorker<ImageIcon, Void> worker = new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = image.createGraphics();

                // Enable antialiasing
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                // Create gradient background
                GradientPaint gradient = new GradientPaint(
                        0, 0, new Color(60, 60, 70),
                        width, height, new Color(30, 30, 40));
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, width, height, 15, 15);

                // Load and cache the note image
                if (cachedNoteImage == null) {
                    try (InputStream is = getClass().getResourceAsStream("/images/soundwave.png")) {
                        if (is != null) {
                            cachedNoteImage = ImageIO.read(is);
                        }
                    } catch (IOException e) {
                        System.err.println("Error loading music note image: " + e.getMessage());
                    }
                }

                if (cachedNoteImage != null) {
                    int targetWidth = width / 2;
                    int targetHeight = height / 2;
                    Image scaledNote = cachedNoteImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);

                    int x = (width - targetWidth) / 2;
                    int y = (height - targetHeight) / 2;

                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                    g2d.drawImage(scaledNote, x, y, null);
                } else {
                    // Fallback
                    g2d.setColor(new Color(100, 100, 120, 150));
                    g2d.fillRect(width / 4, height / 4, width / 2, height / 2);
                }

                g2d.dispose();
                return new ImageIcon(image);
            }

            @Override
            protected void done() {
                try {
                    cachedAlbumArt = get();
                    albumArtLabel.setIcon(cachedAlbumArt);
                } catch (Exception e) {
                    System.err.println("Error creating album art: " + e.getMessage());
                }
            }
        };
        worker.execute();

        // Return a simple placeholder immediately
        BufferedImage tempImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = tempImage.createGraphics();
        g2d.setColor(new Color(30, 30, 40));
        g2d.fillRoundRect(0, 0, width, height, 15, 15);
        g2d.dispose();
        return new ImageIcon(tempImage);
    }

    private void makeDraggable() {
        final Point[] dragPoint = { new Point(0, 0) };

        headerPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragPoint[0] = e.getPoint();
            }
        });

        headerPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Point currentLocation = getLocation();
                setLocation(
                        currentLocation.x + e.getX() - dragPoint[0].x,
                        currentLocation.y + e.getY() - dragPoint[0].y);
            }
        });
    }

    // Replace setupProgressTimer() method - Optimized version
    private void setupProgressTimer() {
        progressTimer = new Timer(500, new ActionListener() {
            private int lastPosition = -1;
            private int stuckCount = 0;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isPlaying || !audioPlayer.isLoaded()) {
                    return;
                }

                int currentPosition = audioPlayer.getCurrentPosition();
                int duration = audioPlayer.getDuration();

                if (currentPosition != lastPosition) {
                    updateSeekBarAndLabels(currentPosition, duration);
                    lastPosition = currentPosition;
                    stuckCount = 0;
                } else {
                    stuckCount++;
                }

                // Optimized song end detection
                if (currentPosition >= duration - 1 && duration > 0) {
                    handleSongEnd();
                } else if (stuckCount > 4 && currentPosition >= duration - 2) {
                    handleSongEnd();
                }
            }

            private void updateSeekBarAndLabels(int currentPosition, int duration) {
                // Update seek bar without triggering listener
                ChangeListener[] listeners = seekBar.getChangeListeners();
                for (ChangeListener listener : listeners) {
                    seekBar.removeChangeListener(listener);
                }

                seekBar.setMaximum(duration);
                seekBar.setValue(currentPosition);

                for (ChangeListener listener : listeners) {
                    seekBar.addChangeListener(listener);
                }

                // Update time labels
                currentTimeLabel.setText(formatTime(currentPosition));
                totalTimeLabel.setText(formatTime(duration));
            }

            private void handleSongEnd() {
                progressTimer.stop();
                isPlaying = false;

                SwingUtilities.invokeLater(() -> {
                    nextSong();
                    progressTimer.start();
                });
            }
        });
        progressTimer.start();
    }

    private void updateTimeLabels() {
        if (audioPlayer.isLoaded()) {
            int currentPosition = audioPlayer.getCurrentPosition();
            int duration = audioPlayer.getDuration();

            currentTimeLabel.setText(formatTime(currentPosition));
            totalTimeLabel.setText(formatTime(duration));
        }
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void togglePlayPause() {
        if (!isPlaying) {
            if (!audioPlayer.isLoaded()) {
                loadCurrentSong();
            }
            audioPlayer.play();
            playPauseButton.setText("❚❚");
            playPauseButton.setFont(new Font("Segoe UI Symbol", Font.BOLD, 20));
            isPlaying = true;
        } else {
            audioPlayer.pause();
            playPauseButton.setText("▶");
            playPauseButton.setFont(new Font("Segoe UI Symbol", Font.BOLD, 20));
            isPlaying = false;
        }
    }

    private void nextSong() {
        if (playlist.isEmpty()) {
            return; // No songs to play
        }

        int originalIndex = currentSongIndex;
        int attempts = 0;

        do {
            if (isLooping) {
                // Stay on current song for loop mode
                break;
            } else if (isShuffling) {
                // Validate shuffleOrder
                if (shuffleOrder.isEmpty() || shuffleOrder.size() != playlist.size()
                        || !shuffleOrder.contains(currentSongIndex)) {
                    rebuildShuffleOrder();
                }
                int currentShuffleIndex = shuffleOrder.indexOf(currentSongIndex);
                int nextShuffleIndex = (currentShuffleIndex + 1) % shuffleOrder.size();
                currentSongIndex = shuffleOrder.get(nextShuffleIndex);
            } else {
                currentSongIndex = (currentSongIndex + 1) % playlist.size();
            }

            // Check if the selected song file exists
            if (currentSongIndex < playlist.size()) {
                Song song = playlist.get(currentSongIndex);
                File songFile = new File(song.getFilePath());

                if (songFile.exists() && songFile.canRead()) {
                    break; // Found a valid song
                }
            }

            attempts++;
        } while (attempts < playlist.size() && currentSongIndex != originalIndex);

        // If we couldn't find any valid songs, refresh the playlist
        if (attempts >= playlist.size()) {
            refreshPlaylist();
            return;
        }

        loadCurrentSong();

        // Autoplay the next song
        isPlaying = true;
        audioPlayer.play();
        playPauseButton.setText("❚❚");
        playPauseButton.setFont(new Font("Segoe UI Symbol", Font.BOLD, 20));
        playlistView.setSelectedIndex(currentSongIndex);
        updateTimeLabels();
    }

    private void previousSong() {
        if (isLooping) {
            loadCurrentSong();
        } else if (isShuffling) {
            int prevIndex = shuffleOrder.indexOf(currentSongIndex) - 1;
            if (prevIndex < 0)
                prevIndex = shuffleOrder.size() - 1;
            currentSongIndex = shuffleOrder.get(prevIndex);
            loadCurrentSong();
        } else {
            currentSongIndex = (currentSongIndex - 1 + playlist.size()) % playlist.size();
            loadCurrentSong();
        }

        if (isPlaying) {
            audioPlayer.play();
        }
        playlistView.setSelectedIndex(currentSongIndex);
    }

    private void toggleShuffle() {
        isShuffling = !isShuffling;
        shuffleButton.setActive(isShuffling);
        shuffleButton.setBackground(isShuffling ? ACCENT_COLOR : CONTROL_PANEL_COLOR);
        shuffleButton.repaint();

        if (isShuffling) {
            rebuildShuffleOrder();
        } else {
            shuffleOrder.clear();
        }
    }

    private void rebuildShuffleOrder() {
        shuffleOrder.clear();
        if (playlist.isEmpty()) {
            return;
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < playlist.size(); i++) {
            indices.add(i);
        }
        while (!indices.isEmpty()) {
            int index = random.nextInt(indices.size());
            shuffleOrder.add(indices.remove(index));
        }
        if (currentSongIndex >= playlist.size()) {
            currentSongIndex = 0;
        }
    }

    private void toggleLoop() {
        isLooping = !isLooping;
        loopButton.setActive(isLooping);
        loopButton.setBackground(isLooping ? ACCENT_COLOR : CONTROL_PANEL_COLOR);
        loopButton.repaint();
    }

    private void initializeCurrentSong() {
        if (!playlist.isEmpty()) {
            currentSongIndex = 0;
            loadCurrentSong();
        }
    }

    private void loadCurrentSong() {
        if (playlist.isEmpty()) {
            updateUIForEmptyPlaylist();
            return;
        }

        if (currentSongIndex >= playlist.size() || currentSongIndex < 0) {
            currentSongIndex = 0;
        }

        Song song = playlist.get(currentSongIndex);

        // Check if the file still exists before attempting to load
        File songFile = new File(song.getFilePath());
        if (!songFile.exists() || !songFile.canRead()) {
            System.err.println("Song file no longer exists: " + song.getFilePath());

            // Remove this song from database and refresh playlist
            SwingWorker<Void, Void> cleanupWorker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    databaseManager.cleanupInvalidSongs();
                    return null;
                }

                @Override
                protected void done() {
                    refreshPlaylistFromDatabase();
                    JOptionPane.showMessageDialog(MusicPlayer.this,
                            "Song file no longer exists and has been removed from playlist:\n" + song.getTitle(),
                            "File Not Found",
                            JOptionPane.WARNING_MESSAGE);
                }
            };
            cleanupWorker.execute();
            return;
        }

        // Update UI immediately for responsiveness
        titleLabel.setText(song.getTitle());
        artistLabel.setText(song.getArtist());
        seekBar.setValue(0);
        playlistView.setSelectedIndex(currentSongIndex);

        // Load audio in background with enhanced error handling
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Ensure audioPlayer is initialized
                if (audioPlayer == null) {
                    audioPlayer = new AudioPlayer();
                }

                // Reset audio player to ensure clean state
                audioPlayer.reset();

                // Load the new audio file
                audioPlayer.load(song.getFilePath());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    SwingUtilities.invokeLater(() -> {
                        if (audioPlayer.isLoaded()) {
                            updateTimeLabels();
                            if (isPlaying) {
                                try {
                                    audioPlayer.play();
                                } catch (Exception e) {
                                    JOptionPane.showMessageDialog(MusicPlayer.this,
                                            "Cannot play audio: " + e.getMessage(),
                                            "Playback Error",
                                            JOptionPane.ERROR_MESSAGE);
                                    System.err.println("Playback error: " + e.getMessage());
                                    isPlaying = false;
                                    playPauseButton.setText("▶");
                                }
                            }
                        } else {
                            throw new IllegalStateException("Audio not loaded after load attempt");
                        }
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(MusicPlayer.this,
                                "Could not load the audio file: " + song.getFilePath() +
                                        "\nError: " + e.getMessage() +
                                        "\nThe file may be corrupted or in an unsupported format.",
                                "Error Loading Audio",
                                JOptionPane.ERROR_MESSAGE);

                        // Try to skip to next song if current one fails
                        if (playlist.size() > 1) {
                            nextSong();
                        } else {
                            updateUIForEmptyPlaylist();
                        }
                    });
                    System.err.println("Failed to load audio file: " + song.getFilePath() + " - " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    public static void main(String[] args) {
        // Set the look and feel to the system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create the application
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MusicPlayer();
            }
        });
    }

    private void performCleanShutdown() {
        try {
            // Stop the progress timer
            if (progressTimer != null) {
                progressTimer.stop();
            }

            // Stop audio playback
            if (audioPlayer != null) {
                audioPlayer.pause();
                audioPlayer.reset();
                audioPlayer.dispose();
            }

            // Clean up database connections
            if (databaseManager != null) {
                // Perform final cleanup of invalid songs
                databaseManager.cleanupInvalidSongs();
            }

            System.out.println("Application shutdown completed successfully.");

        } catch (Exception e) {
            System.err.println("Error during application shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
}