package src.com.musicplayer.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import src.com.musicplayer.MusicPlayer;

public class CustomButton extends JButton {
    private boolean isActive = false;

    public CustomButton(String text) {
        super(text);
        // Use "Segoe UI Symbol" font, ensure availability or fallback
        Font buttonFont = new Font("Segoe UI Symbol", Font.BOLD, 14);
        setFont(buttonFont);
        setForeground(MusicPlayer.TEXT_COLOR);
        setBackground(MusicPlayer.CONTROL_PANEL_COLOR);
        setBorderPainted(false);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setPreferredSize(new Dimension(50, 50));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isActive) {
                    setBackground(MusicPlayer.BUTTON_HOVER_COLOR);
                }
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isActive) {
                    setBackground(MusicPlayer.CONTROL_PANEL_COLOR);
                }
                repaint();
            }
        });
    }

    public void setActive(boolean active) {
        this.isActive = active;
        setBackground(active ? MusicPlayer.ACCENT_COLOR : MusicPlayer.CONTROL_PANEL_COLOR);
        setForeground(active ? Color.BLACK : MusicPlayer.TEXT_COLOR);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Determine color based on state
        if (isActive || getModel().isPressed()) {
            g2.setColor(MusicPlayer.ACCENT_COLOR);
            super.setForeground(Color.BLACK);
        } else if (getModel().isRollover()) {
            g2.setColor(MusicPlayer.BUTTON_HOVER_COLOR);
            super.setForeground(MusicPlayer.TEXT_COLOR);
        } else {
            g2.setColor(getBackground());
            super.setForeground(MusicPlayer.TEXT_COLOR);
        }

        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
        super.paintComponent(g);
        g2.dispose();
    }
}