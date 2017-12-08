package us.ihmc.commonWalkingControlModules.dynamicPlanning;

import gnu.trove.list.array.TIntArrayList;
import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.DecompositionFactory;
import org.ejml.interfaces.decomposition.SingularValueDecomposition;
import org.ejml.ops.CommonOps;
import us.ihmc.commons.MathTools;
import us.ihmc.commons.PrintTools;
import us.ihmc.robotics.linearAlgebra.DiagonalMatrixTools;
import us.ihmc.robotics.linearAlgebra.MatrixTools;
import us.ihmc.robotics.lists.RecyclingArrayList;

import java.util.List;

public abstract class AbstractDDPSolver<E extends Enum> implements DDPSolverInterface<E>
{
   private static final boolean useDynamicsHessian = false;

   protected final DiscreteHybridDynamics<E> dynamics;
   protected final LQCostFunction costFunction;
   protected final LQCostFunction terminalCostFunction;

   protected final RecyclingArrayList<DenseMatrix64F> stateTrajectory;
   protected final RecyclingArrayList<DenseMatrix64F> controlTrajectory;

   protected final RecyclingArrayList<DenseMatrix64F> desiredStateTrajectory;
   protected final RecyclingArrayList<DenseMatrix64F> desiredControlTrajectory;

   protected final RecyclingArrayList<DenseMatrix64F> updatedStateTrajectory;
   protected final RecyclingArrayList<DenseMatrix64F> updatedControlTrajectory;

   protected final RecyclingArrayList<DenseMatrix64F> feedBackGainTrajectory;
   protected final RecyclingArrayList<DenseMatrix64F> feedForwardTrajectory;

   protected final RecyclingArrayList<DenseMatrix64F> costStateGradientTrajectory;
   protected final RecyclingArrayList<DenseMatrix64F> costControlGradientTrajectory;
   protected final RecyclingArrayList<DenseMatrix64F> costStateHessianTrajectory;
   protected final RecyclingArrayList<DenseMatrix64F> costControlHessianTrajectory;
   protected final RecyclingArrayList<DenseMatrix64F> costStateControlHessianTrajectory;

   protected final DenseMatrix64F hamiltonianStateGradient;
   protected final DenseMatrix64F hamiltonianControlGradient;
   protected final DenseMatrix64F hamiltonianStateHessian;
   protected final DenseMatrix64F hamiltonianControlHessian;
   protected final DenseMatrix64F hamiltonianStateControlHessian;
   protected final DenseMatrix64F hamiltonianControlStateHessian;
   protected final DenseMatrix64F invQuu;

   protected final RecyclingArrayList<DenseMatrix64F> dynamicsStateGradientTrajectory;
   protected final RecyclingArrayList<DenseMatrix64F> dynamicsControlGradientTrajectory;
   protected final DenseMatrix64F dynamicsStateHessian;
   protected final DenseMatrix64F dynamicsControlHessian;
   protected final DenseMatrix64F dynamicsControlStateHessian;

   protected final RecyclingArrayList<DenseMatrix64F> valueStateGradientTrajectory;
   protected final RecyclingArrayList<DenseMatrix64F> valueStateHessianTrajectory;

   private final DenseMatrix64F Q_XX_col;
   private final DenseMatrix64F Q_UX_col;
   private final DenseMatrix64F Q_UU_col;

   private final DenseMatrix64F tempMatrix = new DenseMatrix64F(0, 0);

   private final SingularValueDecomposition<DenseMatrix64F> decomposer = DecompositionFactory.svd(0, 0, true, true, true);
   protected final boolean debug;

   private static final double minimumModificationFactor = 2;
   private static final double lambdaMax = 1000;
   protected static final double lambdaMin = 1e-10;

   private double modificationFactor = 1e-10;
   protected double lambda;

   protected double lineSearchGain = 1.0;

