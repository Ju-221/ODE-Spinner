public class TankModel {

    public static final double GRAVITY = 9.81;
    public static final double TANK_RADIUS = 0.15; //meters 15m radius
    public static final double TANK_HEIGHT = 0.50; //meters 50m tall
    public static final double FILL_FRACTION = 0.80; //percentage filed

    private static final int INTEGRATION_STEPS = 1200; // more steps = more accuracy but slower performance
    private static final double VOLUME_TOLERANCE = 1e-9;
    private static final double RAD_PER_SEC_TO_RPM = 60.0 / (2.0 * Math.PI);

    private double omega = 6.0;
    private double integrationConstant = 0.0;

    public double getOmega() {
        return omega;
    }

    public static double toRpm(double omegaRadPerSec) {
        return omegaRadPerSec * RAD_PER_SEC_TO_RPM;
    }

    public void setOmega(double omega) {
        this.omega = omega;
        refreshIntegrationConstant();
    }

    public double getIntegrationConstant() {
        return integrationConstant;
    }

    public double rawZ(double r, double currentOmega, double constant) {
        return (currentOmega * currentOmega / (2.0 * GRAVITY)) * r * r + constant;
    }

    public double z(double r, double currentOmega, double constant) {
        return Math.max(0.0, rawZ(r, currentOmega, constant));
    }

    public double z(double r) {
        return z(r, omega, integrationConstant);
    }

    public double targetVolume() {
        return Math.PI * TANK_RADIUS * TANK_RADIUS * (TANK_HEIGHT * FILL_FRACTION);
    }

    public double volumeFor(double currentOmega, double constant) {
        double dr = TANK_RADIUS / INTEGRATION_STEPS;
        double integral = 0.0;

        for (int i = 0; i < INTEGRATION_STEPS; i++) {
            double r1 = i * dr;
            double r2 = (i + 1) * dr;

            double f1 = z(r1, currentOmega, constant) * r1;
            double f2 = z(r2, currentOmega, constant) * r2;
            integral += 0.5 * (f1 + f2) * dr;
        }

        return 2.0 * Math.PI * integral;
    }

    public double solveIntegrationConstant(double currentOmega) {
        double target = targetVolume();

        double low = -2.0 * TANK_HEIGHT;
        double high = 2.0 * TANK_HEIGHT;

        while (volumeFor(currentOmega, low) > target) {
            low *= 2.0;
        }
        while (volumeFor(currentOmega, high) < target) {
            high *= 2.0;
        }

        for (int i = 0; i < 120; i++) {
            double mid = 0.5 * (low + high);
            double volumeMid = volumeFor(currentOmega, mid);

            if (Math.abs(volumeMid - target) < VOLUME_TOLERANCE) {
                return mid;
            }

            if (volumeMid < target) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return 0.5 * (low + high);
    }

    public void refreshIntegrationConstant() {
        integrationConstant = solveIntegrationConstant(omega);
    }

    public boolean spills(double currentOmega, double constant) {
        double zWall = z(TANK_RADIUS, currentOmega, constant);
        return zWall > TANK_HEIGHT;
    }

    public boolean spillsAtCurrentOmega() {
        return spills(omega, integrationConstant);
    }

    public double maxSafeOmega() {
        double low = 0.0;
        double high = 1.0;

        while (true) {
            double c = solveIntegrationConstant(high);
            if (spills(high, c)) {
                break;
            }
            high *= 2.0;
            if (high > 200.0) {
                return high;
            }
        }

        for (int i = 0; i < 80; i++) {
            double mid = 0.5 * (low + high);
            double c = solveIntegrationConstant(mid);

            if (spills(mid, c)) {
                high = mid;
            } else {
                low = mid;
            }
        }

        return low;
    }

    public void runValidationSweep() {
        double target = targetVolume();

        System.out.printf("%10s %10s %10s %10s %10s %12s %8s%n",
            "omega", "rpm", "C", "z(0)", "z(R)", "volError(%)", "spill");
        for (double w = 0.0; w <= 20.0; w += 1.0) {
            double c = solveIntegrationConstant(w);
            double simulatedVolume = volumeFor(w, c);
            double relError = 100.0 * Math.abs(simulatedVolume - target) / target;
            double zCenter = z(0.0, w, c);
            double zWall = z(TANK_RADIUS, w, c);
            double rpm = toRpm(w);

            System.out.printf("%10.2f %10.2f %10.5f %10.5f %10.5f %12.6f %8s%n",
                w, rpm, c, zCenter, zWall, relError, (zWall > TANK_HEIGHT ? "YES" : "NO"));
        }

        double safeOmega = maxSafeOmega();
        System.out.printf("%nEstimated max safe omega (no spill): %.6f rad/s (%.2f RPM)%n",
            safeOmega, toRpm(safeOmega));
    }
}
