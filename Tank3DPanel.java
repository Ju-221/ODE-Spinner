import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

public class Tank3DPanel extends JPanel {
    private final TankModel model;
    private static final double VISUAL_SPIN_FACTOR = 0.45;
    private static final double BASE_RENDER_SCALE = 1.35;
    private static final double ZOOM_STEP = 1.12;
    private static final double MIN_ZOOM = 0.45;
    private static final double MAX_ZOOM = 3.20;
    private static final double FULL_RAINBOW_SLOPE = 2.5;
    private static final Color BLUE_WIREFRAME = new Color(20, 90, 200);

    private double yaw = Math.toRadians(40.0);
    private double pitch = Math.toRadians(28.0);
    private double sceneSpin = 0.0;
    private double zoom = 1.5;
    private boolean rainbowMesh = true;
    private boolean axesVisible = true;
    private int verticalOffset = 70;
    private int dragStartX;
    private int dragStartY;
    private long lastFrameNanos;

    public Tank3DPanel(TankModel model) {
        this.model = model;
        this.lastFrameNanos = System.nanoTime();

        MouseAdapter dragToRotate = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStartX = e.getX();
                dragStartY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - dragStartX;
                int dy = e.getY() - dragStartY;

                yaw += dx * 0.01;
                pitch -= dy * 0.01;

                double pitchLimit = Math.toRadians(85.0);
                pitch = Math.max(-pitchLimit, Math.min(pitchLimit, pitch));

                dragStartX = e.getX();
                dragStartY = e.getY();
                repaint();
            }
        };

        addMouseListener(dragToRotate);
        addMouseMotionListener(dragToRotate);

        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();
        inputMap.put(KeyStroke.getKeyStroke('a'), "setBlueWireframe");
        inputMap.put(KeyStroke.getKeyStroke('A'), "setBlueWireframe");
        actionMap.put("setBlueWireframe", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                rainbowMesh = !rainbowMesh;
                repaint();
            }
        });

        Timer spinTimer = new Timer(16, e -> {
            long now = System.nanoTime();
            double dtSeconds = (now - lastFrameNanos) / 1_000_000_000.0;
            lastFrameNanos = now;

            sceneSpin += model.getOmega() * VISUAL_SPIN_FACTOR * dtSeconds;
            repaint();
        });
        spinTimer.start();
    }

    public void setVerticalOffset(int verticalOffset) {
        this.verticalOffset = verticalOffset;
    }

    public void zoomIn() {
        zoom = Math.min(MAX_ZOOM, zoom * ZOOM_STEP);
    }

    public void zoomOut() {
        zoom = Math.max(MIN_ZOOM, zoom / ZOOM_STEP);
    }

    public double getZoom() {
        return zoom;
    }

    public void setAxesVisible(boolean axesVisible) {
        this.axesVisible = axesVisible;
    }

    public boolean isRainbowMesh() {
        return rainbowMesh;
    }

    public int getVerticalOffset() {
        return verticalOffset;
    }

    public double getMaxSlopeMagnitude() {
        return slopeMagnitude(TankModel.TANK_RADIUS);
    }

    private void drawAxisTicks3D(Graphics2D g, double ax, double ay, double az,
                                 double bx, double by, double bz,
                                 double tickStepMeters, double tickSizeMeters,
                                 Color axisColor,
                                 double centerX, double centerY, double scale,
                                 char axisType) {
        g.setColor(axisColor);

        Point pA = projectPhysicalHeight(ax, ay, az, (int) centerX, (int) centerY, scale);
        Point pB = projectPhysicalHeight(bx, by, bz, (int) centerX, (int) centerY, scale);
        g.drawLine(pA.x, pA.y, pB.x, pB.y);

        double length = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        int ticks = Math.max(1, (int) Math.floor(length / tickStepMeters));
        for (int i = 1; i <= ticks; i++) {
            double t = (i * tickStepMeters) / length;
            if (t >= 1.0) {
                break;
            }

            double tx = ax + (bx - ax) * t;
            double ty = ay + (by - ay) * t;
            double tz = az + (bz - az) * t;

            Point tickStart;
            Point tickEnd;
            if (axisType == 'x') {
                tickStart = projectPhysicalHeight(tx, ty - tickSizeMeters, tz, (int) centerX, (int) centerY, scale);
                tickEnd = projectPhysicalHeight(tx, ty + tickSizeMeters, tz, (int) centerX, (int) centerY, scale);
            } else if (axisType == 'y') {
                tickStart = projectPhysicalHeight(tx - tickSizeMeters, ty, tz, (int) centerX, (int) centerY, scale);
                tickEnd = projectPhysicalHeight(tx + tickSizeMeters, ty, tz, (int) centerX, (int) centerY, scale);
            } else {
                tickStart = projectPhysicalHeight(tx - tickSizeMeters, ty, tz, (int) centerX, (int) centerY, scale);
                tickEnd = projectPhysicalHeight(tx + tickSizeMeters, ty, tz, (int) centerX, (int) centerY, scale);
            }

            g.drawLine(tickStart.x, tickStart.y, tickEnd.x, tickEnd.y);
        }
    }

    private Point project(double x, double y, double z, int centerX, int centerY, double scale) {
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);

        double xYaw = cosYaw * x - sinYaw * y;
        double yYaw = sinYaw * x + cosYaw * y;
        double zYaw = z;

        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);

        double yPitch = cosPitch * yYaw + sinPitch * zYaw;
        double zPitch = -sinPitch * yYaw + cosPitch * zYaw;

        double perspective = 1.0 / (1.0 + 0.8 * zPitch);
        int sx = centerX + (int) (xYaw * scale * perspective);
        int sy = centerY - (int) (yPitch * scale * perspective);
        return new Point(sx, sy);
    }

    private Point projectPhysicalHeight(double x, double y, double height, int centerX, int centerY, double scale) {
        // Convert physical height (0 at bottom, H at top) to the renderer's z axis orientation.
        double renderZ = TankModel.TANK_HEIGHT - height;
        return project(x, y, renderZ, centerX, centerY, scale);
    }

    private Color colorForHeight(double height, double minHeight, double maxHeight) {
        double range = Math.max(1e-9, maxHeight - minHeight);
        double normalized = (height - minHeight) / range;
        normalized = Math.max(0.0, Math.min(1.0, normalized));

        // Hue from blue (low) to red (high) for a simulation-style rainbow map.
        float hue = (float) (0.66 - 0.66 * normalized);
        return Color.getHSBColor(hue, 1.0f, 1.0f);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private Color blend(Color a, Color b, double t) {
        double alpha = clamp01(t);
        int r = (int) Math.round((1.0 - alpha) * a.getRed() + alpha * b.getRed());
        int g = (int) Math.round((1.0 - alpha) * a.getGreen() + alpha * b.getGreen());
        int bl = (int) Math.round((1.0 - alpha) * a.getBlue() + alpha * b.getBlue());
        return new Color(r, g, bl);
    }

    private double slopeMagnitude(double radius) {
        // dz/dr = (omega^2/g) * r
        double omega = model.getOmega();
        return Math.abs((omega * omega / TankModel.GRAVITY) * radius);
    }

    private void drawRing(Graphics2D g, double z, Color color, int centerX, int centerY, double scale) {
        g.setColor(color);
        int samples = 90;

        Point previous = null;
        for (int i = 0; i <= samples; i++) {
            double theta = 2.0 * Math.PI * i / samples + sceneSpin;
            double x = TankModel.TANK_RADIUS * Math.cos(theta);
            double y = TankModel.TANK_RADIUS * Math.sin(theta);
            Point current = projectPhysicalHeight(x, y, z, centerX, centerY, scale);

            if (previous != null) {
                g.drawLine(previous.x, previous.y, current.x, current.y);
            }
            previous = current;
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2 + verticalOffset;
        double scale = Math.min(getWidth(), getHeight()) * BASE_RENDER_SCALE * zoom;

        model.refreshIntegrationConstant();
        double constant = model.getIntegrationConstant();
        double zCenter = model.z(0.0, model.getOmega(), constant);
        double zWall = model.z(TankModel.TANK_RADIUS, model.getOmega(), constant);
        double minSurfaceHeight = Math.min(zCenter, zWall);
        double maxSurfaceHeight = Math.max(zCenter, zWall);

        drawRing(g, 0.0, new Color(80, 80, 80), centerX, centerY, scale);
        drawRing(g, TankModel.TANK_HEIGHT, new Color(60, 60, 60), centerX, centerY, scale);

        g.setColor(new Color(120, 120, 120));
        for (int i = 0; i < 8; i++) {
            double theta = 2.0 * Math.PI * i / 8.0 + sceneSpin;
            double x = TankModel.TANK_RADIUS * Math.cos(theta);
            double y = TankModel.TANK_RADIUS * Math.sin(theta);

            Point pBottom = projectPhysicalHeight(x, y, 0.0, centerX, centerY, scale);
            Point pTop = projectPhysicalHeight(x, y, TankModel.TANK_HEIGHT, centerX, centerY, scale);
            g.drawLine(pBottom.x, pBottom.y, pTop.x, pTop.y);
        }

        int radialLines = 18;
        int angleLines = 48;

        for (int i = 0; i <= radialLines; i++) {
            double r = TankModel.TANK_RADIUS * i / radialLines;
            Point previous = null;

            for (int j = 0; j <= angleLines; j++) {
                double theta = 2.0 * Math.PI * j / angleLines + sceneSpin;
                double x = r * Math.cos(theta);
                double y = r * Math.sin(theta);
                double radial = Math.sqrt(x * x + y * y);
                double z = model.z(radial, model.getOmega(), constant);

                Point current = projectPhysicalHeight(x, y, z, centerX, centerY, scale);
                if (previous != null) {
                    if (rainbowMesh) {
                        Color rainbow = colorForHeight(z, minSurfaceHeight, maxSurfaceHeight);
                        double slopeFactor = clamp01(slopeMagnitude(radial) / FULL_RAINBOW_SLOPE);
                        g.setColor(blend(BLUE_WIREFRAME, rainbow, slopeFactor));
                    } else {
                        g.setColor(BLUE_WIREFRAME);
                    }
                    g.drawLine(previous.x, previous.y, current.x, current.y);
                }
                previous = current;
            }
        }

        for (int j = 0; j < 12; j++) {
            double theta = 2.0 * Math.PI * j / 12.0 + sceneSpin;
            Point previous = null;

            for (int i = 0; i <= radialLines; i++) {
                double r = TankModel.TANK_RADIUS * i / radialLines;
                double x = r * Math.cos(theta);
                double y = r * Math.sin(theta);
                double z = model.z(r, model.getOmega(), constant);
                Point current = projectPhysicalHeight(x, y, z, centerX, centerY, scale);

                if (previous != null) {
                    if (rainbowMesh) {
                        Color rainbow = colorForHeight(z, minSurfaceHeight, maxSurfaceHeight);
                        double slopeFactor = clamp01(slopeMagnitude(r) / FULL_RAINBOW_SLOPE);
                        g.setColor(blend(BLUE_WIREFRAME, rainbow, slopeFactor));
                    } else {
                        g.setColor(BLUE_WIREFRAME);
                    }
                    g.drawLine(previous.x, previous.y, current.x, current.y);
                }
                previous = current;
            }
        }

        if (axesVisible) {
            double cx = 0.0;
            double cy = 0.0;
            double cz = TankModel.TANK_HEIGHT * 0.5;
            double axisLength = TankModel.TANK_RADIUS * 0.9;
            double tickStep = 0.05;
            double tickSize = 0.006;

            drawAxisTicks3D(g, cx - axisLength, cy, cz, cx + axisLength, cy, cz,
                    tickStep, tickSize, new Color(180, 40, 40), centerX, centerY, scale, 'x');
            drawAxisTicks3D(g, cx, cy - axisLength, cz, cx, cy + axisLength, cz,
                    tickStep, tickSize, new Color(30, 110, 30), centerX, centerY, scale, 'y');
                drawAxisTicks3D(g, cx, cy, 0.0, cx, cy, TankModel.TANK_HEIGHT,
                    tickStep, tickSize, new Color(30, 30, 160), centerX, centerY, scale, 'z');

            Point px = projectPhysicalHeight(cx + axisLength, cy, cz, centerX, centerY, scale);
            Point py = projectPhysicalHeight(cx, cy + axisLength, cz, centerX, centerY, scale);
            Point pz = projectPhysicalHeight(cx, cy, cz + axisLength, centerX, centerY, scale);
            g.setColor(new Color(180, 40, 40));
            g.drawString("X", px.x + 6, px.y - 2);
            g.setColor(new Color(30, 110, 30));
            g.drawString("Y", py.x + 6, py.y - 2);
            g.setColor(new Color(30, 30, 160));
            g.drawString("Z", pz.x + 6, pz.y - 2);

            Point origin = projectPhysicalHeight(cx, cy, cz, centerX, centerY, scale);
            g.setColor(new Color(50, 50, 50));
            g.fillOval(origin.x - 3, origin.y - 3, 6, 6);
            //g.drawString("(0,0,0)", origin.x + 8, origin.y - 6);
        }
    }
}