   public AbstractDDPSolver(DiscreteHybridDynamics<E> dynamics, LQCostFunction costFunction, LQCostFunction terminalCostFunction, boolean debug)
   {
      this.dynamics = dynamics;
      this.costFunction = costFunction;
      this.terminalCostFunction = terminalCostFunction;
      this.debug = debug;

      int stateSize = dynamics.getStateVectorSize();
      int controlSize = dynamics.getControlVectorSize();

      VariableVectorBuilder controlBuilder = new VariableVectorBuilder(controlSize, 1);
      VariableVectorBuilder stateBuilder = new VariableVectorBuilder(stateSize, 1);
      VariableVectorBuilder gainBuilder = new VariableVectorBuilder(controlSize, stateSize);

      stateTrajectory = new RecyclingArrayList<>(1000, stateBuilder);
      controlTrajectory = new RecyclingArrayList<>(1000, controlBuilder);
      desiredStateTrajectory = new RecyclingArrayList<>(1000, stateBuilder);
      desiredControlTrajectory = new RecyclingArrayList<>(1000, controlBuilder);
      updatedStateTrajectory = new RecyclingArrayList<>(1000, stateBuilder);
      updatedControlTrajectory = new RecyclingArrayList<>(1000, controlBuilder);

      feedBackGainTrajectory = new RecyclingArrayList<>(1000, gainBuilder);
      feedForwardTrajectory = new RecyclingArrayList<>(1000, controlBuilder);

      valueStateGradientTrajectory = new RecyclingArrayList<>(1000, new VariableVectorBuilder(stateSize, 1));
      valueStateHessianTrajectory = new RecyclingArrayList<>(1000, new VariableVectorBuilder(stateSize, stateSize));

      costStateGradientTrajectory = new RecyclingArrayList<>(1000, new VariableVectorBuilder(stateSize, 1));
      costControlGradientTrajectory = new RecyclingArrayList<>(1000, new VariableVectorBuilder(controlSize, 1));
      costStateHessianTrajectory = new RecyclingArrayList<>(1000, new VariableVectorBuilder(stateSize, stateSize));
      costControlHessianTrajectory = new RecyclingArrayList<>(1000, new VariableVectorBuilder(controlSize, controlSize));
      costStateControlHessianTrajectory = new RecyclingArrayList<>(1000, new VariableVectorBuilder(stateSize, controlSize));

      hamiltonianStateGradient = new DenseMatrix64F(stateSize, 1);
      hamiltonianControlGradient = new DenseMatrix64F(controlSize, 1);
      hamiltonianStateHessian = new DenseMatrix64F(stateSize, stateSize);
      hamiltonianControlHessian = new DenseMatrix64F(controlSize, controlSize);
      hamiltonianStateControlHessian = new DenseMatrix64F(stateSize, controlSize);
      hamiltonianControlStateHessian = new DenseMatrix64F(controlSize, stateSize);
      invQuu = new DenseMatrix64F(controlSize, controlSize);

      dynamicsStateGradientTrajectory = new RecyclingArrayList<>(1000, new VariableVectorBuilder(stateSize, stateSize));
      dynamicsControlGradientTrajectory = new RecyclingArrayList<>(1000, new VariableVectorBuilder(stateSize, controlSize));
      dynamicsStateHessian = new DenseMatrix64F(stateSize, stateSize);
      dynamicsControlHessian = new DenseMatrix64F(stateSize, stateSize);
      dynamicsControlStateHessian = new DenseMatrix64F(stateSize, stateSize);

      stateTrajectory.clear();
      controlTrajectory.clear();
      desiredStateTrajectory.clear();
      desiredControlTrajectory.clear();
      updatedStateTrajectory.clear();
      updatedControlTrajectory.clear();

      valueStateHessianTrajectory.clear();
      valueStateGradientTrajectory.clear();

      feedBackGainTrajectory.clear();
      feedForwardTrajectory.clear();

      Q_XX_col = new DenseMatrix64F(stateSize, 1);
      Q_UX_col = new DenseMatrix64F(controlSize, 1);
      Q_UU_col = new DenseMatrix64F(controlSize, 1);
   }

