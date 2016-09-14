package us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.icpOptimization.projectionAndRecursionMultipliers.stateMatrices.swing;

import org.ejml.data.DenseMatrix64F;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;

import java.util.ArrayList;

public class SwingExitCMPProjectionMatrix extends DenseMatrix64F
{
   private final DoubleYoVariable omega;

   private final DoubleYoVariable doubleSupportSplitRatio;
   private final DoubleYoVariable exitCMPRatio;

   private final DoubleYoVariable startOfSplineTime;
   private final DoubleYoVariable endOfSplineTime;
   private final DoubleYoVariable totalTrajectoryTime;

   public SwingExitCMPProjectionMatrix(DoubleYoVariable omega, DoubleYoVariable doubleSupportSplitRatio, DoubleYoVariable exitCMPRatio,
         DoubleYoVariable startOfSplineTime, DoubleYoVariable endOfSplineTime, DoubleYoVariable totalTrajectoryTime)
   {
      super(4, 1);

      this.omega = omega;

      this.doubleSupportSplitRatio = doubleSupportSplitRatio;
      this.exitCMPRatio = exitCMPRatio;

      this.startOfSplineTime = startOfSplineTime;
      this.endOfSplineTime = endOfSplineTime;
      this.totalTrajectoryTime = totalTrajectoryTime;
   }

   public void reset()
   {
      zero();
   }

   public void compute(ArrayList<DoubleYoVariable> doubleSupportDurations, ArrayList<DoubleYoVariable> singleSupportDurations)
   {
      compute(doubleSupportDurations.get(1).getDoubleValue(),doubleSupportDurations.get(0).getDoubleValue(), singleSupportDurations.get(0).getDoubleValue());
   }

   public void compute(double upcomingDoubleSupportDuration, double currentDoubleSupportDuration, double singleSupportDuration)
   {
      double stepDuration = currentDoubleSupportDuration + singleSupportDuration;

      double upcomingInitialDoubleSupportDuration = doubleSupportSplitRatio.getDoubleValue() * upcomingDoubleSupportDuration;
      double endOfDoubleSupportDuration = (1.0 - doubleSupportSplitRatio.getDoubleValue()) * currentDoubleSupportDuration;

      double timeSpentOnExitCMP = exitCMPRatio.getDoubleValue() * stepDuration;
      double timeSpentOnEntryCMP = (1.0 - exitCMPRatio.getDoubleValue()) * stepDuration;

      double exitTime = upcomingInitialDoubleSupportDuration - timeSpentOnExitCMP;
      double exitRecursion = Math.exp(omega.getDoubleValue() * exitTime);

      double entryTime = startOfSplineTime.getDoubleValue() + endOfDoubleSupportDuration - timeSpentOnEntryCMP;
      double entryRecursion = Math.exp(omega.getDoubleValue() * entryTime);

      double lastSegmentDuration = totalTrajectoryTime.getDoubleValue() - endOfSplineTime.getDoubleValue();
      double lastSegmentProjection = Math.exp(-omega.getDoubleValue() * lastSegmentDuration);

      zero();

      set(0, 0, entryRecursion * (1.0 - exitRecursion));
      set(1, 0, omega.getDoubleValue() * entryRecursion * (1.0 - exitRecursion));
      set(2, 0, 1.0 - lastSegmentProjection);
      set(3, 0, -omega.getDoubleValue() * lastSegmentProjection);
   }
}
