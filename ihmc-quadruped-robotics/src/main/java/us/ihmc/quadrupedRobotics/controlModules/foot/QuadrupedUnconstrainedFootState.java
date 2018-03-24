package us.ihmc.quadrupedRobotics.controlModules.foot;

import us.ihmc.commonWalkingControlModules.controllerCore.command.feedbackController.SpatialFeedbackControlCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.virtualModelControl.VirtualForceCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.virtualModelControl.VirtualModelControlCommand;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.quadrupedRobotics.controller.force.QuadrupedForceControllerToolbox;
import us.ihmc.quadrupedRobotics.controller.force.toolbox.QuadrupedSolePositionController;
import us.ihmc.quadrupedRobotics.controller.force.toolbox.QuadrupedSolePositionControllerSetpoints;
import us.ihmc.robotModels.FullQuadrupedRobotModel;
import us.ihmc.robotics.robotSide.RobotQuadrant;

public abstract class QuadrupedUnconstrainedFootState extends QuadrupedFootState
{
   protected final QuadrupedSolePositionController solePositionController;
   protected final QuadrupedSolePositionControllerSetpoints solePositionControllerSetpoints;

   protected final FrameVector3D initialSoleForces = new FrameVector3D();

   protected final FrameVector3D desiredLinearAcceleration = new FrameVector3D(ReferenceFrame.getWorldFrame());

   protected final VirtualForceCommand virtualForceCommand = new VirtualForceCommand();

   protected final QuadrupedForceControllerToolbox controllerToolbox;
   protected final RobotQuadrant robotQuadrant;

   public QuadrupedUnconstrainedFootState(RobotQuadrant robotQuadrant, QuadrupedForceControllerToolbox controllerToolbox,
                                          QuadrupedSolePositionController solePositionController)
   {
      this.robotQuadrant = robotQuadrant;
      this.solePositionController = solePositionController;
      this.controllerToolbox = controllerToolbox;
      solePositionControllerSetpoints = new QuadrupedSolePositionControllerSetpoints(robotQuadrant);

      FullQuadrupedRobotModel fullRobotModel = controllerToolbox.getFullRobotModel();
      virtualForceCommand.set(fullRobotModel.getBody(), fullRobotModel.getFoot(robotQuadrant));
   }

   public void onEntry()
   {
      controllerToolbox.getFootContactState(robotQuadrant).clear();
   }

   public void doControl()
   {
   }

   @Override
   public VirtualModelControlCommand<?> getVirtualModelControlCommand()
   {
      return virtualForceCommand;
   }

   @Override
   public SpatialFeedbackControlCommand getFeedbackControlCommand()
   {
      return null;
   }
}