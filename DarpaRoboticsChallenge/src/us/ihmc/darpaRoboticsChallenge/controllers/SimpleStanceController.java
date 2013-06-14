package us.ihmc.darpaRoboticsChallenge.controllers;

import com.yobotics.simulationconstructionset.DoubleYoVariable;
import com.yobotics.simulationconstructionset.SimulationConstructionSet;
import com.yobotics.simulationconstructionset.YoVariableRegistry;
import com.yobotics.simulationconstructionset.robotController.RobotController;
import com.yobotics.simulationconstructionset.util.AxisAngleOrientationController;
import com.yobotics.simulationconstructionset.util.graphics.DynamicGraphicObjectsListRegistry;
import com.yobotics.simulationconstructionset.util.math.frames.YoFrameVector;
import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;
import us.ihmc.SdfLoader.*;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.PlaneContactState;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.RectangularContactableBody;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.YoPlaneContactState;
import us.ihmc.commonWalkingControlModules.dynamics.FullRobotModel;
import us.ihmc.commonWalkingControlModules.momentumBasedController.CoMBasedMomentumRateOfChangeControlModule;
import us.ihmc.commonWalkingControlModules.momentumBasedController.MomentumRateOfChangeData;
import us.ihmc.commonWalkingControlModules.momentumBasedController.TaskspaceConstraintData;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.MomentumOptimizationSettings;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.OptimizationMomentumControlModule;
import us.ihmc.commonWalkingControlModules.referenceFrames.ReferenceFrames;
import us.ihmc.commonWalkingControlModules.terrain.TerrainType;
import us.ihmc.controlFlow.ControlFlowInputPort;
import us.ihmc.darpaRoboticsChallenge.DRCRobotModel;
import us.ihmc.darpaRoboticsChallenge.DRCRobotSDFLoader;
import us.ihmc.darpaRoboticsChallenge.DRCSCSInitialSetup;
import us.ihmc.darpaRoboticsChallenge.DRCSimulationVisualizer;
import us.ihmc.darpaRoboticsChallenge.drcRobot.DRCRobotJointMap;
import us.ihmc.darpaRoboticsChallenge.drcRobot.DRCRobotParameters;
import us.ihmc.darpaRoboticsChallenge.initialSetup.SquaredUpDRCRobotInitialSetup;
import us.ihmc.projectM.R2Sim02.initialSetup.RobotInitialSetup;
import us.ihmc.robotSide.RobotSide;
import us.ihmc.robotSide.SideDependentList;
import us.ihmc.utilities.exeptions.NoConvergenceException;
import us.ihmc.utilities.math.geometry.FrameOrientation;
import us.ihmc.utilities.math.geometry.FramePoint;
import us.ihmc.utilities.math.geometry.FrameVector;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.screwTheory.*;

import java.util.*;

/**
 * @author twan
 *         Date: 5/8/13
 */
public class SimpleStanceController implements RobotController
{
   private final String name = SimpleStanceController.class.getSimpleName();
   private final YoVariableRegistry registry = new YoVariableRegistry(name);
   private final SDFPerfectSimulatedSensorReader sensorReader;
   private final SDFPerfectSimulatedOutputWriter outputWriter;
   private final OptimizationMomentumControlModule momentumControlModule;
   private final SideDependentList<GeometricJacobian> footJacobians = new SideDependentList<GeometricJacobian>();
   private final GeometricJacobian pelvisJacobian;
   private final DenseMatrix64F orientationSelectionMatrix;
   private final LinkedHashMap<ContactablePlaneBody, ? extends PlaneContactState> contactStates;
   private final TwistCalculator twistCalculator;
   private final InverseDynamicsCalculator inverseDynamicsCalculator;
   private final AxisAngleOrientationController pelvisOrientationController;
   private final CoMBasedMomentumRateOfChangeControlModule momentumRateOfChangeControlModule;
   private final CenterOfMassJacobian centerOfMassJacobian;
   private final FullRobotModel fullRobotModel;
   private final ReferenceFrame centerOfMassFrame;
   private final YoFrameVector desiredPelvisAngularAcceleration;
   private final Map<OneDoFJoint, DoubleYoVariable> desiredJointAccelerationYoVariables = new LinkedHashMap<OneDoFJoint, DoubleYoVariable>();
   private final YoFrameVector desiredPelvisTorque;
   private final YoFrameVector desiredPelvisForce;
   private final OneDoFJoint[] oneDoFJoints;
   private final SixDoFJoint rootJoint;
   private final SpatialAccelerationCalculator spatialAccelerationCalculator;

