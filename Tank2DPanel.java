import java.awt.*;
import javax.swing.*;

public class Tank2DPanel extends JPanel {
    private final TankModel model;
    private boolean axesVisible = true;

    public Tank2DPanel(TankModel model) {
        this.model = model;
    }

    public void setAxesVisible(boolean axesVisible) {
        this.axesVisible = axesVisible;
    }

    private void drawAxisWithTicks2D(Graphics2D g, int x1, int y1, int x2, int y2, int tickEveryPx, int tickSize) {
        g.drawLine(x1, y1, x2, y2);

        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            for (int y = minY; y <= maxY; y += tickEveryPx) {
                g.drawLine(x1 - tickSize, y, x1 + tickSize, y);
            }
        } else if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            for (int x = minX; x <= maxX; x += tickEveryPx) {
                g.drawLine(x, y1 - tickSize, x, y1 + tickSize);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics gph) {
        super.paintComponent(gph);

        Graphics2D g = (Graphics2D) gph;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        model.refreshIntegrationConstant();

        int centerX = getWidth() / 2;
        int baseY = getHeight() - 60;

        double scale = 700;

        int leftWallX = centerX - (int) (TankModel.TANK_RADIUS * scale);
        int rightWallX = centerX + (int) (TankModel.TANK_RADIUS * scale);
        int topY = baseY - (int) (TankModel.TANK_HEIGHT * scale);

        g.setColor(new Color(50, 50, 50));
        g.drawLine(leftWallX, topY, leftWallX, baseY);
        g.drawLine(rightWallX, topY, rightWallX, baseY);
        g.drawLine(leftWallX, baseY, rightWallX, baseY);

        g.setColor(new Color(30, 90, 180));
        double drDraw = 0.002;
        for (double r = -TankModel.TANK_RADIUS; r < TankModel.TANK_RADIUS; r += drDraw) {
            double z1 = model.z(Math.abs(r));
            double z2 = model.z(Math.abs(r + drDraw));

            int x1 = centerX + (int) (r * scale);
            int y1 = baseY - (int) (z1 * scale);

            int x2 = centerX + (int) ((r + drDraw) * scale);
            int y2 = baseY - (int) (z2 * scale);

            g.drawLine(x1, y1, x2, y2);
        }

        if (axesVisible) {
            int midY = baseY - (int) ((TankModel.TANK_HEIGHT * scale) / 2.0);
            int tickEveryPx = (int) (0.05 * scale);

            g.setColor(new Color(180, 40, 40));
            drawAxisWithTicks2D(g, leftWallX, midY, rightWallX, midY, tickEveryPx, 4);
            g.drawString("X", rightWallX + 8, midY - 4);

            g.setColor(new Color(30, 110, 30));
            drawAxisWithTicks2D(g, centerX, topY, centerX, baseY, tickEveryPx, 4);
            g.drawString("Z", centerX + 6, topY - 6);
        }
    }
}
