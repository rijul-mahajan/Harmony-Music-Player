package src.com.musicplayer.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;

import src.com.musicplayer.MusicPlayer;

public class CustomSlider extends JSlider {
    public CustomSlider(int min, int max, int value) {
        super(min, max, value);
        setUI(new CustomSliderUI(this));
        setFocusable(false);
    }

    static class CustomSliderUI extends BasicSliderUI {
        public CustomSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int trackHeight = 4;
            int trackWidth = trackRect.width;
            int trackY = trackRect.y + (trackRect.height - trackHeight) / 2;

            // Background track
            g2d.setColor(new Color(70, 70, 70));
            g2d.fillRoundRect(trackRect.x, trackY, trackWidth, trackHeight, trackHeight, trackHeight);

            // Filled track based on slider value
            int value = slider.getValue();
            int min = slider.getMinimum();
            int max = slider.getMaximum();
            double percentage = (double) (value - min) / (max - min);
            int filledWidth = (int) (percentage * trackWidth);

            if (filledWidth > 0) {
                g2d.setColor(MusicPlayer.ACCENT_COLOR);
                g2d.fillRoundRect(trackRect.x, trackY, filledWidth, trackHeight, trackHeight, trackHeight);
            }
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int thumbSize = 12;
            int thumbX = thumbRect.x + (thumbRect.width - thumbSize) / 2;
            int thumbY = thumbRect.y + (thumbRect.height - thumbSize) / 2;

            g2d.setColor(MusicPlayer.ACCENT_COLOR);
            g2d.fillOval(thumbX, thumbY, thumbSize, thumbSize);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(20, 20);
        }

        @Override
        public void paintFocus(Graphics g) {
            // Do nothing to remove focus border
        }
    }
}