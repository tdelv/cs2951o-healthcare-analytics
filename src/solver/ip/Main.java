package solver.ip;

import ilog.concert.IloException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Optional;

public class Main {

    private static String filename;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java Main <file>");
            return;
        }

        String input = args[0];
        Path path = Paths.get(input);
        filename = path.getFileName().toString();

        Timer.totalTimer.start();

		/*
			Parse command line args here!
		 */
        CliArgs parser = new CliArgs(args);

        Settings.solveType = Settings.SolveType.valueOf(parser.switchValue("-solveType", "solveFloat"));
        Settings.probabilityUseTest = parser.switchDoubleValue("-probabilityUseTest", 0.3);
        Settings.choiceFadeoff = parser.switchDoubleValue("-choiceFadeOff", 0.8);
        Settings.randRestart = parser.switchDoubleValue("-randRestart", 0.0);
        Settings.randRestartFadeoff = parser.switchDoubleValue("-randRestartFadeoff", 0.9);
        Settings.verbosity = parser.switchIntegerValue("-verbosity", 1);
        Settings.dynamic = parser.switchBooleanValue("-dynamic", false);
        Settings.useStack = parser.switchBooleanValue("-useStack", true);
        Settings.numWorkers = parser.switchIntegerValue("-numWorkers", 1);
        Settings.skip = parser.switchIntegerValue("-skip", 5);

        Settings.setup();
        if (Settings.verbosity > 0) {
            Settings.print();
        }

        try {
            Optional<Integer> solution;
            switch (Settings.solveType) {
                case solveFloat: {
                    IPInstance instance = DataParser.parseIPFile(input);
                    if (Settings.useStack) {
                        if (Settings.numWorkers == 1) {
                            if (Settings.skip > 0) {
                                solution = instance.solveWhileSkip();
                            } else {
                                solution = instance.solveWhile();
                            }
                        } else {
                            solution = instance.solveWhileParallel();
                        }
                    } else {
                        solution = instance.solveRecursive();
                    }
                    break;
                }
                case solveInt: {
                    IPInstance instance = DataParser.parseIPFile(input);
                    LPInstance lpInstance = instance.lpInstance;
                    Optional<LPInstance.SolveLPReturn> plainSolution = lpInstance.solveLP(new HashMap<>());
                    solution = plainSolution.map((result) -> (int)Math.ceil(result.totalCost));
                    break;
                }
                default:
                    System.err.println("Unhandled solveType: " + Settings.solveType);
                    System.exit(1);
                    solution = null;
                    break;
            }

            Timer.totalTimer.stop();
            Timer.printTimers();
            System.out.println("Num invocations: " + LPInstance.numInvocations);
            printAndExit(solution);
        } catch (IloException | InterruptedException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void printAndExit(Optional<Integer> solution) {
        if (solution.isPresent()) {
            System.out.println("Instance: " + filename + " Time: " + String.format("%.2f", Timer.totalTimer.getTotalTime()) + " Result: " + solution.get() + " Solution: OPT");
        } else {
            System.out.println("Instance: " + filename + " Time: " + String.format("%.2f", Timer.totalTimer.getTotalTime()) + " Result: --" + " Solution: FAIL");
        }

        System.exit(0);
    }
}
