package us.ihmc.atlas.networkProcessor.wholeBodyTrajectoryToolboxModule;

import static us.ihmc.humanoidRobotics.communication.packets.manipulation.wholeBodyTrajectory.ConfigurationSpaceName.YAW;
import static us.ihmc.humanoidRobotics.communication.packets.manipulation.wholeBodyTrajectory.WholeBodyTrajectoryToolboxMessageTools.createTrajectoryMessage;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import controller_msgs.msg.dds.RigidBodyExplorationConfigurationMessage;
import controller_msgs.msg.dds.WaypointBasedTrajectoryMessage;
import controller_msgs.msg.dds.WholeBodyTrajectoryToolboxConfigurationMessage;
import controller_msgs.msg.dds.WholeBodyTrajectoryToolboxMessage;
import us.ihmc.atlas.AtlasRobotModel;
import us.ihmc.atlas.AtlasRobotVersion;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.RobotTarget;
import us.ihmc.avatar.networkProcessor.wholeBodyTrajectoryToolboxModule.AvatarWholeBodyTrajectoryToolboxControllerTest;
import us.ihmc.continuousIntegration.ContinuousIntegrationAnnotations;
import us.ihmc.continuousIntegration.IntegrationCategory;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.euclid.matrix.RotationMatrix;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.humanoidRobotics.communication.packets.HumanoidMessageTools;
import us.ihmc.humanoidRobotics.communication.packets.manipulation.wholeBodyTrajectory.ConfigurationSpaceName;
import us.ihmc.humanoidRobotics.communication.packets.manipulation.wholeBodyTrajectory.WholeBodyTrajectoryToolboxMessageTools;
import us.ihmc.humanoidRobotics.communication.packets.manipulation.wholeBodyTrajectory.WholeBodyTrajectoryToolboxMessageTools.FunctionTrajectory;
import us.ihmc.manipulation.planning.exploringSpatial.TrajectoryLibraryForDRC;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.robotics.screwTheory.SelectionMatrix6D;
import us.ihmc.simulationconstructionset.UnreasonableAccelerationException;

@ContinuousIntegrationAnnotations.ContinuousIntegrationPlan(categories = {IntegrationCategory.EXCLUDE})
public class AtlasWholeBodyTrajectoryToolboxControllerTest extends AvatarWholeBodyTrajectoryToolboxControllerTest
{
   private DRCRobotModel robotModel = new AtlasRobotModel(AtlasRobotVersion.ATLAS_UNPLUGGED_V5_DUAL_ROBOTIQ, RobotTarget.SCS, false);
   private DRCRobotModel ghostRobotModel = new AtlasRobotModel(AtlasRobotVersion.ATLAS_UNPLUGGED_V5_DUAL_ROBOTIQ, RobotTarget.SCS, false);

   @Override
   public DRCRobotModel getRobotModel()
   {
      return robotModel;
   }

   @Override
   public DRCRobotModel getGhostRobotModel()
   {
      return ghostRobotModel;
   }

   @Override
   public String getSimpleRobotName()
   {
      return getRobotModel().getSimpleRobotName();
   }