   public void initializeFromLQRSolution(E dynamicsState, RecyclingArrayList<DenseMatrix64F> stateTrajectory, RecyclingArrayList<DenseMatrix64F> controlTrajectory,
                                         RecyclingArrayList<DenseMatrix64F> desiredStateTrajectory, RecyclingArrayList<DenseMatrix64F> desiredControlTrajectory,
                                         RecyclingArrayList<DenseMatrix64F> feedBackGainTrajectory, RecyclingArrayList<DenseMatrix64F> feedForwardTrajectory)
   {
      this.stateTrajectory.clear();
      this.controlTrajectory.clear();
      this.feedBackGainTrajectory.clear();
      this.feedForwardTrajectory.clear();
      this.desiredStateTrajectory.clear();
      this.desiredControlTrajectory.clear();
      this.updatedControlTrajectory.clear();
      this.updatedStateTrajectory.clear();

      valueStateHessianTrajectory.clear();
      valueStateGradientTrajectory.clear();

      costStateGradientTrajectory.clear();
      costControlGradientTrajectory.clear();
      costStateHessianTrajectory.clear();
      costControlHessianTrajectory.clear();
      costStateControlHessianTrajectory.clear();

      dynamicsControlGradientTrajectory.clear();
      dynamicsStateGradientTrajectory.clear();

      for (int i = 0; i < stateTrajectory.size(); i++)
      {
         this.stateTrajectory.add().set(stateTrajectory.get(i));
         this.controlTrajectory.add().set(controlTrajectory.get(i));
         this.desiredStateTrajectory.add().set(desiredStateTrajectory.get(i));
         this.desiredControlTrajectory.add().set(desiredControlTrajectory.get(i));
         this.feedBackGainTrajectory.add().set(feedBackGainTrajectory.get(i));
         this.feedForwardTrajectory.add().set(feedForwardTrajectory.get(i));
         this.updatedControlTrajectory.add().zero();
         this.updatedStateTrajectory.add().zero();

         valueStateHessianTrajectory.add().zero();
         valueStateGradientTrajectory.add().zero();

         costStateGradientTrajectory.add().zero();
         costControlGradientTrajectory.add().zero();
         costStateHessianTrajectory.add().zero();
         costControlHessianTrajectory.add().zero();
         costStateControlHessianTrajectory.add().zero();

         dynamicsControlGradientTrajectory.add().zero();
         dynamicsStateGradientTrajectory.add().zero();
      }

      // FIXME
      forwardPass(dynamicsState, 0, stateTrajectory.size() - 1, stateTrajectory.get(0), updatedStateTrajectory, updatedControlTrajectory);

      for (int i = 0; i < updatedControlTrajectory.size(); i++)
      {
         this.stateTrajectory.get(i).set(updatedStateTrajectory.get(i));
         this.controlTrajectory.get(i).set(updatedControlTrajectory.get(i));
      }
   }

   public void initializeTrajectoriesFromDesireds(DenseMatrix64F initialCoM, RecyclingArrayList<DenseMatrix64F> desiredStateTrajectory, RecyclingArrayList<DenseMatrix64F> desiredControlTrajectory)
   {
      this.stateTrajectory.clear();
      this.controlTrajectory.clear();
      this.feedBackGainTrajectory.clear();
      this.feedForwardTrajectory.clear();
      this.desiredStateTrajectory.clear();
      this.desiredControlTrajectory.clear();
      this.updatedControlTrajectory.clear();
      this.updatedStateTrajectory.clear();

      valueStateHessianTrajectory.clear();
      valueStateGradientTrajectory.clear();

      costStateGradientTrajectory.clear();
      costControlGradientTrajectory.clear();
      costStateHessianTrajectory.clear();
      costControlHessianTrajectory.clear();
      costStateControlHessianTrajectory.clear();

      dynamicsControlGradientTrajectory.clear();
      dynamicsStateGradientTrajectory.clear();

      for (int i = 0; i < desiredStateTrajectory.size(); i++)
      {
         this.stateTrajectory.add().set(desiredStateTrajectory.get(i));
         this.controlTrajectory.add().set(desiredControlTrajectory.get(i));
         this.desiredStateTrajectory.add().set(desiredStateTrajectory.get(i));
         this.desiredControlTrajectory.add().set(desiredControlTrajectory.get(i));
         this.feedBackGainTrajectory.add().zero();
         this.feedForwardTrajectory.add().zero();
         this.updatedControlTrajectory.add().zero();
         this.updatedStateTrajectory.add().zero();
         valueStateHessianTrajectory.add().zero();
         valueStateGradientTrajectory.add().zero();

         costStateGradientTrajectory.add().zero();
         costControlGradientTrajectory.add().zero();
         costStateHessianTrajectory.add().zero();
         costControlHessianTrajectory.add().zero();
         costStateControlHessianTrajectory.add().zero();

         dynamicsControlGradientTrajectory.add().zero();
         dynamicsStateGradientTrajectory.add().zero();
      }

      stateTrajectory.getFirst().set(initialCoM);
   }

