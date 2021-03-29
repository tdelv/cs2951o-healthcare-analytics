package solver.ip;

import ilog.concert.IloNumVarType;
import ilog.cplex.IloCplex;

import java.util.Random;

public class Settings {
    static Random rand = new Random(0);

    static SolveType solveType = SolveType.solveFloat;
    static IloNumVarType varType = IloNumVarType.Float;
    static double probabilityUseTest = 0.3;
    static double choiceFadeoff = 0.8;
    static double randRestart = 0.00;
    static double randRestartFadeoff = 0.9;
    static int verbosity = 1;
    static boolean dynamic = false;
    static boolean useStack = true;
    static int numWorkers = 1;
    static int skip = 5;

    enum SolveType {
        solveFloat,
        solveInt
    }

    public static void setup() {
        switch (solveType) {
            case solveFloat:
                varType = IloNumVarType.Float;
                break;
            case solveInt:
                varType = IloNumVarType.Int;
                break;
            default:
                System.err.println("Invalid solveType: " + solveType);
                System.exit(1);
        }

        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
    }

    public static void print() {
        System.out.println("Settings:");
        System.out.println("  solveType: " + solveType);
        System.out.println("  varType: " + varType);
        System.out.println("  probabilityUseTest: " + probabilityUseTest);
        System.out.println("  choiceFadeoff: " + choiceFadeoff);
        System.out.println("  randRestart: " + randRestart);
        System.out.println("  randRestartFadeoff: " + randRestartFadeoff);
        System.out.println("  verbosity: " + verbosity);
        System.out.println("  dynamic: " + dynamic);
        System.out.println("  useStack: " + useStack);
        System.out.println("  numWorkers: " + numWorkers);
        System.out.println("  skip: " + skip);
    }
}
