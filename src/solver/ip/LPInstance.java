package solver.ip;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LPInstance {
    IloCplex cplex;

    int numTests;            // number of tests
    int numDiseases;        // number of diseases
    double[] costOfTest;  // [numTests] the cost of each test
    int[][] A;            // [numTests][numDiseases] 0/1 matrix if test is positive for disease

    static int numInvocations;

    Map<Integer, List<DiseasePair>> testDifferentiates;
    Map<DiseasePair, List<Integer>> diseasesDifferentiatedBy;

    public LPInstance(int numTests, int numDiseases, double[] costOfTest, int[][] A, Map<Integer, List<DiseasePair>> testDifferentiates, Map<DiseasePair, List<Integer>> diseasesDifferentiatedBy) {
        this.numTests = numTests;
        this.numDiseases = numDiseases;
        this.costOfTest = costOfTest;
        this.A = A;

        this.testDifferentiates = testDifferentiates;
        this.diseasesDifferentiatedBy = diseasesDifferentiatedBy;

        this.numInvocations = 0;
    }

    public class SolveLPReturn {
        public Map<Integer, Boolean> setTests;
        public double totalCost;
        public boolean isInteger;
        public double numTestsUsed;

        public SolveLPReturn(Map<Integer, Boolean> setTests, double totalCost, boolean isInteger, double numTestsUsed) {
            this.setTests = setTests;
            this.totalCost = totalCost;
            this.isInteger = isInteger;
            this.numTestsUsed = numTestsUsed;
        }
    }

    public Optional<SolveLPReturn> solveLP(Map<Integer, Boolean> setTests) throws IloException {
        numInvocations++;
        Timer lpSolveTimer = new Timer();
        lpSolveTimer.start();

        IloCplex cplex = new IloCplex();
        cplex.setOut(null);
        IloNumVar[] useTest = cplex.numVarArray(numTests, 0, 1, Settings.varType);

        for (Integer test : setTests.keySet()) {
            cplex.addEq(useTest[test], setTests.get(test) ? 1.0 : 0.0);
        }

        /*
            For each pair of disease d1, d2:
                Check that there is some test t for which A[t][d1] != A[t][d2] (and useTest[t] = 1)
            Forall d1, d2, Exists t | (A[t][d1] != A[t][d2] and useTest[t] = 1)
         */

        Timer lpSolveForLoopTimer = new Timer();
        lpSolveForLoopTimer.start();
        for (int d1 = 0; d1 < numDiseases - 1; d1 ++) {
            for (int d2 = d1 + 1; d2 < numDiseases; d2 ++) {
                IloNumExpr canDifferentiate = cplex.numExpr();

                for (int t : this.diseasesDifferentiatedBy.get(new DiseasePair(d1, d2))) {
                    canDifferentiate = cplex.sum(canDifferentiate, useTest[t]);
                }

                // Checks that there is at least one test that differs for the 2 diseases
                cplex.addGe(canDifferentiate, 1); // slack
            }
        }
        lpSolveForLoopTimer.stop();
        Timer.lpSolveForLoopTimer.addTime(lpSolveForLoopTimer.getTotalTime());

        // Get cost of used tests
        IloNumExpr totalCost = cplex.scalProd(useTest, costOfTest);
        cplex.addMinimize(totalCost);


        Optional<SolveLPReturn> ret;
        Timer cplexSolveTimer = new Timer();
        cplexSolveTimer.start();
        boolean sat = cplex.solve();
        cplexSolveTimer.stop();
        Timer.cplexSolveTimer.addTime(cplexSolveTimer.getTotalTime());

        if (sat) {
            double objValue = cplex.getObjValue();
            boolean isInteger = true;
            double numTestsUsed = 0;
            for (int test = 0; test < numTests; test ++) {
                double testUsed = cplex.getValue(useTest[test]);
                isInteger = isInteger && (testUsed == 0 || testUsed == 1);
                numTestsUsed += testUsed;
            }

            ret = Optional.of(new SolveLPReturn(setTests, objValue, isInteger, numTestsUsed));
        } else {
            ret = Optional.empty();
        }

        cplex.end();
        lpSolveTimer.stop();
        Timer.lpSolveTimer.addTime(lpSolveTimer.getTotalTime());
        return ret;
    }

    private void prettyPrint(IloNumExpr[] useTest) throws IloException {
        System.out.print("Used tests:");
        for (int i = 0; i < numTests; i ++) {
            if (cplex.getValue(useTest[i]) == 1) {
                System.out.print(" " + i);
            }
        }
        System.out.println();
        System.out.print("Cost of tests:");
        for (int i = 0; i < numTests; i ++) {
            if (cplex.getValue(useTest[i]) == 1) {
                System.out.print(" " + costOfTest[i]);
            }
        }
        System.out.println();
        for (int d1 = 0; d1 < numDiseases - 1; d1 ++) {
            for (int d2 = d1 + 1; d2 < numDiseases; d2 ++) {
                boolean differed = false;
                for (int t = 0; t < numTests; t ++) {
                    if ((cplex.getValue(useTest[t]) == 1) && (A[t][d1] != A[t][d2])) {
                        differed = true;
                        System.out.println("Diseases " + d1 + " and " + d2 + " diff by test " + t);
                    }
                }
                if (!differed) {
                    System.out.println("Diseases " + d1 + " and " + d2 + " not differed :(");
                }
              }
        }
    }
}