   void computeFunctionApproximations(E dynamicsState, int startIndex, int endIndex)
   {
      for (int t = startIndex; t <= endIndex; t++)
      {
         DenseMatrix64F currentState = stateTrajectory.getAndGrowIfNeeded(t);
         DenseMatrix64F currentControl = controlTrajectory.getAndGrowIfNeeded(t);
         DenseMatrix64F desiredState = desiredStateTrajectory.get(t);
         DenseMatrix64F desiredControl = desiredControlTrajectory.get(t);

         dynamics.getDynamicsStateGradient(dynamicsState, currentState, currentControl, dynamicsStateGradientTrajectory.get(t));
         dynamics.getDynamicsControlGradient(dynamicsState, currentState, currentControl, dynamicsControlGradientTrajectory.get(t));

         costFunction.getCostStateGradient(currentControl, currentState, desiredControl, desiredState, costStateGradientTrajectory.get(t));
         costFunction.getCostControlGradient(currentControl, currentState, desiredControl, desiredState, costControlGradientTrajectory.get(t));
         costFunction.getCostStateHessian(currentControl, currentState, costStateHessianTrajectory.get(t));
         costFunction.getCostControlHessian(currentControl, currentState, costControlHessianTrajectory.get(t));
         costFunction.getCostControlGradientOfStateGradient(currentControl, currentState, costStateControlHessianTrajectory.get(t));
      }
   }


   private final DenseMatrix64F U = new DenseMatrix64F(0, 0);
   private final DenseMatrix64F W = new DenseMatrix64F(0, 0);
   private final DenseMatrix64F V = new DenseMatrix64F(0, 0);
   boolean computeFeedbackGainAndFeedForwardTerms(DenseMatrix64F hamiltonianControlGradient, DenseMatrix64F hamiltonianControlHessian,
                                                  DenseMatrix64F hamiltonianControlStateHessian, DenseMatrix64F feedbackGainToPack, DenseMatrix64F feedForwardControlToPack)
   {
      // insure that the hessian is positive definite
      int controlSize = hamiltonianControlHessian.numCols;
      U.reshape(controlSize, controlSize);
      W.reshape(controlSize, controlSize);
      V.reshape(controlSize, controlSize);
      tempMatrix.reshape(controlSize, controlSize);

      decomposer.decompose(hamiltonianControlHessian);
      decomposer.getU(U, true);
      decomposer.getW(W);
      decomposer.getV(V, false);

      MatrixTools.addDiagonal(W, lambda);
      for (int i = 0; i < W.getNumRows(); i++)
      {
         if (W.get(i, i) <= 0.0)
         {
            if (debug) PrintTools.info("Hessian is not positive definite, aborting backward pass.");
            return false;
         }
      }

      DiagonalMatrixTools.invertDiagonalMatrix(W);
      CommonOps.mult(V, W, tempMatrix);
      CommonOps.mult(tempMatrix, U, invQuu);

      // K = -inv(Quu) Qux
      CommonOps.mult(-1.0, invQuu, hamiltonianControlStateHessian, feedbackGainToPack);

      // du = -inv(Quu) Qu
      CommonOps.mult(-1.0, invQuu, hamiltonianControlGradient, feedForwardControlToPack);

      return true;
   }

