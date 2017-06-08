package us.ihmc.avatar.behaviorTests;

import static org.junit.Assert.*;

import org.junit.Test;

import us.ihmc.avatar.DRCObstacleCourseStartingLocation;
import us.ihmc.avatar.MultiRobotTestInterface;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.networkProcessor.DRCNetworkModuleParameters;
import us.ihmc.avatar.testTools.DRCBehaviorTestHelper;
import us.ihmc.avatar.testTools.DRCSimulationTestHelper;
import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.communication.packetCommunicator.PacketCommunicator;
import us.ihmc.communication.packets.PacketDestination;
import us.ihmc.communication.util.NetworkPorts;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.humanoidBehaviors.behaviors.roughTerrain.HumanCoManipulationBehavior;
import us.ihmc.humanoidBehaviors.behaviors.roughTerrain.PushAndWalkBehavior;
import us.ihmc.humanoidBehaviors.communication.CommunicationBridge;
import us.ihmc.humanoidBehaviors.utilities.WristForceSensorFilteredUpdatable;
import us.ihmc.humanoidRobotics.frames.HumanoidReferenceFrames;
import us.ihmc.humanoidRobotics.kryo.IHMCCommunicationKryoNetClassList;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.sensorProcessing.parameters.DRCRobotSensorInformation;
import us.ihmc.simulationConstructionSetTools.util.environments.FlatGroundEnvironment;
import us.ihmc.simulationToolkit.controllers.PushRobotController;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;
import us.ihmc.simulationconstructionset.util.simulationTesting.SimulationTestingParameters;
import us.ihmc.tools.thread.ThreadTools;

public abstract class AvatarHumanCoManipulationBehaviorTest implements MultiRobotTestInterface{

	private static final SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromEnvironmentVariables();
	DRCBehaviorTestHelper drcBehaviorTestHelper;
		
	protected void testBehavior() throws SimulationExceededMaximumTimeException
	{
		DRCRobotModel robotModel = getRobotModel();
		DRCRobotSensorInformation robotSensorInfo = robotModel.getSensorInformation();
		FlatGroundEnvironment flatGround = new FlatGroundEnvironment();
		DRCNetworkModuleParameters networkModuleParameters = new DRCNetworkModuleParameters();
		networkModuleParameters.enableBehaviorModule(true);
		networkModuleParameters.enableSensorModule(true);
		
		DRCObstacleCourseStartingLocation selectedLocation = DRCObstacleCourseStartingLocation.DEFAULT_BUT_ALMOST_PI;		
		drcBehaviorTestHelper = new DRCBehaviorTestHelper(flatGround, "DRCSimpleFlatGroundScriptTest", selectedLocation, simulationTestingParameters, robotModel, networkModuleParameters, true);
      FullHumanoidRobotModel fullRobotModel = drcBehaviorTestHelper.getControllerFullRobotModel();
      SimulationConstructionSet scs = drcBehaviorTestHelper.getSimulationConstructionSet();
		
		SideDependentList<WristForceSensorFilteredUpdatable> wristSensorUpdatables = drcBehaviorTestHelper.getWristForceSensorUpdatableSideDependentList();
      /*if (robotSensorInfo.getWristForceSensorNames() != null && !robotSensorInfo.getWristForceSensorNames().containsValue(null))
      {
         wristSensorUpdatables = new SideDependentList<>();
         for (RobotSide robotSide : RobotSide.values)
         {
            wristSensorUpdatables.put(robotSide, drcBehaviorTestHelper.getWristForceSensorUpdatable(robotSide));
         }
      }*/
      
		HumanoidReferenceFrames referenceFrames = new HumanoidReferenceFrames(fullRobotModel);
		WalkingControllerParameters walkingControllerParameters = robotModel.getWalkingControllerParameters();
		CommunicationBridge communicationBridge = drcBehaviorTestHelper.getBehaviorCommunicationBridge();
		HumanCoManipulationBehavior collaborativeBehavior = new HumanCoManipulationBehavior(communicationBridge, referenceFrames, fullRobotModel, walkingControllerParameters, null, robotSensorInfo, wristSensorUpdatables);
		scs.addYoVariableRegistry(collaborativeBehavior.getYoVariableRegistry());
		
		drcBehaviorTestHelper.setupCameraForUnitTest(new Point3D(0.0, 0.0, 1.0), new Point3D(10.0, 10.0, 3.0));
		ThreadTools.sleep(1000);
		assertTrue(drcBehaviorTestHelper.simulateAndBlockAndCatchExceptions(0.5));
		drcBehaviorTestHelper.dispatchBehavior(collaborativeBehavior);
		assertTrue(drcBehaviorTestHelper.simulateAndBlockAndCatchExceptions(1));
		drcBehaviorTestHelper.destroySimulation();
	}
}
