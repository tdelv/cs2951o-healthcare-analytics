package solver.ip;

import ilog.concert.IloException;
import ilog.concert.IloNumVarType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Optional;

public class Main {

    private static String filename;
    private static Timer watch;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java Main <file>");
            return;
        }

        CliArgs parser = new CliArgs(args);

        String input = args[0];
        Path path = Paths.get(input);
        String filename = path.getFileName().toString();
        Main.filename = filename;

        Timer watch = new Timer();
        Main.watch = watch;
        watch.start();
        IPInstance instance = DataParser.parseIPFile(input);

		/*
			Parse command line args here!
		 */
        instance.solveType = IPInstance.SolveType.valueOf(parser.switchValue("-solveType", "solveFloat"));
        int verbosity = parser.switchIntegerValue("-verbosity", 0);

//        if (verbosity <= 5) {
//            instance.cplex.setOut(null);
//        }

        try {
            System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
            switch (instance.solveType) {
                case solveFloat: {
                    Optional<Integer> solution = instance.solve();

                    watch.stop();

                    if (solution.isPresent()) {
                        System.out.println("Instance: " + filename + " Time: " + String.format("%.2f", watch.getTime()) + " Result: " + solution.get() + " Solution: OPT");
                    } else {
                        System.out.println("Instance: " + filename + " Time: " + String.format("%.2f", watch.getTime()) + " Result: --" + " Solution: FAIL");
                    }
                    break;
                }
                case solveInt: {
                    instance.varType = IloNumVarType.Int;
                    Optional<IPInstance.SolveLPReturn> solution = instance.solveLP(new HashMap<>());
                    watch.stop();
                    if (solution.isPresent()) {
                        System.out.println("Instance: " + filename + " Time: " + String.format("%.2f", watch.getTime()) + " Result: " + solution.get().totalCost + " Solution: OPT");
                    } else {
                        System.out.println("Instance: " + filename + " Time: " + String.format("%.2f", watch.getTime()) + " Result: --" + " Solution: FAIL");
                    }
                    break;
                }
                default:
                    System.err.println("Unhandled solveType: " + instance.solveType);
                    System.exit(1);

            }
        } catch (IloException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void printAndExit(Optional<Integer> solution) {
        if (solution.isPresent()) {
            System.out.println("Instance: " + filename + " Time: " + String.format("%.2f", watch.getTime()) + " Result: " + solution.get() + " Solution: OPT");
        } else {
            System.out.println("Instance: " + filename + " Time: " + String.format("%.2f", watch.getTime()) + " Result: --" + " Solution: FAIL");
        }

        System.exit(0);
    }
}