   void updateHamiltonianApproximations(E dynamicsState, int t, DenseMatrix64F costStateGradient, DenseMatrix64F costControlGradient, DenseMatrix64F costStateHessian,
                                        DenseMatrix64F costControlHessian, DenseMatrix64F costStateControlHessian, DenseMatrix64F dynamicsStateGradient,
                                        DenseMatrix64F dynamicsControlGradient, DenseMatrix64F valueStateGradient, DenseMatrix64F valueStateHessian,
                                        DenseMatrix64F hamiltonianStateGradientToPack, DenseMatrix64F hamiltonianControlGradientToPack,
                                        DenseMatrix64F hamiltonianStateHessianToPack, DenseMatrix64F hamiltonianControlHessianToPack,
                                        DenseMatrix64F hamiltonianStateControlHessianToPack, DenseMatrix64F hamiltonianControlStateHessianToPack)
   {
      // Qx = Lx + A' Vx
      hamiltonianStateGradientToPack.set(costStateGradient);
      CommonOps.multAddTransA(dynamicsStateGradient, valueStateGradient, hamiltonianStateGradientToPack);

      // Qu = Lu + B' Vx
      hamiltonianControlGradientToPack.set(costControlGradient);
      CommonOps.multAddTransA(dynamicsControlGradient, valueStateGradient, hamiltonianControlGradientToPack);

      // Qxx = Lxx + A' Vxx A
      hamiltonianStateHessianToPack.set(costStateHessian);
      addMultQuad(dynamicsStateGradient, valueStateHessian, dynamicsStateGradient, hamiltonianStateHessianToPack);

      // Qxu = Lxu + A' Vxx B
      hamiltonianStateControlHessianToPack.set(costStateControlHessian);
      addMultQuad(dynamicsStateGradient, valueStateHessian, dynamicsControlGradient, hamiltonianStateControlHessianToPack);

      // Quu = Luu + B' Vxx B
      hamiltonianControlHessianToPack.set(costControlHessian);
      addMultQuad(dynamicsControlGradient, valueStateHessian, dynamicsControlGradient, hamiltonianControlHessianToPack);


      if (useDynamicsHessian)
      {
         DenseMatrix64F currentState = stateTrajectory.get(t);
         DenseMatrix64F currentControl = controlTrajectory.get(t);

         for (int stateIndex = 0; stateIndex < dynamics.getStateVectorSize(); stateIndex++)
         {
            dynamics.getDynamicsStateHessian(dynamicsState, stateIndex, currentState, currentControl, dynamicsStateHessian);
            dynamics.getDynamicsStateGradientOfControlGradient(dynamicsState, stateIndex, currentState, currentControl,
                                                               dynamicsControlStateHessian);

            CommonOps.multTransA(dynamicsStateHessian, valueStateGradient, Q_XX_col);
            MatrixTools.addMatrixBlock(hamiltonianStateHessianToPack, 0, stateIndex, Q_XX_col, 0, 0, dynamics.getStateVectorSize(), 1, 1.0);

            CommonOps.multTransA(dynamicsControlStateHessian, valueStateGradient, Q_UX_col);
            MatrixTools.addMatrixBlock(hamiltonianControlStateHessianToPack, 0, stateIndex, Q_UX_col, 0, 0, dynamics.getControlVectorSize(), 1, 1.0);
         }

         for (int controlIndex = 0; controlIndex < dynamics.getControlVectorSize(); controlIndex++)
         {
            dynamics.getDynamicsControlHessian(dynamicsState, controlIndex, currentState, currentControl, dynamicsControlHessian);

            CommonOps.multTransA(dynamicsControlHessian, valueStateGradient, Q_UU_col);
            MatrixTools.addMatrixBlock(hamiltonianControlHessianToPack, 0, controlIndex, Q_UU_col, 0, 0, dynamics.getControlVectorSize(), 1, 1.0);
         }
      }

      // Qux = Qxu'
      CommonOps.transpose(hamiltonianStateControlHessianToPack, hamiltonianControlStateHessianToPack);
   }