   @Override
   @ContinuousIntegrationAnnotations.ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 120000)
   public void testOneBigCircle() throws Exception, UnreasonableAccelerationException
   {
      super.testOneBigCircle();
   }

   @Override
   @ContinuousIntegrationAnnotations.ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 120000)
   public void testHandCirclePositionAndYaw() throws Exception, UnreasonableAccelerationException
   {
      super.testHandCirclePositionAndYaw();
   }
   
   @ContinuousIntegrationAnnotations.ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 120000)
   public void testMovingPaintDrum() throws Exception, UnreasonableAccelerationException
   {
      // Trajectory parameters
      double trajectoryTime = 5.0;
      double liftUpHeight = 0.2;
      Point3D initialHandle = new Point3D(0.6, -0.5, 0.7);
      Point3D goalHandle = new Point3D(0.55, 0.1, 0.7);
      Vector3D initialHandleConfiguration = new Vector3D(1.0, 0.0, 0.0);

      // WBT toolbox configuration message
      FullHumanoidRobotModel fullRobotModel = createFullRobotModelAtInitialConfiguration();
      WholeBodyTrajectoryToolboxConfigurationMessage configuration = new WholeBodyTrajectoryToolboxConfigurationMessage();
      configuration.getInitialConfiguration().set(HumanoidMessageTools.createKinematicsToolboxOutputStatus(fullRobotModel));
      configuration.setMaximumExpansionSize(1000);

      // trajectory message, exploration message
      List<WaypointBasedTrajectoryMessage> handTrajectories = new ArrayList<>();
      List<RigidBodyExplorationConfigurationMessage> rigidBodyConfigurations = new ArrayList<>();

      double timeResolution = trajectoryTime / 100.0;

      RobotSide robotSide = RobotSide.RIGHT;
      RigidBody hand = fullRobotModel.getHand(robotSide);

      FunctionTrajectory handFunction = time -> computeMovingTrajectory(time, trajectoryTime, initialHandle, initialHandleConfiguration, goalHandle,
                                                                        liftUpHeight);

      SelectionMatrix6D selectionMatrix = new SelectionMatrix6D();
      selectionMatrix.resetSelection();
      WaypointBasedTrajectoryMessage trajectory = createTrajectoryMessage(hand, 0.0, trajectoryTime, timeResolution, handFunction, selectionMatrix);
      Pose3D controlFramePose = new Pose3D(fullRobotModel.getHandControlFrame(robotSide).getTransformToParent());

      trajectory.getControlFramePositionInEndEffector().set(controlFramePose.getPosition());
      trajectory.getControlFrameOrientationInEndEffector().set(controlFramePose.getOrientation());

      handTrajectories.add(trajectory);

      ConfigurationSpaceName[] spaces = {YAW};

      rigidBodyConfigurations.add(HumanoidMessageTools.createRigidBodyExplorationConfigurationMessage(hand, spaces));
      
      int maxNumberOfIterations = 10000;
      WholeBodyTrajectoryToolboxMessage message = HumanoidMessageTools.createWholeBodyTrajectoryToolboxMessage(configuration, handTrajectories, null, rigidBodyConfigurations);
      runTrajectoryTest(message, maxNumberOfIterations);
   }

   private static Pose3D computeMovingTrajectory(double time, double trajectoryTime, Point3D initialHandle, Vector3D initialHandleConfiguration,
                                                 Point3D goalHandle, double liftUpHeight)
   {
      Point3D liftUpInitialPoint = new Point3D(initialHandle);
      liftUpInitialPoint.addZ(liftUpHeight);

      Point3D liftUpGoalPoint = new Point3D(goalHandle);
      liftUpGoalPoint.addZ(liftUpHeight);

      double wholePositionDistance = 2 * liftUpHeight + initialHandle.distance(goalHandle);

      double liftUpEndTime = trajectoryTime * liftUpHeight / wholePositionDistance;
      double movingEndTime = trajectoryTime * (wholePositionDistance - liftUpHeight) / wholePositionDistance;

      Point3D point = new Point3D();
      double alpha = 0.0;
      if (time < liftUpEndTime)
      {
         alpha = time / liftUpEndTime;
         point.interpolate(initialHandle, liftUpInitialPoint, alpha);
      }
      else if (liftUpEndTime < time && time < movingEndTime)
      {
         alpha = (time - liftUpEndTime) / (movingEndTime - liftUpEndTime);
         point.interpolate(liftUpInitialPoint, liftUpGoalPoint, alpha);
      }
      else if (movingEndTime < time && time <= trajectoryTime)
      {
         alpha = (time - movingEndTime) / (trajectoryTime - movingEndTime);
         point.interpolate(liftUpGoalPoint, goalHandle, alpha);
      }
      else
      {
         alpha = 0;
         point = new Point3D(goalHandle);
      }

      initialHandleConfiguration.normalize();
      Vector3D zAxis = new Vector3D(initialHandleConfiguration);
      Vector3D xAxis = new Vector3D(0, 0, -1);
      Vector3D yAxis = new Vector3D();
      yAxis.cross(zAxis, xAxis);

      RotationMatrix initialOrientation = new RotationMatrix();
      initialOrientation.setColumns(xAxis, yAxis, zAxis);

      return new Pose3D(point, new Quaternion(initialOrientation));
   }
   
   @ContinuousIntegrationAnnotations.ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 120000)
   public void testDrillMotion() throws Exception, UnreasonableAccelerationException
   {
      // trajectory parameter
      double trajectoryTime = 15.0;

      boolean cuttingDirectionCW = true;
      double cuttingRadius = 0.3;
      Vector3D wallNormalVector = new Vector3D(1.0, -2.0, 0.0);
      Point3D cuttingCenterPosition = new Point3D(0.5, -0.5, 1.1);

      // wbt toolbox configuration message
      FullHumanoidRobotModel fullRobotModel = createFullRobotModelAtInitialConfiguration();
      WholeBodyTrajectoryToolboxConfigurationMessage configuration = new WholeBodyTrajectoryToolboxConfigurationMessage();
      configuration.getInitialConfiguration().set(HumanoidMessageTools.createKinematicsToolboxOutputStatus(fullRobotModel));
      configuration.setMaximumExpansionSize(1000);

      // trajectory message
      List<WaypointBasedTrajectoryMessage> trajectories = new ArrayList<>();
      List<RigidBodyExplorationConfigurationMessage> rigidBodyConfigurations = new ArrayList<>();

      double timeResolution = trajectoryTime / 100.0;

      RobotSide robotSide = RobotSide.RIGHT;
      RigidBody hand = fullRobotModel.getHand(robotSide);

      Vector3D translationToGraspingFrame = new Vector3D(-0.0, 0.05, 0.1);

      FunctionTrajectory handFunction = time -> TrajectoryLibraryForDRC.computeCuttingWallTrajectory(time, trajectoryTime, cuttingRadius, cuttingDirectionCW,
                                                                                                     cuttingCenterPosition, wallNormalVector);

      SelectionMatrix6D selectionMatrix = new SelectionMatrix6D();
      selectionMatrix.resetSelection();
      //selectionMatrix.selectAngularZ(false);
      WaypointBasedTrajectoryMessage trajectoryHand = WholeBodyTrajectoryToolboxMessageTools.createTrajectoryMessage(hand, 0.0, trajectoryTime, timeResolution,
                                                                                                                     handFunction, selectionMatrix);
      Pose3D controlFramePose = new Pose3D(fullRobotModel.getHandControlFrame(robotSide).getTransformToParent());
      controlFramePose.appendTranslation(translationToGraspingFrame);

      trajectoryHand.getControlFramePositionInEndEffector().set(controlFramePose.getPosition());
      trajectoryHand.getControlFrameOrientationInEndEffector().set(controlFramePose.getOrientation());

      trajectories.add(trajectoryHand);

      ConfigurationSpaceName[] spaces = {ConfigurationSpaceName.YAW};
      rigidBodyConfigurations.add(HumanoidMessageTools.createRigidBodyExplorationConfigurationMessage(hand, spaces));

      // keep sight on trajectory.
      RigidBody head = fullRobotModel.getHead();
      SelectionMatrix6D selectionMatrixHead = new SelectionMatrix6D();
      selectionMatrixHead.clearSelection();
      selectionMatrixHead.selectLinearY(true);
      selectionMatrixHead.selectLinearZ(true);

      WaypointBasedTrajectoryMessage trajectoryHead = WholeBodyTrajectoryToolboxMessageTools.createTrajectoryMessage(head, 0.0, trajectoryTime, timeResolution,
                                                                                                                     handFunction, selectionMatrixHead);

      trajectoryHead.getControlFramePositionInEndEffector().set(new Point3D(0.5, 0.0, 0.0));
      trajectoryHead.setWeight(0.01);
      //trajectories.add(trajectoryHead);

      // run test      
      int maxNumberOfIterations = 10000;
      WholeBodyTrajectoryToolboxMessage message = HumanoidMessageTools.createWholeBodyTrajectoryToolboxMessage(configuration, trajectories, null,
                                                                                                               rigidBodyConfigurations);
      runTrajectoryTest(message, maxNumberOfIterations);
   }
}