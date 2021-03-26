package solver.ip;

import ilog.cplex.*;
import ilog.concert.*;

import java.util.*;
import java.util.stream.IntStream;

public class IPInstance {
    // IBM Ilog Cplex Solver
    IloCplex cplex;

    int numTests;            // number of tests
    int numDiseases;        // number of diseases
    double[] costOfTest;  // [numTests] the cost of each test
    int[][] A;            // [numTests][numDiseases] 0/1 matrix if test is positive for disease

    Optional<Double> minCost;

    Map<Integer, List<DiseasePair>> testDifferentiates;
    Map<DiseasePair, List<Integer>> diseasesDifferentiatedBy;

    int[] testOrder;
    int [] orderedTestOrder;

    private class DiseasePair {
        public int d1, d2;
        public DiseasePair(int d1, int d2) {
            this.d1 = d1;
            this.d2 = d2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DiseasePair that = (DiseasePair) o;
            return d1 == that.d1 && d2 == that.d2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(d1, d2);
        }
    }

    SolveType solveType = SolveType.solveFloat;
    IloNumVarType varType = IloNumVarType.Float;

    enum SolveType {
        solveFloat,
        solveInt
    }

    public IPInstance() {
        super();
    }

    void init(int numTests, int numDiseases, double[] costOfTest, int[][] A) {
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
    }

    public Optional<Integer> solve() throws IloException {
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

        /*
            Setup recursion here!
         */

        Map<Integer, Boolean> setTests = new HashMap<Integer, Boolean>();

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

        double[] numDiffer = new double[numTests];
        for (int t = 0; t < numTests; t ++) {
            int posCount = 0, negCount = 0;

            for (int d = 0; d < numDiseases; d ++) {
                if (A[t][d] == 1) {
                    posCount ++;
                } else {
                    negCount ++;
                }
            }
            numDiffer[t] = (posCount * negCount) / costOfTest[t];
        }
        
        orderedTestOrder = IntStream.range(0, numTests)
                .boxed().sorted((i, j) -> (int) (numDiffer[j] - numDiffer[i]))
                .mapToInt(ele -> ele).toArray();



        solveRecursive(setTests);
        if (minCost.isPresent()) {
            return Optional.of((int) Math.ceil(minCost.get()));
        } else {
            return Optional.empty();
        }
    }

    private void solveRecursive(Map<Integer, Boolean> setTests) throws IloException {

        // solve with setTests constraints

        // can get:
        // linear solution - check if we need to prune
        // integer solution - check if we need to better or worse
        // infeasible

//        Timer timer = new Timer();
//        timer.start();
//        timer.stop();
//        if (timer.getTime() > 1) {
//            System.out.println(timer.getTime());
//        }

        Optional<SolveLPReturn> linearResult = this.solveLP(setTests);

        // No best solution cost yet
        if (!minCost.isPresent()) {
            // Found an integer solution
            if (linearResult.isPresent() && linearResult.get().isInteger) {
                // Update best solution cost
                minCost = Optional.of(linearResult.get().totalCost);
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
                }

            }
            else {
                if (!minCost.isPresent() || linearResult.get().totalCost < minCost.get()) {
                    // keep going
                    // set another variable

                    // WHERE WE DECIDE ON THE DISEASE TO SPLIT ON //

//                    Timer timer = new Timer();
//                    timer.start();
//                    double[] numDiffer = new double[numTests];
//                    for (int d1 = 0; d1 < numDiseases; d1 ++) {
//                        for (int d2 = d1 + 1; d2 < numDiseases; d2 ++) {
//                            // Check if already differentiated
//                            boolean alreadyDifferentiated = false;
//                            for (int t : diseasesDifferentiatedBy.get(new DiseasePair(d1, d2))) {
//                                if (setTests.containsKey(t)) {
//                                    alreadyDifferentiated = true;
//                                    break;
//                                }
//                            }
//
//                            if (alreadyDifferentiated) {
//                                continue;
//                            }
//
//                            // Update numDiffers if not
//                            for (int t : diseasesDifferentiatedBy.get(new DiseasePair(d1, d2))) {
//                                numDiffer[t] ++;
//                            }
//                        }
//                    }
//
//                    for (int t = 0; t < numTests; t ++) {
//                        numDiffer[t] /= costOfTest[t];
//                    }
//                    timer.stop();
//                    if (timer.getTime() > 0.01) {
//                        System.out.println(timer.getTime());
//                    }
//
//                    orderedTestOrder = IntStream.range(0, numTests)
//                            .boxed().sorted((i, j) -> (int) (numDiffer[j] - numDiffer[i]))
//                            .mapToInt(ele -> ele).toArray();


                    int testChoice = -1;
                    for (int i = 0; i < numTests; i ++) {
                        int currTest = orderedTestOrder[i];
                        if (!setTests.containsKey(currTest)) {
                            testChoice = currTest;
                            break;
                        }
                    }
                    if (testChoice == -1) {
                        System.err.println("No more tests to set!");
                        System.exit(1);
                    }

                    Map<Integer, Boolean> trueMap, falseMap;
                    trueMap = new HashMap<>(setTests);
                    trueMap.put(testChoice, true);
                    falseMap = new HashMap<>(setTests);
                    falseMap.put(testChoice, false);

                    this.solveRecursive(trueMap);

                    this.solveRecursive(falseMap);



                }
            }
        }