   public SimpleStanceController(SDFRobot robot, SDFFullRobotModel fullRobotModel, ReferenceFrames referenceFrames, double controlDT,
                                 InverseDynamicsJoint[] jointsToOptimize, double gravityZ, double footForward, double footBack, double footWidth)
   {
      this.sensorReader = new SDFPerfectSimulatedSensorReader(robot, fullRobotModel, referenceFrames);
      this.outputWriter = new SDFPerfectSimulatedOutputWriter(robot, fullRobotModel);
      MomentumOptimizationSettings momentumOptimizationSettings = createOptimizationSettings(1.0, 5e-2, 1e-5, 0.0, 1e-5);
      rootJoint = fullRobotModel.getRootJoint();
      twistCalculator = new TwistCalculator(ReferenceFrame.getWorldFrame(), fullRobotModel.getPelvis());
      this.momentumControlModule = new OptimizationMomentumControlModule(rootJoint, referenceFrames.getCenterOfMassFrame(), controlDT, jointsToOptimize, momentumOptimizationSettings, gravityZ, twistCalculator, null, registry

      );

      for (RobotSide robotSide : RobotSide.values)
      {
         RigidBody foot = fullRobotModel.getFoot(robotSide);
         GeometricJacobian jacobian = new GeometricJacobian(fullRobotModel.getElevator(), foot, foot.getBodyFixedFrame());
         footJacobians.put(robotSide, jacobian);
      }

      RigidBody pelvis = fullRobotModel.getPelvis();
      pelvisJacobian = new GeometricJacobian(fullRobotModel.getElevator(), pelvis, pelvis.getBodyFixedFrame());

      orientationSelectionMatrix = new DenseMatrix64F(3, Momentum.SIZE);
      CommonOps.setIdentity(orientationSelectionMatrix);

      SideDependentList<ContactablePlaneBody> feet = createFeet(fullRobotModel, referenceFrames, footForward, footBack, footWidth);
      double coefficientOfFriction = 1.0;
      contactStates = createContactStates(feet, registry, coefficientOfFriction);
      this.inverseDynamicsCalculator = new InverseDynamicsCalculator(twistCalculator, gravityZ);

      this.pelvisOrientationController = new AxisAngleOrientationController("pelvis", pelvis.getBodyFixedFrame(), registry);
      pelvisOrientationController.setProportionalGains(5.0, 5.0, 5.0);
      pelvisOrientationController.setDerivativeGains(1.0, 1.0, 1.0);

      centerOfMassJacobian = new CenterOfMassJacobian(rootJoint.getSuccessor());
      centerOfMassFrame = referenceFrames.getCenterOfMassFrame();
      momentumRateOfChangeControlModule = new CoMBasedMomentumRateOfChangeControlModule(centerOfMassFrame, centerOfMassJacobian, registry);
      momentumRateOfChangeControlModule.setProportionalGains(100.0, 100.0, 100.0);
      momentumRateOfChangeControlModule.setDerivativeGains(20.0, 20.0, 20.0);

      desiredPelvisAngularAcceleration = new YoFrameVector("desiredPelvisAngularAcceleration", rootJoint.getSuccessor().getBodyFixedFrame(), registry);

      this.fullRobotModel = fullRobotModel;

      InverseDynamicsJoint[] allJoints = ScrewTools.computeSupportAndSubtreeJoints(rootJoint.getSuccessor());
      oneDoFJoints = ScrewTools.filterJoints(allJoints, OneDoFJoint.class);

      for (OneDoFJoint oneDoFJoint : oneDoFJoints)
      {
         DoubleYoVariable yoVariable = new DoubleYoVariable("qdd_d_" + oneDoFJoint.getName(), registry);
         desiredJointAccelerationYoVariables.put(oneDoFJoint, yoVariable);
      }

      desiredPelvisTorque = new YoFrameVector("desiredPelvisTorque", rootJoint.getSuccessor().getBodyFixedFrame(), registry);
      desiredPelvisForce = new YoFrameVector("desiredPelvisForce", rootJoint.getSuccessor().getBodyFixedFrame(), registry);

      spatialAccelerationCalculator = new SpatialAccelerationCalculator(rootJoint.getPredecessor(), twistCalculator, gravityZ, true);
   }

