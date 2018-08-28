package us.ihmc.footstepPlanning.graphSearch.heuristics;

import us.ihmc.commons.MathTools;
import us.ihmc.euclid.geometry.Pose2D;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.footstepPlanning.graphSearch.parameters.FootstepPlannerCostParameters;
import us.ihmc.footstepPlanning.graphSearch.parameters.FootstepPlannerParameters;
import us.ihmc.footstepPlanning.graphSearch.graph.FootstepNode;
import us.ihmc.pathPlanning.bodyPathPlanner.BodyPathPlanner;
import us.ihmc.robotics.geometry.AngleTools;
import us.ihmc.yoVariables.registry.YoVariableRegistry;

public class BodyPathHeuristics extends CostToGoHeuristics
{
   private static final double pathViolationWeight = 30.0;
   private final BodyPathPlanner bodyPath;
   private final FootstepPlannerParameters parameters;
   private final FootstepPlannerCostParameters costParameters;

   private double goalAlpha = 1.0;

   public BodyPathHeuristics(YoVariableRegistry registry, FootstepPlannerParameters parameters, BodyPathPlanner bodyPath)
   {
      this("", registry, parameters, bodyPath);
   }

   public BodyPathHeuristics(String namePrefix, YoVariableRegistry registry, FootstepPlannerParameters parameters, BodyPathPlanner bodyPath)
   {
      super(namePrefix, registry);
      this.bodyPath = bodyPath;
      this.parameters = parameters;
      costParameters = parameters.getCostParameters();
   }

   @Override
   protected double computeHeuristics(FootstepNode node, FootstepNode goalNode)
   {
      Point2D midFootPoint = node.getOrComputeMidFootPoint(parameters.getIdealFootstepWidth());
      Pose2D closestPointOnPath = new Pose2D();

      double alpha = bodyPath.getClosestPoint(midFootPoint, closestPointOnPath);
      alpha = MathTools.clamp(alpha, 0.0, goalAlpha);
      bodyPath.getPointAlongPath(alpha, closestPointOnPath);

      double distanceToPath = closestPointOnPath.getPosition().distance(midFootPoint);
      double pathLength = bodyPath.computePathLength(alpha) - bodyPath.computePathLength(goalAlpha);
      double remainingDistance = pathLength + pathViolationWeight * distanceToPath;

      double yaw = pathViolationWeight * AngleTools.computeAngleDifferenceMinusPiToPi(node.getYaw(), closestPointOnPath.getYaw());
      double minSteps = remainingDistance / parameters.getMaximumStepReach();
      return remainingDistance + costParameters.getYawWeight() * Math.abs(yaw) + costParameters.getCostPerStep() * minSteps;
   }

   public void setGoalAlpha(double alpha)
   {
      goalAlpha = alpha;
   }
}
