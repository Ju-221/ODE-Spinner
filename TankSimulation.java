import java.awt.BorderLayout;
import javax.swing.*;

public class TankSimulation {

    private static void setupAndShowUI() {
        JFrame frame = new JFrame("Rotating Tank Validator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        TankModel model = new TankModel();
        Tank2DPanel view2D = new Tank2DPanel(model);
        Tank3DPanel view3D = new Tank3DPanel(model);

        JTextArea dataCollectedArea = new JTextArea();
        dataCollectedArea.setEditable(false);
        dataCollectedArea.setLineWrap(true);
        dataCollectedArea.setWrapStyleWord(true);
        dataCollectedArea.setFocusable(false);

        JScrollPane dataScroll = new JScrollPane(dataCollectedArea);
        JPanel dataPanel = new JPanel(new BorderLayout());
        dataPanel.setBorder(BorderFactory.createTitledBorder("data collected:"));
        dataPanel.add(dataScroll, BorderLayout.CENTER);
        dataPanel.setPreferredSize(new java.awt.Dimension(250, 0));

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("2D view"));
        leftPanel.add(view2D, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("3D view"));
        rightPanel.add(view3D, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);

        frame.setLayout(new BorderLayout());
        frame.add(splitPane, BorderLayout.CENTER);
        frame.add(dataPanel, BorderLayout.WEST);

        JLabel omegaValueLabel = new JLabel();

        JSlider omegaSlider = new JSlider(0, 200, 60);
        omegaSlider.setMajorTickSpacing(50);
        omegaSlider.setMinorTickSpacing(10);
        omegaSlider.setPaintTicks(true);
        omegaSlider.setPaintLabels(true);
        omegaSlider.setBorder(BorderFactory.createTitledBorder("Angular velocity"));
        omegaValueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        omegaValueLabel.setText(String.format("%.2f rad/s | %.2f RPM",
                model.getOmega(), TankModel.toRpm(model.getOmega())));
        omegaSlider.addChangeListener(e -> {
            model.setOmega(omegaSlider.getValue() / 10.0);
            omegaValueLabel.setText(String.format("%.2f rad/s | %.2f RPM",
                    model.getOmega(), TankModel.toRpm(model.getOmega())));
            view2D.repaint();
            view3D.repaint();
        });

        JCheckBox showAxesCheckBox = new JCheckBox("Show axes + measuring lines", true);
        showAxesCheckBox.addActionListener(e -> {
            boolean showAxes = showAxesCheckBox.isSelected();
            view2D.setAxesVisible(showAxes);
            view3D.setAxesVisible(showAxes);
            view2D.repaint();
            view3D.repaint();
        });

        JSlider verticalViewSlider = new JSlider(JSlider.VERTICAL, -220, 220, -100);
        verticalViewSlider.setMajorTickSpacing(110);
        verticalViewSlider.setMinorTickSpacing(22);
        verticalViewSlider.setPaintTicks(true);
        verticalViewSlider.setPaintLabels(true);
        verticalViewSlider.setBorder(BorderFactory.createTitledBorder("3D up/down"));
        view3D.setVerticalOffset(verticalViewSlider.getValue());
        verticalViewSlider.addChangeListener(e -> {
            view3D.setVerticalOffset(verticalViewSlider.getValue());
            view3D.repaint();
        });

        JButton zoomInButton = new JButton("+");
        zoomInButton.setToolTipText("Zoom in 3D view");
        zoomInButton.addActionListener(e -> {
            view3D.zoomIn();
            view3D.repaint();
        });

        JButton zoomOutButton = new JButton("-");
        zoomOutButton.setToolTipText("Zoom out 3D view");
        zoomOutButton.addActionListener(e -> {
            view3D.zoomOut();
            view3D.repaint();
        });

        JPanel zoomButtonsPanel = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
        zoomButtonsPanel.setBorder(BorderFactory.createTitledBorder("3D zoom"));
        zoomButtonsPanel.add(zoomOutButton);
        zoomButtonsPanel.add(zoomInButton);

        JPanel eastControls = new JPanel(new BorderLayout());
        eastControls.add(verticalViewSlider, BorderLayout.CENTER);
        eastControls.add(zoomButtonsPanel, BorderLayout.SOUTH);

        JPanel bottomControls = new JPanel(new BorderLayout());
        JPanel leftTogglesPanel = new JPanel(new java.awt.GridLayout(1, 1, 0, 2));
        leftTogglesPanel.add(showAxesCheckBox);
        bottomControls.add(leftTogglesPanel, BorderLayout.WEST);
        bottomControls.add(omegaValueLabel, BorderLayout.NORTH);
        bottomControls.add(omegaSlider, BorderLayout.SOUTH);

        Runnable refreshDataPanel = () -> {
            model.refreshIntegrationConstant();
            double omega = model.getOmega();
            double rpm = TankModel.toRpm(omega);
            double zCenter = model.z(0.0);
            double zWall = model.z(TankModel.TANK_RADIUS);
            double headroom = TankModel.TANK_HEIGHT - zWall;
            String meshMode = view3D.isRainbowMesh() ? "slope-rainbow" : "blue wireframe";

            String text = String.format(
                "omega: %.3f rad/s\n"
                    + "omega: %.2f RPM\n"
                    + "C: %.5f m\n"
                    + "z(0): %.5f m\n"
                    + "z(R): %.5f m\n"
                    + "tank height: %.3f m\n"
                    + "static fill height: %.3f m\n"
                    + "wall headroom: %.5f m\n"
                    + "spill risk: %s\n"
                    + "max slope |dz/dr|: %.4f\n"
                    + "mesh mode: %s\n"
                    + "3D zoom: %.0f%%%n"
                    + "3D vertical offset: %d\n"
                    + "axes guides: %s\n\n"
                    + "Controls:\n"
                    + "- Drag in 3D to rotate\n"
                    + "- Press A for blue mesh\n"
                    + "- Use +/- buttons to zoom\n"
                    + "- Use 3D up/down slider\n"
                    + "- Toggle axes checkbox",
                omega,
                rpm,
                model.getIntegrationConstant(),
                zCenter,
                zWall,
                TankModel.TANK_HEIGHT,
                TankModel.TANK_HEIGHT * TankModel.FILL_FRACTION,
                headroom,
                model.spillsAtCurrentOmega() ? "YES" : "NO",
                view3D.getMaxSlopeMagnitude(),
                meshMode,
                view3D.getZoom() * 100.0,
                view3D.getVerticalOffset(),
                showAxesCheckBox.isSelected() ? "ON" : "OFF");
            dataCollectedArea.setText(text);
        };

        Timer dataRefreshTimer = new Timer(150, e -> refreshDataPanel.run());
        dataRefreshTimer.start();
        refreshDataPanel.run();

        frame.add(bottomControls, BorderLayout.SOUTH);
        frame.add(eastControls, BorderLayout.EAST);
        frame.setSize(1080, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        TankModel model = new TankModel();
        model.runValidationSweep();
        SwingUtilities.invokeLater(TankSimulation::setupAndShowUI);
    }
}