   private final DenseMatrix64F stateError = new DenseMatrix64F(0, 0);
   void computeUpdatedControl(DenseMatrix64F currentState, DenseMatrix64F updatedState, DenseMatrix64F feedbackGainMatrix, DenseMatrix64F feedforwardControl,
                              DenseMatrix64F currentControl, DenseMatrix64F updatedControlToPack)
   {
      stateError.reshape(currentState.getNumRows(), 1);
      CommonOps.subtract(updatedState, currentState, stateError);

      // u += K*(xhat - x)
      CommonOps.mult(feedbackGainMatrix, stateError, updatedControlToPack);

      // u = alpha * du + uref
      CommonOps.addEquals(updatedControlToPack, lineSearchGain, feedforwardControl);
      CommonOps.addEquals(updatedControlToPack, currentControl);
   }

   void computePreviousValueApproximation(DenseMatrix64F hamiltonianStateGradient, DenseMatrix64F hamiltonianControlGradient,
                                          DenseMatrix64F hamiltonianStateHessian, DenseMatrix64F hamiltonianStateControlHessian,
                                          DenseMatrix64F feedbackGainMatrix, DenseMatrix64F previousValueStateGradientToPack,
                                          DenseMatrix64F previousValueStateHessianToPack)
   {
      // Vx = Qx + K' Qu
      previousValueStateGradientToPack.set(hamiltonianStateGradient);
      CommonOps.multAddTransA(feedbackGainMatrix, hamiltonianControlGradient, previousValueStateGradientToPack);

      // Vxx = Qxx + Qxu K
      previousValueStateHessianToPack.set(hamiltonianStateHessian);
      CommonOps.multAdd(hamiltonianStateControlHessian, feedbackGainMatrix, previousValueStateHessianToPack);
   }

   /**
    * D = D + A^T *  B * C
    */
   void addMultQuad(DenseMatrix64F A, DenseMatrix64F B, DenseMatrix64F C, DenseMatrix64F DToPack)
   {
      tempMatrix.reshape(A.numCols, B.numCols);
      CommonOps.multTransA(A, B, tempMatrix);
      CommonOps.multAdd(tempMatrix, C, DToPack);
   }

   /**
    * D = D + alpha * A^T *  B * C
    */
   void addMultQuad(double alpha, DenseMatrix64F A, DenseMatrix64F B, DenseMatrix64F C, DenseMatrix64F DToPack)
   {
      tempMatrix.reshape(A.numCols, B.numCols);
      CommonOps.multTransA(alpha, A, B, tempMatrix);
      CommonOps.multAdd(tempMatrix, C, DToPack);
   }

   void applyLevenbergMarquardtHeuristicForHessianRegularization(boolean success)
   {
      if (success)
      {
         modificationFactor = Math.min(1.0 / minimumModificationFactor, modificationFactor / minimumModificationFactor);
         lambda = Math.max(modificationFactor * lambda, lambdaMin);
      }
      else
      {
         modificationFactor = Math.max(minimumModificationFactor, modificationFactor * minimumModificationFactor);
         lambda = MathTools.clamp(lambda * modificationFactor, lambdaMin, lambdaMax);
      }
   }

   public RecyclingArrayList<DenseMatrix64F> getControlTrajectory()
   {
      return controlTrajectory;
   }

   public RecyclingArrayList<DenseMatrix64F> getStateTrajectory()
   {
      return stateTrajectory;
   }