        return;
    }

    public class SolveLPReturn {
        public double totalCost;
        public boolean isInteger;

        public SolveLPReturn(double totalCost, boolean isInteger) {
            this.totalCost = totalCost;
            this.isInteger = isInteger;
        }
    }

    public Optional<SolveLPReturn> solveLP(Map<Integer, Boolean> setTests) throws IloException {
        IloCplex cplex = new IloCplex();
        cplex.setOut(null);
        IloNumVar[] useTest = cplex.numVarArray(numTests, 0, 1, varType);

        for (Integer test : setTests.keySet()) {
            cplex.addEq(useTest[test], setTests.get(test) ? 1.0 : 0.0);
        }

        /*
            For each pair of disease d1, d2:
                Check that there is some test t for which A[t][d1] != A[t][d2] (and useTest[t] = 1)
            Forall d1, d2, Exists t | (A[t][d1] != A[t][d2] and useTest[t] = 1)
         */

        for (int d1 = 0; d1 < numDiseases - 1; d1 ++) {
            for (int d2 = d1 + 1; d2 < numDiseases; d2 ++) {
                IloNumExpr canDifferentiate = cplex.numExpr();
                for (int t = 0; t < numTests; t++) {
                    /*
                        If the test differentiates the two diseases,
                        add it to canDifferentiate.
                     */

                    if (A[t][d1] != A[t][d2]) {
                        canDifferentiate = cplex.sum(canDifferentiate, useTest[t]);
                    }
                }
                // Checks that there is at least one test that differs for the 2 diseases
                cplex.addGe(canDifferentiate, 1); // slack
            }
        }

        // Get cost of used tests
        IloNumExpr totalCost = cplex.scalProd(useTest, costOfTest);
        cplex.addMinimize(totalCost);


        if (cplex.solve()) {
//            System.out.print("Used tests:");
//            for (int i = 0; i < numTests; i ++) {
//                if (cplex.getValue(useTest[i]) == 1) {
//                    System.out.print(" " + i);
//                }
//            }
//            System.out.println();
//            System.out.print("Cost of tests:");
//            for (int i = 0; i < numTests; i ++) {
//                if (cplex.getValue(useTest[i]) == 1) {
//                    System.out.print(" " + costOfTest[i]);
//                }
//            }
//            System.out.println();
//            for (int d1 = 0; d1 < numDiseases - 1; d1 ++) {
//                for (int d2 = d1 + 1; d2 < numDiseases; d2 ++) {
//                    boolean differed = false;
//                    for (int t = 0; t < numTests; t ++) {
//                        if ((cplex.getValue(useTest[t]) == 1) && (A[t][d1] != A[t][d2])) {
//                            differed = true;
//                            System.out.println("Diseases " + d1 + " and " + d2 + " diff by test " + t);
//                        }
//                    }
//                    if (!differed) {
//                        System.out.println("Diseases " + d1 + " and " + d2 + " not differed :(");
//                    }
//                  }
//            }
            boolean isInteger = true;
            for (int test = 0; test < numTests; test ++) {
                double testUsed = cplex.getValue(useTest[test]);
                isInteger = isInteger && (testUsed == 0 || testUsed == 1);
            }
            return Optional.of(new SolveLPReturn(cplex.getObjValue(), isInteger));
        } else {
            return Optional.empty();
        }
    }

    private Optional<Double> solveDual() throws IloException {
        IloCplex cplex = new IloCplex();
        IloNumVar[] useTest = cplex.numVarArray(numTests, 0, 1, varType);

        if (cplex.solve()) {
            return Optional.of(cplex.getObjValue());
        } else {
            return Optional.empty();
        }
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



// turn into linear time for finding differences - check
// while loop instead of recursion
// copying vs recalculating
// dynamically update
// alternating high numbers vs low differences