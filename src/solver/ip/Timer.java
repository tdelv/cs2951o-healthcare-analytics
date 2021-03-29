package solver.ip;

public class Timer {
    public static Timer totalTimer = new Timer();
    public static Timer setupTimer = new Timer();
    public static Timer lpSolveTimer = new Timer();
    public static Timer lpSolveForLoopTimer = new Timer();
    public static Timer cplexSolveTimer = new Timer();
    public static Timer dynamicUpdateTimer = new Timer();

    private long startTime;
    private long stopTime;
    private boolean running;
    private double totalTime;

    private final double nano = 1000000000.0;

    public Timer() {
        super();
    }

    public void reset() {
        this.startTime = 0;
        this.running = false;

        this.totalTime = 0;
    }

    public void start() {
        assert !running : "Called start on stopped timer.";
        this.startTime = System.nanoTime();
        this.running = true;
    }

    public void stop() {
        assert running : "Called stop on running timer.";
        this.stopTime = System.nanoTime();
        this.running = false;
        this.totalTime += this.getCurrentTime();
    }

    public double getCurrentTime() {
        double elapsed;
        if (running) {
            elapsed = ((System.nanoTime() - startTime) / nano);
        } else {
            elapsed = ((stopTime - startTime) / nano);
        }
        return elapsed;
    }

    public double getTotalTime() {
        if (running) {
            return this.totalTime + this.getCurrentTime();
        } else {
            return this.totalTime;
        }
    }

    public void addTime(double toAdd) {
        this.totalTime += toAdd;
    }

    public static void printTimers() {
        System.out.println("totalTimer: " + totalTimer.getTotalTime());
        System.out.println("  setupTimer: " + setupTimer.getTotalTime());
        System.out.println("  lpSolveTimer: " + lpSolveTimer.getTotalTime());
        System.out.println("    lpSolveForLoopTimer: " + lpSolveForLoopTimer.getTotalTime());
        System.out.println("    cplexSolveTimer: " + cplexSolveTimer.getTotalTime());
        System.out.println("  dynamicUpdateTimer: " + dynamicUpdateTimer.getTotalTime());
    }
}
