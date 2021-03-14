package solver.ip;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java Main <file>");
            return;
        }

        CliArgs parser = new CliArgs(args);

        String input = args[0];
        Path path = Paths.get(input);
        String filename = path.getFileName().toString();

        Timer watch = new Timer();
        watch.start();

		/*
			Parse command line args here!
		 */

        IPInstance instance = DataParser.parseIPFile(input);
        Optional<Integer> solution = instance.solve();

        watch.stop();

        if (solution.isPresent()) {
            System.out.println("Instance: " + filename + " Time: " + String.format("%.2f", watch.getTime()) + " Result: " + solution.get() + " Solution: OPT");
        } else {
            System.out.println("Instance: " + filename + " Time: " + String.format("%.2f", watch.getTime()) + " Result: --" + " Solution: FAIL");
        }
    }
}