   private static MomentumOptimizationSettings createOptimizationSettings(double momentumWeight, double lambda, double wRho, double rhoMin, double wPhi)
   {
      MomentumOptimizationSettings momentumOptimizationSettings = new MomentumOptimizationSettings(new YoVariableRegistry("test1"));
      momentumOptimizationSettings.setMomentumWeight(momentumWeight, momentumWeight, momentumWeight, momentumWeight);
      momentumOptimizationSettings.setDampedLeastSquaresFactor(lambda);
      momentumOptimizationSettings.setRhoPlaneContactRegularization(wRho);
      momentumOptimizationSettings.setPhiCylinderContactRegularization(wPhi);
      momentumOptimizationSettings.setRhoMin(rhoMin);

      return momentumOptimizationSettings;
   }

   public void doControl()
   {
      sensorReader.read();

      twistCalculator.compute();

      centerOfMassJacobian.compute();

      momentumControlModule.reset();

      inverseDynamicsCalculator.reset();

      constrainFeet();

      FrameVector desiredPelvisAngularAcceleration = constrainPelvis();

      controlLinearMomentum();

      setJointAccelerationsAndWrenches();

      inverseDynamicsCalculator.compute();

      outputWriter.write();

      SpatialAccelerationVector pelvisAcceleration = new SpatialAccelerationVector();

      spatialAccelerationCalculator.compute();
      spatialAccelerationCalculator.packAccelerationOfBody(pelvisAcceleration, fullRobotModel.getPelvis());
      FrameVector angularAccelerationBack = new FrameVector(pelvisAcceleration.getExpressedInFrame());

      pelvisAcceleration.packAngularPart(angularAccelerationBack);

//    angularAccelerationBack.changeFrame(desiredPelvisAngularAcceleration.getReferenceFrame());
//    if (!desiredPelvisAngularAcceleration.epsilonEquals(angularAccelerationBack, 1e-12))
//       throw new RuntimeException();

      this.desiredPelvisAngularAcceleration.set(desiredPelvisAngularAcceleration);

      for (OneDoFJoint oneDoFJoint : oneDoFJoints)
      {
         desiredJointAccelerationYoVariables.get(oneDoFJoint).set(oneDoFJoint.getQddDesired());
      }

      Wrench wrench = new Wrench();
      rootJoint.packWrench(wrench);

      FrameVector pelvisTorque = new FrameVector(rootJoint.getSuccessor().getBodyFixedFrame());
      wrench.packAngularPart(pelvisTorque);
      desiredPelvisTorque.set(pelvisTorque);

      FrameVector pelvisForce = new FrameVector(rootJoint.getSuccessor().getBodyFixedFrame());
      wrench.packLinearPart(pelvisForce);
      desiredPelvisForce.set(pelvisForce);

   }

   private void controlLinearMomentum()
   {
      momentumRateOfChangeControlModule.startComputation();
      MomentumRateOfChangeData momentumRateOfChangeData = momentumRateOfChangeControlModule.getMomentumRateOfChangeOutputPort().getData();
      momentumControlModule.setDesiredRateOfChangeOfMomentum(momentumRateOfChangeData);
   }

   private void setJointAccelerationsAndWrenches()
   {
      try
      {
         momentumControlModule.compute(contactStates, null, null);
      }
      catch (NoConvergenceException e)
      {
         e.printStackTrace();
      }

      Map<RigidBody, Wrench> externalWrenches = momentumControlModule.getExternalWrenches();

      for (RigidBody rigidBody : externalWrenches.keySet())
      {
         inverseDynamicsCalculator.setExternalWrench(rigidBody, externalWrenches.get(rigidBody));
      }
   }

