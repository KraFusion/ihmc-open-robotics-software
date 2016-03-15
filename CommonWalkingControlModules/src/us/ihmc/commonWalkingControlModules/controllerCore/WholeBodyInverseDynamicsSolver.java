package us.ihmc.commonWalkingControlModules.controllerCore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ejml.data.DenseMatrix64F;

import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.ExternalWrenchCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.InverseDynamicsCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.InverseDynamicsCommandList;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.JointspaceAccelerationCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.MomentumModuleSolution;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.MomentumRateCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.PlaneContactStateCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.PointAccelerationCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.SpatialAccelerationCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.PrivilegedConfigurationCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.lowLevel.LowLevelJointControlMode;
import us.ihmc.commonWalkingControlModules.controllerCore.command.lowLevel.LowLevelOneDoFJointDesiredDataHolder;
import us.ihmc.commonWalkingControlModules.controllerCore.command.lowLevel.RootJointDesiredConfigurationData;
import us.ihmc.commonWalkingControlModules.controllerCore.command.lowLevel.RootJointDesiredConfigurationDataReadOnly;
import us.ihmc.commonWalkingControlModules.momentumBasedController.PlaneContactWrenchProcessor;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.InverseDynamicsOptimizationControlModule;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.MomentumControlModuleException;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.MomentumOptimizationSettings;
import us.ihmc.commonWalkingControlModules.visualizer.WrenchVisualizer;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.humanoidRobotics.model.CenterOfPressureDataHolder;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.linearAlgebra.MatrixTools;
import us.ihmc.robotics.math.frames.YoFrameVector;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.screwTheory.InverseDynamicsCalculator;
import us.ihmc.robotics.screwTheory.InverseDynamicsJoint;
import us.ihmc.robotics.screwTheory.OneDoFJoint;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.robotics.screwTheory.ScrewTools;
import us.ihmc.robotics.screwTheory.SixDoFJoint;
import us.ihmc.robotics.screwTheory.SpatialForceVector;
import us.ihmc.robotics.screwTheory.TwistCalculator;
import us.ihmc.robotics.screwTheory.Wrench;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;

public class WholeBodyInverseDynamicsSolver
{
   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final InverseDynamicsCalculator inverseDynamicsCalculator;
   private final InverseDynamicsOptimizationControlModule optimizationControlModule;

   private final SixDoFJoint rootJoint;
   private final RootJointDesiredConfigurationData rootJointDesiredConfiguration = new RootJointDesiredConfigurationData();
   private final LowLevelOneDoFJointDesiredDataHolder lowLevelOneDoFJointDesiredDataHolder = new LowLevelOneDoFJointDesiredDataHolder();
   private final Map<OneDoFJoint, DoubleYoVariable> jointAccelerationsSolution = new HashMap<>();

   private final PlaneContactWrenchProcessor planeContactWrenchProcessor;
   private final WrenchVisualizer wrenchVisualizer;

   private final OneDoFJoint[] controlledOneDoFJoints;
   private final InverseDynamicsJoint[] jointsToOptimizeFor;

   private final YoFrameVector desiredMomentumRateLinear;
   private final YoFrameVector optimalMomentumRateLinear;

   public WholeBodyInverseDynamicsSolver(WholeBodyControlCoreToolbox toolbox, MomentumOptimizationSettings momentumOptimizationSettings,
         YoVariableRegistry parentRegistry)
   {
      TwistCalculator twistCalculator = toolbox.getTwistCalculator();
      double gravityZ = toolbox.getGravityZ();
      List<? extends ContactablePlaneBody> contactablePlaneBodies = toolbox.getContactablePlaneBodies();
      YoGraphicsListRegistry yoGraphicsListRegistry = toolbox.getYoGraphicsListRegistry();

      rootJoint = toolbox.getRobotRootJoint();

      inverseDynamicsCalculator = new InverseDynamicsCalculator(twistCalculator, gravityZ);
      optimizationControlModule = new InverseDynamicsOptimizationControlModule(toolbox, momentumOptimizationSettings, registry);
      optimizationControlModule.setMinRho(momentumOptimizationSettings.getRhoMinScalar());

      jointsToOptimizeFor = momentumOptimizationSettings.getJointsToOptimizeFor();
      controlledOneDoFJoints = ScrewTools.filterJoints(jointsToOptimizeFor, OneDoFJoint.class);
      lowLevelOneDoFJointDesiredDataHolder.registerJointsWithEmptyData(controlledOneDoFJoints);
      lowLevelOneDoFJointDesiredDataHolder.setJointsControlMode(controlledOneDoFJoints, LowLevelJointControlMode.FORCE_CONTROL);

      for (int i = 0; i < controlledOneDoFJoints.length; i++)
      {
         OneDoFJoint joint = controlledOneDoFJoints[i];
         DoubleYoVariable jointAccelerationSolution = new DoubleYoVariable("qdd_qp_" + joint.getName(), registry);
         jointAccelerationsSolution.put(joint, jointAccelerationSolution);
      }

      planeContactWrenchProcessor = new PlaneContactWrenchProcessor(contactablePlaneBodies, yoGraphicsListRegistry, registry);

      wrenchVisualizer = WrenchVisualizer.createWrenchVisualizerWithContactableBodies("DesiredExternalWrench", contactablePlaneBodies, 1.0,
            yoGraphicsListRegistry, registry);

      ReferenceFrame centerOfMassFrame = toolbox.getCenterOfMassFrame();
      desiredMomentumRateLinear = new YoFrameVector("desiredMomentumRateLinear", centerOfMassFrame, registry);
      optimalMomentumRateLinear = new YoFrameVector("optimalMomentumRateLinear", centerOfMassFrame, registry);

      parentRegistry.addChild(registry);
   }