   @Override
   public boolean computeTrajectory(E dynamicsState)
   {
      double cost, cost0;
      cost0 = Double.MAX_VALUE;

      int startIndex = 0;
      int endIndex = stateTrajectory.size() - 1;

      for (int iter = 0; iter < 1000; iter++)
      {
         boolean lastIteration = false;
         computeFunctionApproximations(dynamicsState, startIndex, endIndex);

         for (int iterB = 0; iterB < 20; iterB++)
         {
            boolean hessianWasPD = backwardPass(dynamicsState, startIndex, endIndex, stateTrajectory, controlTrajectory);

            if (hessianWasPD)
            {
               cost = forwardPass(dynamicsState, startIndex, endIndex, stateTrajectory.getFirst(), updatedStateTrajectory, updatedControlTrajectory);

               for (int i = 0; i < updatedControlTrajectory.size(); i++)
               {
                  stateTrajectory.get(i).set(updatedStateTrajectory.get(i));
                  controlTrajectory.get(i).set(updatedControlTrajectory.get(i));
               }

               if (Math.abs(cost - cost0) / Math.abs(cost0) < 1e-1)
                  return true;

               cost0 = cost;

               applyLevenbergMarquardtHeuristicForHessianRegularization(true);
               break;
            }
            else
            {
               if (lastIteration)
                  break;

               applyLevenbergMarquardtHeuristicForHessianRegularization(false);
            }

            if (lambda == lambdaMin)
               lastIteration = true;
         }

      }

      throw new RuntimeException("Didn't converge.");
   }


   @Override
   public boolean computeTrajectory(List<E> dynamicsStates, TIntArrayList startIndices, TIntArrayList endIndices)
   {
      double cost, cost0;
      cost0 = Double.MAX_VALUE;

      for (int iter = 0; iter < 1000; iter++)
      {
         boolean lastIteration = false;
         for (int segment = dynamicsStates.size() - 1; segment >= 0; segment--)
            computeFunctionApproximations(dynamicsStates.get(segment), startIndices.get(segment), endIndices.get(segment));

         for (int iterB = 0; iterB < 20; iterB++)
         {
            boolean hessianWasPD = true;
            for (int segment = dynamicsStates.size() - 1; segment >= 0; segment--)
            {
               hessianWasPD = backwardPass(dynamicsStates.get(segment), startIndices.get(segment), endIndices.get(segment), stateTrajectory, controlTrajectory);
               if (!hessianWasPD)
                  break;
            }

            if (hessianWasPD)
            {
               cost = 0;
               for (int segment = 0; segment < dynamicsStates.size(); segment++)
               {
                  int startIndex = startIndices.get(segment);
                  cost += forwardPass(dynamicsStates.get(segment), startIndex, endIndices.get(segment), stateTrajectory.get(startIndex),
                                     updatedStateTrajectory, updatedControlTrajectory);
               }

               for (int i = 0; i < updatedControlTrajectory.size(); i++)
               {
                  stateTrajectory.get(i).set(updatedStateTrajectory.get(i));
                  controlTrajectory.get(i).set(updatedControlTrajectory.get(i));
               }

               if (Math.abs(cost - cost0) / Math.abs(cost0) < 1e-1)
                  return true;

               cost0 = cost;

               applyLevenbergMarquardtHeuristicForHessianRegularization(true);
               break;
            }
            else
            {
               if (lastIteration)
                  break;

               applyLevenbergMarquardtHeuristicForHessianRegularization(false);
            }

            if (lambda == lambdaMin)
               lastIteration = true;
         }

      }

      throw new RuntimeException("Didn't converge.");
   }

   public abstract double forwardPass(E dynamicsState, int startIndex, int endIndex, DenseMatrix64F initialCoM,
                                      RecyclingArrayList<DenseMatrix64F> updatedStateTrajectory, RecyclingArrayList<DenseMatrix64F> updatedControlTrajectory);


   public abstract boolean backwardPass(E dynamicsState, int startIndex, int endIndex, RecyclingArrayList<DenseMatrix64F> stateTrajectory,
                                        RecyclingArrayList<DenseMatrix64F> controlTrajectory);
}
