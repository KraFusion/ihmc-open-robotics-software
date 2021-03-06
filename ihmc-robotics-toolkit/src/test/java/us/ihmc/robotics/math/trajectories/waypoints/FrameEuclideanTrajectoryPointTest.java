package us.ihmc.robotics.math.trajectories.waypoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

import us.ihmc.commons.RandomNumbers;
import us.ihmc.continuousIntegration.ContinuousIntegrationAnnotations.ContinuousIntegrationTest;
import us.ihmc.euclid.axisAngle.AxisAngle;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.FrameQuaternion;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.interfaces.FramePoint3DReadOnly;
import us.ihmc.euclid.referenceFrame.interfaces.FrameVector3DReadOnly;
import us.ihmc.euclid.referenceFrame.tools.EuclidFrameRandomTools;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.robotics.geometry.frameObjects.FrameEuclideanWaypoint;
import us.ihmc.robotics.geometry.transformables.EuclideanWaypoint;
import us.ihmc.robotics.math.trajectories.waypoints.interfaces.EuclideanTrajectoryPointInterface;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;

public class FrameEuclideanTrajectoryPointTest
{
   @ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 30000)
   public void testCommonUsageExample()
   {
      ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
      PoseReferenceFrame poseFrame = new PoseReferenceFrame("poseFrame", new FramePose3D(worldFrame));

      FramePoint3D poseFramePosition = new FramePoint3D(worldFrame, new Point3D(0.5, 7.7, 9.2));
      poseFrame.setPositionAndUpdate(poseFramePosition);

      FrameQuaternion poseOrientation = new FrameQuaternion(worldFrame, new AxisAngle(1.2, 3.9, 4.7, 2.2));
      poseFrame.setOrientationAndUpdate(poseOrientation);

      FrameEuclideanTrajectoryPoint frameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(worldFrame);
      SimpleEuclideanTrajectoryPoint simpleTrajectoryPoint = new SimpleEuclideanTrajectoryPoint();

      double time = 3.4;
      Point3D position = new Point3D(1.0, 2.1, 3.7);
      Vector3D linearVelocity = new Vector3D(-0.4, 1.2, 3.3);

      simpleTrajectoryPoint.set(time, position, linearVelocity);
      frameEuclideanTrajectoryPoint.setIncludingFrame(worldFrame, simpleTrajectoryPoint);
      frameEuclideanTrajectoryPoint.changeFrame(poseFrame);

      // Do some checks:
      RigidBodyTransform transformToPoseFrame = worldFrame.getTransformToDesiredFrame(poseFrame);
      transformToPoseFrame.transform(position);
      transformToPoseFrame.transform(linearVelocity);

      FrameEuclideanTrajectoryPoint expectedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(poseFrame);

      expectedFrameEuclideanTrajectoryPoint.setTime(time);
      expectedFrameEuclideanTrajectoryPoint.setPosition(position);

      expectedFrameEuclideanTrajectoryPoint.setLinearVelocity(linearVelocity);

      assertEquals(3.4, frameEuclideanTrajectoryPoint.getTime(), 1e-7);
      assertEquals(3.4, expectedFrameEuclideanTrajectoryPoint.getTime(), 1e-7);
      assertTrue(expectedFrameEuclideanTrajectoryPoint.epsilonEquals(frameEuclideanTrajectoryPoint, 1e-10));
   }

   @ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 30000)
   public void testConstructors()
   {
      double epsilon = 1.0e-20;
      Random random = new Random(21651016L);
      ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
      ReferenceFrame aFrame = EuclidFrameRandomTools.nextReferenceFrame("aFrame", random, worldFrame);

      ReferenceFrame expectedFrame = worldFrame;
      double expectedTime = 0.0;
      FramePoint3D expectedPosition = new FramePoint3D(expectedFrame);
      FrameVector3D expectedLinearVelocity = new FrameVector3D(expectedFrame);

      FrameEuclideanTrajectoryPoint testedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint();

      assertTrajectoryPointContainsExpectedData(expectedFrame, expectedTime, expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);

      expectedFrame = aFrame;
      expectedTime = 0.0;
      expectedPosition = new FramePoint3D(expectedFrame);
      expectedLinearVelocity = new FrameVector3D(expectedFrame);
      testedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(expectedFrame);

      assertTrajectoryPointContainsExpectedData(expectedFrame, expectedTime, expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);

      expectedFrame = worldFrame;
      expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFrame, 10.0, 10.0, 10.0);
      expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFrame);

      testedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(expectedTime, expectedPosition, expectedLinearVelocity);

      assertTrajectoryPointContainsExpectedData(expectedFrame, expectedTime, expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);

      expectedFrame = worldFrame;
      expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFrame, 10.0, 10.0, 10.0);
      expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFrame);

      FrameEuclideanTrajectoryPoint expectedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(expectedTime, expectedPosition, expectedLinearVelocity);

      testedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(expectedFrameEuclideanTrajectoryPoint);

      assertTrue(expectedFrameEuclideanTrajectoryPoint.epsilonEquals(testedFrameEuclideanTrajectoryPoint, epsilon));
      assertTrajectoryPointContainsExpectedData(expectedFrameEuclideanTrajectoryPoint.getReferenceFrame(), expectedFrameEuclideanTrajectoryPoint.getTime(),
            expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);

      final ReferenceFrame expectedFinalFrame = aFrame;
      final double expectedFinalTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      final FramePoint3D expectedFinalPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFinalFrame, 10.0, 10.0, 10.0);
      final FrameVector3D expectedFinalLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFinalFrame);

      SimpleEuclideanTrajectoryPoint expectedEuclideanTrajectoryPoint = new SimpleEuclideanTrajectoryPoint();
      expectedEuclideanTrajectoryPoint.setTime(expectedFinalTime);
      expectedEuclideanTrajectoryPoint.setPosition(expectedFinalPosition);
      expectedEuclideanTrajectoryPoint.setLinearVelocity(expectedFinalLinearVelocity);

      testedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(expectedFinalFrame, expectedEuclideanTrajectoryPoint);

      assertTrajectoryPointContainsExpectedData(expectedFinalFrame, expectedFinalTime, expectedFinalPosition, expectedFinalLinearVelocity,
            testedFrameEuclideanTrajectoryPoint, epsilon);

   }

   @ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 30000)
   public void testSetters()
   {
      double epsilon = 1.0e-20;
      Random random = new Random(21651016L);
      ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
      ReferenceFrame aFrame = EuclidFrameRandomTools.nextReferenceFrame("aFrame", random, worldFrame);

      ReferenceFrame expectedFrame = worldFrame;
      double expectedTime = 0.0;
      FramePoint3D expectedPosition = new FramePoint3D(expectedFrame);
      FrameVector3D expectedLinearVelocity = new FrameVector3D(expectedFrame);

      final FrameEuclideanTrajectoryPoint testedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint();

      assertTrajectoryPointContainsExpectedData(expectedFrame, expectedTime, expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);

      expectedFrame = worldFrame;
      expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFrame, 10.0, 10.0, 10.0);
      expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFrame);

      testedFrameEuclideanTrajectoryPoint.set(expectedTime, expectedPosition, expectedLinearVelocity);

      assertTrajectoryPointContainsExpectedData(expectedFrame, expectedTime, expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);

      expectedFrame = worldFrame;
      expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFrame, 10.0, 10.0, 10.0);
      expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFrame);

      testedFrameEuclideanTrajectoryPoint.set(expectedTime, expectedPosition, expectedLinearVelocity);

      assertTrajectoryPointContainsExpectedData(expectedFrame, expectedTime, expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);

      expectedFrame = aFrame;
      expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFrame, 10.0, 10.0, 10.0);
      expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFrame);

      testedFrameEuclideanTrajectoryPoint.setIncludingFrame(expectedTime, expectedPosition, expectedLinearVelocity);

      assertTrajectoryPointContainsExpectedData(expectedFrame, expectedTime, expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);

      expectedFrame = worldFrame;
      expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFrame, 10.0, 10.0, 10.0);
      expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFrame);

      FrameEuclideanTrajectoryPoint expectedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(expectedTime, expectedPosition, expectedLinearVelocity);

      testedFrameEuclideanTrajectoryPoint.setIncludingFrame(expectedFrameEuclideanTrajectoryPoint);

      expectedFrame = worldFrame;
      expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFrame, 10.0, 10.0, 10.0);
      expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFrame);

      expectedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(expectedTime, expectedPosition, expectedLinearVelocity);

      testedFrameEuclideanTrajectoryPoint.set(expectedFrameEuclideanTrajectoryPoint);

      assertTrue(expectedFrameEuclideanTrajectoryPoint.epsilonEquals(testedFrameEuclideanTrajectoryPoint, epsilon));
      assertTrajectoryPointContainsExpectedData(expectedFrameEuclideanTrajectoryPoint.getReferenceFrame(), expectedFrameEuclideanTrajectoryPoint.getTime(),
            expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);

      final ReferenceFrame expectedFinalFrame = aFrame;
      final double expectedFinalTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      final FramePoint3D expectedFinalPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFinalFrame, 10.0, 10.0, 10.0);
      final FrameVector3D expectedFinalLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFinalFrame);

      SimpleEuclideanTrajectoryPoint expectedEuclideanTrajectoryPoint = new SimpleEuclideanTrajectoryPoint();
      expectedEuclideanTrajectoryPoint.setTime(expectedFinalTime);
      expectedEuclideanTrajectoryPoint.setPosition(expectedFinalPosition);
      expectedEuclideanTrajectoryPoint.setLinearVelocity(expectedFinalLinearVelocity);

      testedFrameEuclideanTrajectoryPoint.setIncludingFrame(expectedFinalFrame, expectedEuclideanTrajectoryPoint);

      assertTrajectoryPointContainsExpectedData(expectedFinalFrame, expectedFinalTime, expectedFinalPosition, expectedFinalLinearVelocity,
            testedFrameEuclideanTrajectoryPoint, epsilon);

   }

   @ContinuousIntegrationTest(estimatedDuration = 0.1)
   @Test(timeout = 30000)
   public void testChangeFrame() throws Exception
   {
      double epsilon = 1.0e-10;
      Random random = new Random(21651016L);
      ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

      ReferenceFrame expectedFrame = worldFrame;
      double expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      FramePoint3D expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFrame, 10.0, 10.0, 10.0);
      FrameVector3D expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFrame);
      FrameEuclideanTrajectoryPoint testedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(expectedTime, expectedPosition, expectedLinearVelocity);

      for (int i = 0; i < 10000; i++)
      {
         expectedFrame = EuclidFrameRandomTools.nextReferenceFrame("randomFrame" + i, random, random.nextBoolean() ? worldFrame : expectedFrame);

         expectedPosition.changeFrame(expectedFrame);
         expectedLinearVelocity.changeFrame(expectedFrame);
         testedFrameEuclideanTrajectoryPoint.changeFrame(expectedFrame);

         assertTrajectoryPointContainsExpectedData(expectedFrame, expectedTime, expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);
      }
   }

   @ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 30000)
   public void testSetToZero() throws Exception
   {
      double epsilon = 1.0e-10;
      Random random = new Random(21651016L);
      ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

      ReferenceFrame expectedFrame = worldFrame;
      double expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      FramePoint3D expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFrame, 10.0, 10.0, 10.0);
      FrameVector3D expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFrame);
      FrameEuclideanTrajectoryPoint testedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(expectedTime, expectedPosition, expectedLinearVelocity);

      expectedTime = 0.0;
      expectedPosition.setToZero();
      expectedLinearVelocity.setToZero();
      testedFrameEuclideanTrajectoryPoint.setToZero();

      assertTrajectoryPointContainsExpectedData(expectedFrame, expectedTime, expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);

      expectedFrame = EuclidFrameRandomTools.nextReferenceFrame("blop", random, worldFrame);
      expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, worldFrame, 10.0, 10.0, 10.0);
      expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, worldFrame);
      testedFrameEuclideanTrajectoryPoint.setIncludingFrame(expectedTime, expectedPosition, expectedLinearVelocity);

      expectedTime = 0.0;
      expectedPosition.setToZero(expectedFrame);
      expectedLinearVelocity.setToZero(expectedFrame);
      testedFrameEuclideanTrajectoryPoint.setToZero(expectedFrame);

      assertTrajectoryPointContainsExpectedData(expectedFrame, expectedTime, expectedPosition, expectedLinearVelocity, testedFrameEuclideanTrajectoryPoint, epsilon);
   }

   @ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 30000)
   public void testSetToNaN() throws Exception
   {
      Random random = new Random(21651016L);
      ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

      ReferenceFrame expectedFrame = worldFrame;
      double expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      FramePoint3D expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, expectedFrame, 10.0, 10.0, 10.0);
      FrameVector3D expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, expectedFrame);
      FrameEuclideanTrajectoryPoint testedFrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(expectedTime, expectedPosition, expectedLinearVelocity);

      testedFrameEuclideanTrajectoryPoint.setToNaN();
      assertTrue(Double.isNaN(testedFrameEuclideanTrajectoryPoint.getTime()));
      assertTrue(testedFrameEuclideanTrajectoryPoint.containsNaN());

      expectedFrame = EuclidFrameRandomTools.nextReferenceFrame("blop", random, worldFrame);
      expectedTime = RandomNumbers.nextDouble(random, 0.0, 1000.0);
      expectedPosition = EuclidFrameRandomTools.nextFramePoint3D(random, worldFrame, 10.0, 10.0, 10.0);
      expectedLinearVelocity = EuclidFrameRandomTools.nextFrameVector3D(random, worldFrame);
      testedFrameEuclideanTrajectoryPoint.setIncludingFrame(expectedTime, expectedPosition, expectedLinearVelocity);

      testedFrameEuclideanTrajectoryPoint.setToNaN(expectedFrame);

      assertTrue(expectedFrame == testedFrameEuclideanTrajectoryPoint.getReferenceFrame());
      assertTrue(Double.isNaN(testedFrameEuclideanTrajectoryPoint.getTime()));
      assertTrue(testedFrameEuclideanTrajectoryPoint.containsNaN());
   }

   static void assertTrajectoryPointContainsExpectedData(ReferenceFrame expectedFrame, double expectedTime, FramePoint3DReadOnly expectedPosition,
         FrameVector3DReadOnly expectedLinearVelocity, FrameEuclideanTrajectoryPoint testedFrameEuclideanTrajectoryPoint, double epsilon)
   {
      assertTrue(expectedFrame == testedFrameEuclideanTrajectoryPoint.getReferenceFrame());
      assertEquals(expectedTime, testedFrameEuclideanTrajectoryPoint.getTime(), epsilon);
      assertTrue(expectedPosition.epsilonEquals(testedFrameEuclideanTrajectoryPoint.getGeometryObject().getPosition(), epsilon));
      assertTrue(expectedLinearVelocity.epsilonEquals(testedFrameEuclideanTrajectoryPoint.getGeometryObject().getLinearVelocity(), epsilon));

      Point3D actualPosition = new Point3D();
      Vector3D actualLinearVelocity = new Vector3D();

      testedFrameEuclideanTrajectoryPoint.getPosition(actualPosition);
      testedFrameEuclideanTrajectoryPoint.getLinearVelocity(actualLinearVelocity);

      assertTrue(expectedPosition.epsilonEquals(actualPosition, epsilon));
      assertTrue(expectedLinearVelocity.epsilonEquals(actualLinearVelocity, epsilon));

      FramePoint3D actualFramePosition = new FramePoint3D();
      FrameVector3D actualFrameLinearVelocity = new FrameVector3D();

      testedFrameEuclideanTrajectoryPoint.getPositionIncludingFrame(actualFramePosition);
      testedFrameEuclideanTrajectoryPoint.getLinearVelocityIncludingFrame(actualFrameLinearVelocity);

      assertTrue(expectedPosition.epsilonEquals(actualFramePosition, epsilon));
      assertTrue(expectedLinearVelocity.epsilonEquals(actualFrameLinearVelocity, epsilon));

      actualFramePosition = new FramePoint3D(expectedFrame);
      actualFrameLinearVelocity = new FrameVector3D(expectedFrame);

      testedFrameEuclideanTrajectoryPoint.getPosition(actualFramePosition);
      testedFrameEuclideanTrajectoryPoint.getLinearVelocity(actualFrameLinearVelocity);

      assertTrue(expectedPosition.epsilonEquals(actualFramePosition, epsilon));
      assertTrue(expectedLinearVelocity.epsilonEquals(actualFrameLinearVelocity, epsilon));
   }
   
   @ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 30000)
   public void testSomeSetsAngGets()
   {
      ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

      FrameEuclideanTrajectoryPoint FrameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(worldFrame);
      FrameEuclideanTrajectoryPoint.checkReferenceFrameMatch(ReferenceFrame.getWorldFrame());

      SimpleEuclideanTrajectoryPoint simpleTrajectoryPoint = new SimpleEuclideanTrajectoryPoint();

      double time = 3.4;
      Point3D position = new Point3D(1.0, 2.1, 3.7);
      Vector3D linearVelocity = new Vector3D(-0.4, 1.2, 3.3);

      simpleTrajectoryPoint.set(time, position, linearVelocity);
      FrameEuclideanTrajectoryPoint.setIncludingFrame(worldFrame, simpleTrajectoryPoint);

      // Check some get calls: 
      FramePoint3D pointForVerification = new FramePoint3D(worldFrame);
      FrameVector3D linearVelocityForVerification = new FrameVector3D(worldFrame);

      FrameEuclideanTrajectoryPoint.getPosition(pointForVerification);
      FrameEuclideanTrajectoryPoint.getLinearVelocity(linearVelocityForVerification);

      assertEquals(time, FrameEuclideanTrajectoryPoint.getTime(), 1e-10);
      assertTrue(pointForVerification.epsilonEquals(position, 1e-10));
      assertTrue(linearVelocityForVerification.epsilonEquals(linearVelocity, 1e-10));

      // Check NaN calls:
      assertFalse(FrameEuclideanTrajectoryPoint.containsNaN());
      FrameEuclideanTrajectoryPoint.setPositionToNaN();
      assertTrue(FrameEuclideanTrajectoryPoint.containsNaN());
      FrameEuclideanTrajectoryPoint.setPositionToZero();

      assertFalse(FrameEuclideanTrajectoryPoint.containsNaN());
      FrameEuclideanTrajectoryPoint.setLinearVelocityToNaN();
      assertTrue(FrameEuclideanTrajectoryPoint.containsNaN());
      FrameEuclideanTrajectoryPoint.setLinearVelocityToZero();

      FrameEuclideanTrajectoryPoint.getPosition(position);
      FrameEuclideanTrajectoryPoint.getLinearVelocity(linearVelocity);

      // Make sure they are all equal to zero:
      assertTrue(position.epsilonEquals(new Point3D(), 1e-10));
      assertTrue(linearVelocity.epsilonEquals(new Vector3D(), 1e-10));

      time = 9.9;
      pointForVerification.set(3.9, 2.2, 1.1);
      linearVelocityForVerification.set(8.8, 1.4, 9.22);

      assertFalse(Math.abs(FrameEuclideanTrajectoryPoint.getTime() - time) < 1e-7);
      assertFalse(pointForVerification.epsilonEquals(position, 1e-7));
      assertFalse(linearVelocityForVerification.epsilonEquals(linearVelocity, 1e-7));

      FrameEuclideanTrajectoryPoint.set(time, pointForVerification, linearVelocityForVerification);

      FrameEuclideanTrajectoryPoint.getPosition(position);
      FrameEuclideanTrajectoryPoint.getLinearVelocity(linearVelocity);

      assertEquals(time, FrameEuclideanTrajectoryPoint.getTime(), 1e-10);
      assertTrue(pointForVerification.epsilonEquals(position, 1e-10));
      assertTrue(linearVelocityForVerification.epsilonEquals(linearVelocity, 1e-10));

      FrameEuclideanTrajectoryPoint FrameEuclideanTrajectoryPointTwo = new FrameEuclideanTrajectoryPoint(worldFrame);

      double positionDistance = FrameEuclideanTrajectoryPoint.positionDistance(FrameEuclideanTrajectoryPointTwo);
      assertEquals(4.610856753359402, positionDistance, 1e-7);
      assertFalse(FrameEuclideanTrajectoryPoint.epsilonEquals(FrameEuclideanTrajectoryPointTwo, 1e-7));

      FrameEuclideanTrajectoryPointTwo.set(FrameEuclideanTrajectoryPoint);
      positionDistance = FrameEuclideanTrajectoryPoint.positionDistance(FrameEuclideanTrajectoryPointTwo);
      assertEquals(0.0, positionDistance, 1e-7);
      assertTrue(FrameEuclideanTrajectoryPoint.epsilonEquals(FrameEuclideanTrajectoryPointTwo, 1e-7));

      SimpleEuclideanTrajectoryPoint simplePoint = new SimpleEuclideanTrajectoryPoint();
      FrameEuclideanTrajectoryPoint.get(simplePoint);

      FrameEuclideanTrajectoryPoint.setToNaN();
      assertTrue(FrameEuclideanTrajectoryPoint.containsNaN());
      positionDistance = FrameEuclideanTrajectoryPoint.positionDistance(FrameEuclideanTrajectoryPointTwo);
      assertTrue(Double.isNaN(positionDistance));
      assertFalse(FrameEuclideanTrajectoryPoint.epsilonEquals(FrameEuclideanTrajectoryPointTwo, 1e-7));

      EuclideanTrajectoryPointInterface<?> trajectoryPointAsInterface = simplePoint;
      FrameEuclideanTrajectoryPoint.set(trajectoryPointAsInterface);

      positionDistance = FrameEuclideanTrajectoryPoint.positionDistance(FrameEuclideanTrajectoryPointTwo);
      assertEquals(0.0, positionDistance, 1e-7);
      assertTrue(FrameEuclideanTrajectoryPoint.epsilonEquals(FrameEuclideanTrajectoryPointTwo, 1e-7));
   }

   @ContinuousIntegrationTest(estimatedDuration = 0.0)
   @Test(timeout = 30000)
   public void testSomeMoreSettersAndGetters()
   {
      ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

      FrameEuclideanTrajectoryPoint frameEuclideanTrajectoryPoint = new FrameEuclideanTrajectoryPoint(worldFrame);
      frameEuclideanTrajectoryPoint.checkReferenceFrameMatch(ReferenceFrame.getWorldFrame());

      double time = 3.4;
      FramePoint3D position = new FramePoint3D(worldFrame, 1.0, 2.1, 3.7);
      FrameVector3D linearVelocity = new FrameVector3D(worldFrame, -0.4, 1.2, 3.3);

      frameEuclideanTrajectoryPoint.setTime(time);
      frameEuclideanTrajectoryPoint.setPosition(position);
      frameEuclideanTrajectoryPoint.setLinearVelocity(linearVelocity);

      PoseReferenceFrame poseFrame = new PoseReferenceFrame("poseFrame", new FramePose3D(worldFrame));

      FramePoint3D poseFramePosition = new FramePoint3D(worldFrame, new Point3D(0.5, 7.7, 9.2));
      poseFrame.setPositionAndUpdate(poseFramePosition);

      FrameQuaternion poseOrientation = new FrameQuaternion(worldFrame, new AxisAngle(1.2, 3.9, 4.7, 2.2));
      poseFrame.setOrientationAndUpdate(poseOrientation);

      frameEuclideanTrajectoryPoint.changeFrame(poseFrame);

      assertFalse(position.epsilonEquals(frameEuclideanTrajectoryPoint.getPositionCopy(), 1e-10));
      assertFalse(linearVelocity.epsilonEquals(frameEuclideanTrajectoryPoint.getLinearVelocityCopy(), 1e-10));

      position.changeFrame(poseFrame);
      linearVelocity.changeFrame(poseFrame);

      assertTrue(position.epsilonEquals(frameEuclideanTrajectoryPoint.getPositionCopy(), 1e-10));
      assertTrue(linearVelocity.epsilonEquals(frameEuclideanTrajectoryPoint.getLinearVelocityCopy(), 1e-10));

      FrameEuclideanTrajectoryPoint frameEuclideanTrajectoryPointTwo = new FrameEuclideanTrajectoryPoint(poseFrame);
      frameEuclideanTrajectoryPointTwo.setTime(time);
      frameEuclideanTrajectoryPointTwo.setPosition(position);
      frameEuclideanTrajectoryPointTwo.setLinearVelocity(linearVelocity);
      assertTrue(frameEuclideanTrajectoryPointTwo.epsilonEquals(frameEuclideanTrajectoryPoint, 1e-10));

      frameEuclideanTrajectoryPointTwo = new FrameEuclideanTrajectoryPoint(worldFrame);
      frameEuclideanTrajectoryPointTwo.setIncludingFrame(poseFrame, time, new Point3D(position), linearVelocity);
      assertTrue(frameEuclideanTrajectoryPointTwo.epsilonEquals(frameEuclideanTrajectoryPoint, 1e-10));
   
      frameEuclideanTrajectoryPointTwo = new FrameEuclideanTrajectoryPoint(poseFrame);
      EuclideanWaypoint euclideanWaypoint = new EuclideanWaypoint();
      frameEuclideanTrajectoryPoint.getEuclideanWaypoint(euclideanWaypoint);
      frameEuclideanTrajectoryPointTwo.set(time, euclideanWaypoint);
      assertTrue(frameEuclideanTrajectoryPointTwo.epsilonEquals(frameEuclideanTrajectoryPoint, 1e-10));

      frameEuclideanTrajectoryPointTwo = new FrameEuclideanTrajectoryPoint(worldFrame);
      frameEuclideanTrajectoryPointTwo.setIncludingFrame(poseFrame, time, euclideanWaypoint);
      assertTrue(frameEuclideanTrajectoryPointTwo.epsilonEquals(frameEuclideanTrajectoryPoint, 1e-10));

      frameEuclideanTrajectoryPointTwo = new FrameEuclideanTrajectoryPoint(poseFrame);
      FrameEuclideanWaypoint frameEuclideanWaypoint = new FrameEuclideanWaypoint(poseFrame);
      frameEuclideanTrajectoryPoint.getFrameEuclideanWaypoint(frameEuclideanWaypoint);
      frameEuclideanTrajectoryPointTwo.set(time, frameEuclideanWaypoint);
      assertTrue(frameEuclideanTrajectoryPointTwo.epsilonEquals(frameEuclideanTrajectoryPoint, 1e-10));

      frameEuclideanTrajectoryPointTwo = new FrameEuclideanTrajectoryPoint(worldFrame);
      frameEuclideanTrajectoryPointTwo.setIncludingFrame(time, frameEuclideanWaypoint);
      assertTrue(frameEuclideanTrajectoryPointTwo.epsilonEquals(frameEuclideanTrajectoryPoint, 1e-10));

   }
}