   private void constrainFeet()
   {
      for (RobotSide robotSide : RobotSide.values)
      {
         GeometricJacobian jacobian = footJacobians.get(robotSide);
         TaskspaceConstraintData taskspaceConstraintData = new TaskspaceConstraintData();
         taskspaceConstraintData.set(new SpatialAccelerationVector(jacobian.getEndEffectorFrame(), jacobian.getBaseFrame(), jacobian.getEndEffectorFrame()));
         momentumControlModule.setDesiredSpatialAcceleration(jacobian, taskspaceConstraintData);
      }
   }

   private FrameVector constrainPelvis()
   {
      TaskspaceConstraintData taskspaceConstraintData = new TaskspaceConstraintData();

      ReferenceFrame pelvisFrame = pelvisJacobian.getEndEffectorFrame();
      FrameVector output = new FrameVector(pelvisFrame);
      FrameOrientation desiredOrientation = new FrameOrientation(ReferenceFrame.getWorldFrame());
      desiredOrientation.changeFrame(pelvisFrame);
      FrameVector desiredAngularVelocity = new FrameVector(pelvisFrame);
      Twist twist = new Twist();
      twistCalculator.packRelativeTwist(twist, pelvisJacobian.getBase(), pelvisJacobian.getEndEffector());
      FrameVector currentAngularVelocity = new FrameVector(pelvisFrame);
      twist.packAngularPart(currentAngularVelocity);

      FrameVector feedForwardAngularAcceleration = new FrameVector(pelvisFrame);
      pelvisOrientationController.compute(output, desiredOrientation, desiredAngularVelocity, currentAngularVelocity, feedForwardAngularAcceleration);

      DenseMatrix64F nullspaceMultipliers = new DenseMatrix64F(0, 1);

      taskspaceConstraintData.setAngularAcceleration(pelvisFrame, pelvisJacobian.getBaseFrame(), output, nullspaceMultipliers);

      momentumControlModule.setDesiredSpatialAcceleration(pelvisJacobian, taskspaceConstraintData, 1.0);


//    DenseMatrix64F jointAcceleration = new DenseMatrix64F(3, 1);
//    MatrixTools.setDenseMatrixFromTuple3d(jointAcceleration, output.getVector(), 0, 0);
//    momentumControlModule.setDesiredJointAcceleration(fullRobotModel.getRootJoint(), jointAcceleration);

      return output;
   }

   public void initialize()
   {
      sensorReader.read();

      twistCalculator.compute();

      momentumControlModule.initialize();

      CenterOfMassCalculator centerOfMassCalculator = new CenterOfMassCalculator(fullRobotModel.getRootJoint().getSuccessor(), ReferenceFrame.getWorldFrame());
      centerOfMassCalculator.compute();
      FramePoint centerOfMass = centerOfMassCalculator.getCenterOfMass();
      ControlFlowInputPort<FramePoint> desiredCoMPositionInputPort = momentumRateOfChangeControlModule.getDesiredCoMPositionInputPort();
      desiredCoMPositionInputPort.setData(centerOfMass);
   }

   public YoVariableRegistry getYoVariableRegistry()
   {
      return registry;
   }

   public String getName()
   {
      return name;
   }

   public String getDescription()
   {
      return getName();
   }

   private SideDependentList<ContactablePlaneBody> createFeet(SDFFullRobotModel fullRobotModel, ReferenceFrames referenceFrames, double footForward,
           double footBack, double footWidth)
   {
      SideDependentList<ContactablePlaneBody> bipedFeet = new SideDependentList<ContactablePlaneBody>();
      for (RobotSide robotSide : RobotSide.values)
      {
         RigidBody footBody = fullRobotModel.getFoot(robotSide);
         ReferenceFrame soleFrame = referenceFrames.getSoleFrame(robotSide);
         double left = footWidth / 2.0;
         double right = -footWidth / 2.0;

         ContactablePlaneBody foot = new RectangularContactableBody(footBody, soleFrame, footForward, -footBack, left, right);
         bipedFeet.put(robotSide, foot);
      }

      return bipedFeet;
   }

