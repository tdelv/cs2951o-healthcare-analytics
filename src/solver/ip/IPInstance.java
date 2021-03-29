package solver.ip;

import ilog.concert.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class IPInstance {

    int numTests;            // number of tests
    int numDiseases;        // number of diseases
    double[] costOfTest;  // [numTests] the cost of each test
    int[][] A;            // [numTests][numDiseases] 0/1 matrix if test is positive for disease

    LPInstance lpInstance;

    Optional<Double> minCost;

    Map<Integer, List<DiseasePair>> testDifferentiates;
    Map<DiseasePair, List<Integer>> diseasesDifferentiatedBy;
    int [] orderedTestOrder;

    int numPrune = 0, numInt = 0, numInfeasible = 0;

    public IPInstance(int numTests, int numDiseases, double[] costOfTest, int[][] A) {
        assert (numTests >= 0) : "Init error: numtests should be non-negative " + numTests;
        assert (numDiseases >= 0) : "Init error: numtests should be non-negative " + numTests;
        assert (costOfTest != null) : "Init error: costOfTest cannot be null";
        assert (costOfTest.length == numTests) : "Init error: costOfTest length differ from numTests" + costOfTest.length + " vs. " + numTests;
        assert (A != null) : "Init error: A cannot be null";
        assert (A.length == numTests) : "Init error: Number of rows in A differ from numTests" + A.length + " vs. " + numTests;
        assert (A[0].length == numDiseases) : "Init error: Number of columns in A differ from numDiseases" + A[0].length + " vs. " + numDiseases;

        this.numTests = numTests;
        this.numDiseases = numDiseases;
        this.costOfTest = new double[numTests];
        for (int i = 0; i < numTests; i++)
            this.costOfTest[i] = costOfTest[i];
        this.A = new int[numTests][numDiseases];
        for (int i = 0; i < numTests; i++)
            for (int j = 0; j < numDiseases; j++)
                this.A[i][j] = A[i][j];

        setup();

        this.lpInstance = new LPInstance(this.numTests, this.numDiseases, this.costOfTest, this.A, this.testDifferentiates, this.diseasesDifferentiatedBy);
    }

    public Optional<Integer> solveWhileSkip() throws IloException {
        setup();

        Stack<Map<Integer, Boolean>> lpStack = new Stack<>();
        lpStack.add(new HashMap<>());
        while (!(lpStack.isEmpty())) {
            Map<Integer, Boolean> setTests = lpStack.pop();
            Optional<LPInstance.SolveLPReturn> solutionOpt = lpInstance.solveLP(setTests);
            if (solutionOpt.isPresent()) {
                LPInstance.SolveLPReturn solution = solutionOpt.get();
                boolean betterSolution = !minCost.isPresent() || solution.totalCost <= minCost.get();

                if (betterSolution) {
                    if (solution.isInteger) {
                        minCost = Optional.of(solution.totalCost);
                    } else {

                        List<Integer> tests = chooseTest(setTests);
                        int numSkip = Math.min(tests.size(), Settings.skip);

                        for (int i = 0; i < numSkip; i ++) {
                            Map<Integer, Boolean> map = new HashMap<>(setTests);
                            for (int j = 0; j < i; j ++) {
                                map.put(tests.get(tests.size() - (j + 1)), false);
                            }
                            map.put(tests.get(tests.size() - (i + 1)), true);
                            lpStack.add(map);
                        }

                        {
                            Map<Integer, Boolean> falseMap = new HashMap<>(setTests);
                            for (int j = 0; j < numSkip; j ++) {
                                falseMap.put(tests.get(tests.size() - (j + 1)), false);
                            }
                            lpStack.add(falseMap);
                        }

                    }
                }
            }
        }

        if (minCost.isPresent()) {
            return Optional.of((int) Math.ceil(minCost.get()));
        } else {
            return Optional.empty();
        }
    }

    public Optional<Integer> solveWhileParallel() throws IloException, InterruptedException {
        setup();

        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(Settings.numWorkers);

        Queue<LPInstance.SolveLPReturn> lpStack = new PriorityQueue<>((ret1, ret2) -> (int)(ret1.totalCost - ret2.totalCost));
        Optional<LPInstance.SolveLPReturn> rootReturn = lpInstance.solveLP(new HashMap<>());
        if (rootReturn.isPresent()) {
            lpStack.add(rootReturn.get());
        } else {
            return Optional.empty();
        }

        AtomicInteger numRunning = new AtomicInteger(0);
        while (!(lpStack.isEmpty() && (numRunning.get() == 0))) {
            while (!lpStack.isEmpty()) {
                numRunning.incrementAndGet();
                LPInstance.SolveLPReturn node = lpStack.remove();
                pool.execute(() -> {
                    if (!minCost.isPresent() || node.totalCost < minCost.get()) {

                        int testChoice = chooseWithFadeoff(node.setTests);

                        List<Map<Integer, Boolean>> childMaps = new ArrayList<>();
                        {
                            Map<Integer, Boolean> trueMap = new HashMap<>(node.setTests);
                            Map<Integer, Boolean> falseMap = new HashMap<>(node.setTests);

                            trueMap.put(testChoice, true);
                            falseMap.put(testChoice, false);

                            childMaps.add(trueMap);
                            childMaps.add(falseMap);
                        }

                        for (Map<Integer, Boolean> childMap : childMaps) {
                            Optional<LPInstance.SolveLPReturn> childResult = null;
                            try {
                                childResult = lpInstance.solveLP(childMap);
                            } catch (IloException e) {
                                e.printStackTrace();
                                System.exit(1);
                            }
                            if (childResult.isPresent()) {
                                LPInstance.SolveLPReturn childNode = childResult.get();

                                // If it's worse than best, skip
                                if (minCost.isPresent() && childNode.totalCost >= minCost.get()) {
                                    continue;
                                }

                                if (childNode.isInteger) {
                                    minCost = Optional.of(childNode.totalCost);
                                } else {
                                    lpStack.add(childNode);
                                }
                            }
                        }
                    }
                    numRunning.decrementAndGet();
                });
            }
        }

        if (minCost.isPresent()) {
            return Optional.of((int) Math.ceil(minCost.get()));
        } else {
            return Optional.empty();
        }
    }

    public Optional<Integer> solveWhile() throws IloException {
        setup();

        Stack<Map<Integer, Boolean>> lpStack = new Stack<>();
        lpStack.add(new HashMap<>());
        while (!(lpStack.isEmpty())) {
            Map<Integer, Boolean> setTests = lpStack.pop();
            Optional<LPInstance.SolveLPReturn> solutionOpt = lpInstance.solveLP(setTests);
            if (solutionOpt.isPresent()) {
                LPInstance.SolveLPReturn solution = solutionOpt.get();
                boolean betterSolution = !minCost.isPresent() || solution.totalCost <= minCost.get();

                if (betterSolution) {
                    if (solution.isInteger) {
                        minCost = Optional.of(solution.totalCost);
                    } else {

                        int testChoice = chooseWithFadeoff(setTests);

                        Map<Integer, Boolean> trueMap, falseMap;
                        trueMap = new HashMap<>(setTests);
                        trueMap.put(testChoice, true);
                        falseMap = new HashMap<>(setTests);
                        falseMap.put(testChoice, false);
                        lpStack.push(trueMap);
                        lpStack.push(falseMap);
                    }
                }
            }
        }

        if (minCost.isPresent()) {
            return Optional.of((int) Math.ceil(minCost.get()));
        } else {
            return Optional.empty();
        }
    }

    public Optional<Integer> solveRecursive() throws IloException {
        setup();
        solveRecursive(new HashMap<>());
        if (minCost.isPresent()) {
            return Optional.of((int) Math.ceil(minCost.get()));
        } else {
            return Optional.empty();
        }
    }

    private void solveRecursive(Map<Integer, Boolean> setTests) throws IloException {

        Optional<LPInstance.SolveLPReturn> linearResult = lpInstance.solveLP(setTests);

        // No best solution cost yet
        if (!minCost.isPresent()) {
            // Found an integer solution
            if (linearResult.isPresent() && linearResult.get().isInteger) {
                // Update best solution cost
                minCost = Optional.of(linearResult.get().totalCost);
                Settings.probabilityUseTest = linearResult.get().numTestsUsed / numTests;
                System.out.println("Cost: " + minCost.get() + "; % Tests used: " + Settings.probabilityUseTest);
            }
        }
        // Found another miscellaneous solution
        if (linearResult.isPresent()) {
            // Integer or float solution
            boolean isInteger = linearResult.get().isInteger;
            if (isInteger) {
                if (linearResult.get().totalCost < minCost.get()) {
                    // Update best solution cost
                    minCost = Optional.of(linearResult.get().totalCost);
                    Settings.probabilityUseTest = linearResult.get().numTestsUsed / numTests;
                    System.out.println("Depth: " + setTests.size() + "; Integer soln " + ++numInt + "; Cost: " + minCost.get() + "; % Tests used: " + Settings.probabilityUseTest);
                }
            } else {
                if (!minCost.isPresent() || linearResult.get().totalCost < minCost.get()) {
                    // keep going
                    // set another variable

                    // WHERE WE DECIDE ON THE DISEASE TO SPLIT ON //

                    if (Settings.rand.nextDouble() < Settings.randRestart) {
                        Settings.randRestart *= Settings.randRestartFadeoff;
                        solveRecursive(new HashMap<>());
                        if (minCost.isPresent()) {
                            Main.printAndExit(Optional.of((int) Math.ceil(minCost.get())));
                        } else {
                            Main.printAndExit(Optional.empty());
                        }

                    }

                    {
                        int testChoice = chooseWithFadeoff(setTests);

                        Map<Integer, Boolean> trueMap, falseMap;
                        trueMap = new HashMap<>(setTests);
                        trueMap.put(testChoice, true);
                        falseMap = new HashMap<>(setTests);
                        falseMap.put(testChoice, false);

                        this.solveRecursive(falseMap);
                        this.solveRecursive(trueMap);
                    }

                }
            }
        }

        return;
    }

    private void setup() {
        /*
            Setup recursion here!
         */
        Timer.setupTimer.start();

        minCost = Optional.empty();

        testDifferentiates = new HashMap<>();
        diseasesDifferentiatedBy = new HashMap<>();
        for (int t = 0; t < numTests; t ++) {
            testDifferentiates.put(t, new ArrayList<>());
        }
        for (int d1 = 0; d1 < numDiseases; d1 ++) {
            for (int d2 = d1 + 1; d2 < numDiseases; d2 ++) {
                DiseasePair dp = new DiseasePair(d1, d2);
                diseasesDifferentiatedBy.put(dp, new ArrayList<>());
                for (int t = 0; t < numTests; t ++) {
                    if (A[t][d1] != A[t][d2]) {
                        testDifferentiates.get(t).add(dp);
                        diseasesDifferentiatedBy.get(dp).add(t);
                    }
                }
            }
        }

        setTestOrder(new HashMap<>());

        Timer.setupTimer.stop();
    }

    private void setTestOrder(Map<Integer, Boolean> setTests) {
        Timer.dynamicUpdateTimer.start();

        double[] numDiffer = new double[numTests];
        for (int d1 = 0; d1 < numDiseases; d1++) {
            for (int d2 = d1 + 1; d2 < numDiseases; d2++) {
                // Check if already differentiated
                boolean alreadyDifferentiated = false;
                for (int t : diseasesDifferentiatedBy.get(new DiseasePair(d1, d2))) {
                    if (setTests.containsKey(t) && setTests.get(t) == true) {
                        alreadyDifferentiated = true;
                        break;
                    }
                }

                if (alreadyDifferentiated) {
                    continue;
                }

                // Update numDiffers if not
                for (int t : diseasesDifferentiatedBy.get(new DiseasePair(d1, d2))) {
                    numDiffer[t]++;
                }
            }
        }

        for (int t = 0; t < numTests; t++) {
            numDiffer[t] /= costOfTest[t];
        }

        orderedTestOrder = IntStream.range(0, numTests)
                .boxed().sorted((i, j) -> (int) (numDiffer[j] - numDiffer[i]))
                .mapToInt(ele -> ele).toArray();

        Timer.dynamicUpdateTimer.stop();
    }

    private List<Integer> chooseTest(Map<Integer, Boolean> setTests) {
        if (Settings.dynamic) {
            setTestOrder(setTests);
        }

        List<Integer> tests = new ArrayList<>();
        for (int i = 0; i < numTests; i++) {
            int currTest = orderedTestOrder[i];
            if (!setTests.keySet().contains(currTest)) {
                tests.add(currTest);
            }
        }
        if (tests.isEmpty()) {
            System.err.println("No more tests to set!");
            System.exit(1);
        }

        return tests;
    }

    private Integer chooseWithFadeoff(Map<Integer, Boolean> setTests) {
        List<Integer> tests = chooseTest(setTests);
        int test = tests.get(0);
        for (int i = 1; i < tests.size(); i ++) {
            if (Settings.rand.nextDouble() < Settings.choiceFadeoff) {
                test = tests.get(i);
            }
        }

        return test;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Number of tests: " + numTests + "\n");
        buf.append("Number of diseases: " + numDiseases + "\n");
        buf.append("Cost of tests: " + Arrays.toString(costOfTest) + "\n");
        buf.append("A:\n");
        for (int i = 0; i < numTests; i++)
            buf.append(Arrays.toString(A[i]) + "\n");
        return buf.toString();
    }
}