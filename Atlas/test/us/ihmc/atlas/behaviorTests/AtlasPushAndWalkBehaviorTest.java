package us.ihmc.atlas.behaviorTests;

import org.junit.Test;

import us.ihmc.atlas.AtlasRobotModel;
import us.ihmc.atlas.AtlasRobotVersion;
import us.ihmc.avatar.behaviorTests.AvatarPushAndWalkBehaviorTest;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.continuousIntegration.ContinuousIntegrationAnnotations.ContinuousIntegrationTest;
import us.ihmc.robotics.partNames.ArmJointName;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.simulationConstructionSetTools.bambooTools.BambooTools;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;

public class AtlasPushAndWalkBehaviorTest extends AvatarPushAndWalkBehaviorTest
{
	AtlasRobotModel robotModel = new AtlasRobotModel(AtlasRobotVersion.ATLAS_UNPLUGGED_V5_DUAL_ROBOTIQ, DRCRobotModel.RobotTarget.SCS, false);
	@Override
	public DRCRobotModel getRobotModel()
	{
		return robotModel;
	}

	@Override
	public String getSimpleRobotName()
	{
		return BambooTools.getSimpleRobotNameFor(BambooTools.SimpleRobotNameKeys.ATLAS);
	}

	@Override
	@ContinuousIntegrationTest(estimatedDuration = 30.0)
	@Test
	public void testBehavior() throws SimulationExceededMaximumTimeException
	{
		super.testBehavior();
	}

	@Override
	protected String getHandJointNameForForceApplication()
	{
		System.out.println(robotModel.getJointMap().getHandName(RobotSide.LEFT));
		return robotModel.getJointMap().getHandName(RobotSide.LEFT);
	}

}