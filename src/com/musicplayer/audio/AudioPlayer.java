package src.com.musicplayer.audio;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class AudioPlayer {
    private Clip clip;
    private AudioInputStream audioInputStream;
    private AudioInputStream decodedStream;
    private long totalLength;
    private long currentPosition;
    private boolean isLoaded = false;
    private boolean isPlaying = false;
    private float volume = 0.8f; // Default volume
    private String currentFilePath;
    private FloatControl volumeControl;

    public synchronized void load(String filePath) {
        try {
            cleanup(); // Clean up existing resources
            File audioFile = new File(filePath);
            if (!audioFile.exists() || !audioFile.canRead()) {
                throw new IOException("Audio file does not exist or is not readable: " + filePath);
            }

            // Load MP3 using mp3spi
            audioInputStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat baseFormat = audioInputStream.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            decodedStream = AudioSystem.getAudioInputStream(decodedFormat, audioInputStream);

            clip = AudioSystem.getClip();
            clip.open(decodedStream);
            totalLength = clip.getMicrosecondLength();
            currentPosition = 0;
            isLoaded = true;
            currentFilePath = filePath;

            // Initialize volume control
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                setVolume(volume);
            } else {
                System.err.println("Volume control not supported for: " + filePath);
            }

        } catch (UnsupportedAudioFileException e) {
            System.err.println("Unsupported audio file: " + filePath + " - " + e.getMessage());
            cleanup();
            isLoaded = false;
        } catch (LineUnavailableException e) {
            System.err.println("Audio line unavailable: " + e.getMessage());
            cleanup();
            isLoaded = false;
        } catch (IOException e) {
            System.err.println("IO error loading audio file: " + filePath + " - " + e.getMessage());
            cleanup();
            isLoaded = false;
        } catch (Exception e) {
            System.err.println("Unexpected error loading audio file: " + filePath + " - " + e.getMessage());
            cleanup();
            isLoaded = false;
        }
    }

    private synchronized void cleanup() {
        isPlaying = false;
        isLoaded = false;

        // Close clip
        if (clip != null) {
            try {
                if (clip.isRunning()) {
                    clip.stop();
                }
                if (clip.isOpen()) {
                    clip.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing clip: " + e.getMessage());
            } finally {
                clip = null;
            }
        }

        // Close decoded stream
        if (decodedStream != null) {
            try {
                decodedStream.close();
            } catch (IOException e) {
                System.err.println("Error closing decoded stream: " + e.getMessage());
            } finally {
                decodedStream = null;
            }
        }

        // Close audio input stream
        if (audioInputStream != null) {
            try {
                audioInputStream.close();
            } catch (IOException e) {
                System.err.println("Error closing audio input stream: " + e.getMessage());
            } finally {
                audioInputStream = null;
            }
        }

        volumeControl = null;
        currentPosition = 0;
        totalLength = 0;
        currentFilePath = null;
    }

    public synchronized void reset() {
        cleanup();
    }

    public synchronized void dispose() {
        cleanup();
    }

    public synchronized void play() {
        if (!isLoaded || clip == null) {
            System.err.println("Cannot play: Audio not loaded or clip is null.");
            return;
        }
        if (!isPlaying) {
            try {
                clip.setMicrosecondPosition(currentPosition);
                clip.start();
                isPlaying = true;
            } catch (Exception e) {
                System.err.println("Error starting playback: " + e.getMessage());
                isPlaying = false;
            }
        }
    }

    public synchronized void pause() {
        if (!isLoaded || clip == null) {
            System.err.println("Cannot pause: Audio not loaded or clip is null.");
            return;
        }
        if (isPlaying) {
            try {
                currentPosition = clip.getMicrosecondPosition();
                clip.stop();
                isPlaying = false;
            } catch (Exception e) {
                System.err.println("Error pausing audio: " + e.getMessage());
                isPlaying = false;
            }
        }
    }

    public synchronized void stop() {
        if (!isLoaded || clip == null) {
            // Only log if unexpected; initial stop before load is normal
            if (currentFilePath != null) {
                System.err.println("Cannot stop: Audio not loaded or clip is null for: " + currentFilePath);
            }
            isPlaying = false;
            currentPosition = 0;
            return;
        }
        try {
            if (clip.isRunning()) {
                clip.stop();
            }
            currentPosition = 0;
            clip.setMicrosecondPosition(0);
            isPlaying = false;
        } catch (Exception e) {
            System.err.println("Error stopping audio: " + e.getMessage());
            isPlaying = false;
        }
    }

    public synchronized void seek(int position) { // position in seconds
        if (!isLoaded || clip == null) {
            System.err.println("Cannot seek: Audio not loaded or clip is null.");
            return;
        }

        boolean wasPlaying = isPlaying;
        try {
            long targetPosition = (long) position * 1_000_000L;
            if (targetPosition < 0) {
                targetPosition = 0;
            } else if (targetPosition >= totalLength) {
                targetPosition = totalLength - 1_000_000L;
                if (targetPosition < 0) {
                    targetPosition = 0;
                }
            }

            if (isPlaying) {
                clip.stop();
            }
            clip.setMicrosecondPosition(targetPosition);
            currentPosition = targetPosition;

            if (wasPlaying) {
                clip.start();
                isPlaying = true;
            }
        } catch (Exception e) {
            System.err.println("Error seeking audio: " + e.getMessage());
            try {
                isPlaying = false;
                currentPosition = 0;
                clip.setMicrosecondPosition(0);
            } catch (Exception ex) {
                System.err.println("Error during seek recovery: " + ex.getMessage());
            }
        }
    }

    public synchronized void setVolume(float volume) { // volume is 0.0 to 1.0
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        if (volumeControl != null && isLoaded && clip != null) {
            try {
                float minGain = volumeControl.getMinimum();
                float maxGain = volumeControl.getMaximum();
                float targetMinDb = -40.0f;
                float targetMaxDb = 0.0f;
                float gain = targetMinDb + (this.volume * (targetMaxDb - targetMinDb));
                gain = Math.max(minGain, Math.min(maxGain, gain));
                volumeControl.setValue(gain);
            } catch (Exception e) {
                System.err.println("Error setting volume: " + e.getMessage());
            }
        } else {
            System.err.println("Volume control not available or audio not loaded.");
        }
    }

    public synchronized int getCurrentPosition() {
        if (!isLoaded || clip == null) {
            return 0;
        }
        try {
            if (isPlaying && clip.isRunning()) {
                currentPosition = clip.getMicrosecondPosition();
            }
            return (int) (currentPosition / 1_000_000L);
        } catch (Exception e) {
            System.err.println("Error getting current position: " + e.getMessage());
            return (int) (currentPosition / 1_000_000L);
        }
    }

    public synchronized int getDuration() {
        if (isLoaded) {
            return (int) (totalLength / 1_000_000L);
        }
        return 0;
    }

    public synchronized boolean isLoaded() {
        return isLoaded && clip != null;
    }

    public synchronized boolean isPlaying() {
        return isPlaying && isLoaded && clip != null && clip.isRunning();
    }
}