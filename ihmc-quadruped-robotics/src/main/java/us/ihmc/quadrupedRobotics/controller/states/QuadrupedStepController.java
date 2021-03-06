package us.ihmc.quadrupedRobotics.controller.states;

import us.ihmc.quadrupedRobotics.controlModules.QuadrupedBalanceManager;
import us.ihmc.quadrupedRobotics.controlModules.QuadrupedBodyOrientationManager;
import us.ihmc.quadrupedRobotics.controlModules.QuadrupedControlManagerFactory;
import us.ihmc.quadrupedRobotics.controlModules.foot.QuadrupedFeetManager;
import us.ihmc.quadrupedRobotics.controller.ControllerEvent;
import us.ihmc.quadrupedRobotics.controller.QuadrupedController;
import us.ihmc.quadrupedRobotics.controller.QuadrupedControllerToolbox;
import us.ihmc.quadrupedRobotics.messageHandling.QuadrupedStepMessageHandler;
import us.ihmc.quadrupedRobotics.planning.QuadrupedStep;
import us.ihmc.commons.lists.RecyclingArrayList;
import us.ihmc.quadrupedRobotics.planning.QuadrupedTimedStep;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

import java.util.List;

public class QuadrupedStepController implements QuadrupedController
{
   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final QuadrupedStepMessageHandler stepMessageHandler;

   // managers
   private final QuadrupedFeetManager feetManager;
   private final QuadrupedBalanceManager balanceManager;
   private final QuadrupedBodyOrientationManager bodyOrientationManager;

   private final QuadrupedControllerToolbox controllerToolbox;

   private final YoDouble speedUpTime = new YoDouble("speedUpTime", registry);
   private final YoDouble clampedSpeedUpTime = new YoDouble("clampedSpeedUpTime", registry);
   private final YoDouble estimatedRemainingSwingTimeUnderDisturbance = new YoDouble("estimatedRemainingSwingTimeUnderDisturbance", registry);

   public QuadrupedStepController(QuadrupedControllerToolbox controllerToolbox, QuadrupedControlManagerFactory controlManagerFactory,
                                  QuadrupedStepMessageHandler stepMessageHandler, YoVariableRegistry parentRegistry)
   {
      this.controllerToolbox = controllerToolbox;
      this.stepMessageHandler = stepMessageHandler;

      // feedback controllers
      feetManager = controlManagerFactory.getOrCreateFeetManager();
      balanceManager = controlManagerFactory.getOrCreateBalanceManager();
      bodyOrientationManager = controlManagerFactory.getOrCreateBodyOrientationManager();

      parentRegistry.addChild(registry);
   }

   @Override
   public void onEntry()
   {
      // initialize state
      stepMessageHandler.initialize();

      // update task space estimates
      controllerToolbox.update();

      bodyOrientationManager.setDesiredFrameToHoldPosition(controllerToolbox.getReferenceFrames().getCenterOfFeetZUpFrameAveragingLowestZHeightsAcrossEnds());
      bodyOrientationManager.initialize();

      feetManager.reset();
      feetManager.requestFullContact();

      stepMessageHandler.process();
      balanceManager.clearStepSequence();
      balanceManager.addStepsToSequence(stepMessageHandler.getStepSequence());

      balanceManager.initializeForStepping();
   }

   @Override
   public void doAction(double timeInState)
   {
      stepMessageHandler.process();

      // trigger step events
      feetManager.triggerSteps(stepMessageHandler.getActiveSteps());

      // update desired contact state and sole forces
      feetManager.compute();

      balanceManager.clearStepSequence();
      balanceManager.addStepsToSequence(stepMessageHandler.getStepSequence());

      // update desired horizontal com forces
      balanceManager.compute();

      // update step adjustment and swing speed up
      RecyclingArrayList<QuadrupedStep> adjustedSteps = balanceManager.computeStepAdjustment(stepMessageHandler.getActiveSteps());
      if (balanceManager.stepHasBeenAdjusted())
      {
         feetManager.adjustSteps(adjustedSteps);

         // update swing speed up
         requestSwingSpeedUpIfNeeded();
      }


      // update desired body orientation, angular velocity, and torque
      bodyOrientationManager.compute();
   }

   private void requestSwingSpeedUpIfNeeded()
   {
      List<? extends QuadrupedTimedStep> activeSteps = stepMessageHandler.getActiveSteps();
      double speedUpTime = balanceManager.estimateSwingSpeedUpTimeUnderDisturbance();
      this.speedUpTime.set(speedUpTime);

      double minSpeedUpTime = speedUpTime;
      for (int i = 0; i < activeSteps.size(); i++)
      {
         minSpeedUpTime = Math.min(minSpeedUpTime, feetManager.computeClampedSwingSpeedUpTime(activeSteps.get(i).getRobotQuadrant(), speedUpTime));
      }
      this.clampedSpeedUpTime.set(minSpeedUpTime);


      double timeRemaining = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < activeSteps.size(); i++)
      {
         timeRemaining = Math.max(timeRemaining, feetManager.requestSwingSpeedUp(activeSteps.get(i).getRobotQuadrant(), minSpeedUpTime));
      }

      estimatedRemainingSwingTimeUnderDisturbance.set(timeRemaining);
   }

   @Override
   public ControllerEvent fireEvent(double timeInState)
   {
      if (stepMessageHandler.isDoneWithStepSequence())
      {
         return ControllerEvent.DONE;
      }
      return null;
   }

   @Override
   public void onExit()
   {
      stepMessageHandler.reset();
   }

   public void halt()
   {
      stepMessageHandler.halt();
   }

   public YoVariableRegistry getYoVariableRegistry()
   {
      return registry;
   }
}