   private LinkedHashMap<ContactablePlaneBody, YoPlaneContactState> createContactStates(SideDependentList<ContactablePlaneBody> feet,
           YoVariableRegistry registry, double coefficientOfFriction)
   {
      LinkedHashMap<ContactablePlaneBody, YoPlaneContactState> contactStates = new LinkedHashMap<ContactablePlaneBody, YoPlaneContactState>();
      for (ContactablePlaneBody contactablePlaneBody : feet)
      {
         YoPlaneContactState contactState = new YoPlaneContactState(contactablePlaneBody.getName() + "ContactState", contactablePlaneBody.getBodyFrame(),
                                               contactablePlaneBody.getPlaneFrame(), registry);
         contactState.set(contactablePlaneBody.getContactPoints2d(), coefficientOfFriction);
         contactStates.put(contactablePlaneBody, contactState);
      }

      return contactStates;
   }

   private static InverseDynamicsJoint[] createJointsToOptimize(SDFFullRobotModel fullRobotModel)
   {
      List<InverseDynamicsJoint> joints = new ArrayList<InverseDynamicsJoint>();
      InverseDynamicsJoint[] allJoints = ScrewTools.computeSupportAndSubtreeJoints(fullRobotModel.getRootJoint().getSuccessor());
      joints.addAll(Arrays.asList(allJoints));

      for (RobotSide robotSide : RobotSide.values)
      {
         List<InverseDynamicsJoint> fingerJoints = Arrays.asList(ScrewTools.computeSubtreeJoints(fullRobotModel.getHand(robotSide)));
         joints.removeAll(fingerJoints);
      }

      joints.remove(fullRobotModel.getLidarJoint());

      return joints.toArray(new InverseDynamicsJoint[joints.size()]);
   }

   public static void main(String[] args)
   {
      DRCRobotJointMap jointMap = new DRCRobotJointMap(DRCRobotModel.ATLAS_SANDIA_HANDS, false);
      JaxbSDFLoader jaxbSDFLoader = DRCRobotSDFLoader.loadDRCRobot(jointMap);
      SDFFullRobotModel fullRobotModel = jaxbSDFLoader.createFullRobotModel(jointMap);
      SDFRobot robot = jaxbSDFLoader.createRobot(jointMap, false);
      ReferenceFrames referenceFrames = new ReferenceFrames(fullRobotModel, jointMap, jointMap.getAnkleHeight());

      RobotInitialSetup<SDFRobot> intialSetup = new SquaredUpDRCRobotInitialSetup();
      intialSetup.initializeRobot(robot);

      double footForward = DRCRobotParameters.DRC_ROBOT_FOOT_FORWARD;
      double footBack = DRCRobotParameters.DRC_ROBOT_FOOT_BACK;
      double footWidth = DRCRobotParameters.DRC_ROBOT_FOOT_WIDTH;

      double controlDT = 0.005;
      InverseDynamicsJoint[] jointsToOptimize = createJointsToOptimize(fullRobotModel);
      double gravityZ = -robot.getGravityZ();
      SimpleStanceController controller = new SimpleStanceController(robot, fullRobotModel, referenceFrames, controlDT, jointsToOptimize, gravityZ,
                                             footForward, footBack, footWidth);
      controller.initialize();



      DynamicGraphicObjectsListRegistry dynamicGraphicObjectsListsRegistry = new DynamicGraphicObjectsListRegistry();
      new DRCSimulationVisualizer(robot, dynamicGraphicObjectsListsRegistry);

      double simDT = 1e-4;
      int simulationTicksPerControlTick = (int) (controlDT / simDT);
      robot.setController(controller, simulationTicksPerControlTick);

      DRCSCSInitialSetup drcscsInitialSetup = new DRCSCSInitialSetup(TerrainType.FLAT_Z_ZERO, simDT);
      drcscsInitialSetup.initializeRobot(robot, dynamicGraphicObjectsListsRegistry);

      SimulationConstructionSet scs = new SimulationConstructionSet(robot);
      scs.setDT(simDT, 50);
      dynamicGraphicObjectsListsRegistry.addDynamicGraphicsObjectListsToSimulationConstructionSet(scs);

      scs.startOnAThread();
   }
}