   public void reset()
   {
      inverseDynamicsCalculator.reset();
      optimizationControlModule.initialize();
   }

   public void initialize()
   {
      // When you initialize into this controller, reset the estimator positions to current. Otherwise it might be in a bad state
      // where the feet are all jacked up. For example, after falling and getting back up.
      inverseDynamicsCalculator.compute();
      optimizationControlModule.initialize();
      planeContactWrenchProcessor.initialize();
   }

   public void compute()
   {
      MomentumModuleSolution momentumModuleSolution;

      try
      {
         momentumModuleSolution = optimizationControlModule.compute();
      }
      catch (MomentumControlModuleException momentumControlModuleException)
      {
         // Don't crash and burn. Instead do the best you can with what you have.
         // Or maybe just use the previous ticks solution.
         momentumModuleSolution = momentumControlModuleException.getMomentumModuleSolution();
      }

      DenseMatrix64F jointAccelerations = momentumModuleSolution.getJointAccelerations();
      Map<RigidBody, Wrench> externalWrenchSolution = momentumModuleSolution.getExternalWrenchSolution();
      List<RigidBody> rigidBodiesWithExternalWrench = momentumModuleSolution.getRigidBodiesWithExternalWrench();
      SpatialForceVector centroidalMomentumRateSolution = momentumModuleSolution.getCentroidalMomentumRateSolution();

      optimalMomentumRateLinear.set(centroidalMomentumRateSolution.getLinearPart());

      for (int i = 0; i < rigidBodiesWithExternalWrench.size(); i++)
      {
         RigidBody rigidBody = rigidBodiesWithExternalWrench.get(i);
         inverseDynamicsCalculator.setExternalWrench(rigidBody, externalWrenchSolution.get(rigidBody));
      }

      ScrewTools.setDesiredAccelerations(jointsToOptimizeFor, jointAccelerations);

      inverseDynamicsCalculator.compute();
      rootJointDesiredConfiguration.setDesiredAccelerationFromJoint(rootJoint);
      lowLevelOneDoFJointDesiredDataHolder.setDesiredTorqueFromJoints(controlledOneDoFJoints);
      lowLevelOneDoFJointDesiredDataHolder.setDesiredAccelerationFromJoints(controlledOneDoFJoints);

      for (int i = 0; i < controlledOneDoFJoints.length; i++)
      {
         OneDoFJoint joint = controlledOneDoFJoints[i];
         jointAccelerationsSolution.get(joint).set(joint.getQddDesired());
      }

      planeContactWrenchProcessor.compute(externalWrenchSolution);
      wrenchVisualizer.visualize(externalWrenchSolution);
   }

   public void submitInverseDynamicsCommandList(InverseDynamicsCommandList inverseDynamicsCommandList)
   {
      while (inverseDynamicsCommandList.getNumberOfCommands() > 0)
      {
         InverseDynamicsCommand<?> command = inverseDynamicsCommandList.pollCommand();
         switch (command.getCommandType())
         {
         case TASKSPACE:
            optimizationControlModule.submitSpatialAccelerationCommand((SpatialAccelerationCommand) command);
            break;
         case POINT:
            optimizationControlModule.submitPointAccelerationCommand((PointAccelerationCommand) command);
            break;
         case JOINTSPACE:
            optimizationControlModule.submitJointspaceAccelerationCommand((JointspaceAccelerationCommand) command);
            break;
         case MOMENTUM:
            optimizationControlModule.submitMomentumRateCommand((MomentumRateCommand) command);
            recordMomentumRate((MomentumRateCommand) command);
            break;
         case PRIVILEGED_CONFIGURATION:
            optimizationControlModule.submitPrivilegedConfigurationCommand((PrivilegedConfigurationCommand) command);
            break;
         case EXTERNAL_WRENCH:
            optimizationControlModule.submitExternalWrenchCommand((ExternalWrenchCommand) command);
            break;
         case PLANE_CONTACT_STATE:
            optimizationControlModule.submitPlaneContactStateCommand((PlaneContactStateCommand) command);
            break;
         case COMMAND_LIST:
            submitInverseDynamicsCommandList((InverseDynamicsCommandList) command);
            break;
         default:
            throw new RuntimeException("The command type: " + command.getCommandType() + " is not handled.");
         }
      }
   }

   private void recordMomentumRate(MomentumRateCommand command)
   {
      DenseMatrix64F momentumRate = command.getMomentumRate();
      MatrixTools.extractYoFrameTupleFromEJMLVector(desiredMomentumRateLinear, momentumRate, 3);
   }

   public LowLevelOneDoFJointDesiredDataHolder getOutput()
   {
      return lowLevelOneDoFJointDesiredDataHolder;
   }

   public RootJointDesiredConfigurationDataReadOnly getOutputForRootJoint()
   {
      return rootJointDesiredConfiguration;
   }

   public CenterOfPressureDataHolder getDesiredCenterOfPressureDataHolder()
   {
      return planeContactWrenchProcessor.getDesiredCenterOfPressureDataHolder();
   }

   public InverseDynamicsJoint[] getJointsToOptimizeFors()
   {
      return jointsToOptimizeFor;
   }
}
