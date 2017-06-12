package us.ihmc.humanoidRobotics.communication.controllerAPI.command;

import java.util.ArrayList;

import us.ihmc.communication.controllerAPI.command.Command;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.humanoidRobotics.communication.packets.SE3TrajectoryPointMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage;
import us.ihmc.humanoidRobotics.footstep.Footstep;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.lists.RecyclingArrayList;
import us.ihmc.robotics.math.trajectories.waypoints.FrameSE3TrajectoryPoint;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.trajectories.TrajectoryType;

public class FootstepDataCommand implements Command<FootstepDataCommand, FootstepDataMessage>
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private RobotSide robotSide;
   private TrajectoryType trajectoryType = TrajectoryType.DEFAULT;
   private double swingHeight = 0.0;
   private final FramePoint position = new FramePoint();
   private final FrameOrientation orientation = new FrameOrientation();
   private final FramePoint expectedInitialPosition = new FramePoint();
   private final FrameOrientation expectedInitialOrientation = new FrameOrientation();

   private final RecyclingArrayList<Point2D> predictedContactPoints = new RecyclingArrayList<>(4, Point2D.class);

   private final RecyclingArrayList<FramePoint> customPositionWaypoints = new RecyclingArrayList<>(2, FramePoint.class);
   private final RecyclingArrayList<FrameSE3TrajectoryPoint> swingTrajectory = new RecyclingArrayList<>(Footstep.maxNumberOfSwingWaypoints, FrameSE3TrajectoryPoint.class);

   private double swingTrajectoryBlendDuration = 0.0;
   private double swingDuration = Double.NaN;
   private double transferDuration = Double.NaN;

   private ReferenceFrame trajectoryFrame;
   
   /** the time to delay this command on the controller side before being executed **/
   private double executionDelayTime;

   public FootstepDataCommand()
   {
      clear();
   }

   @Override
   public void clear()
   {
      robotSide = null;
      trajectoryType = TrajectoryType.DEFAULT;
      swingHeight = 0.0;
      position.set(0.0, 0.0, 0.0);
      orientation.set(0.0, 0.0, 0.0, 1.0);
      expectedInitialPosition.setToNaN();
      expectedInitialOrientation.setToNaN();
      predictedContactPoints.clear();
      customPositionWaypoints.clear();
      swingTrajectory.clear();

      swingDuration = Double.NaN;
      transferDuration = Double.NaN;
   }

   @Override
   public void set(FootstepDataMessage message)
   {
      robotSide = message.getRobotSide();
      trajectoryType = message.getTrajectoryType();
      swingHeight = message.getSwingHeight();
      swingTrajectoryBlendDuration = message.getSwingTrajectoryBlendDuration();
      position.setIncludingFrame(worldFrame, message.getLocation());
      orientation.setIncludingFrame(worldFrame, message.getOrientation());

      Point3D messageExpectedInitialLocation = message.getExpectedInitialLocation();
      if (messageExpectedInitialLocation != null)
      {
         expectedInitialPosition.setIncludingFrame(worldFrame, messageExpectedInitialLocation);
      }

      Quaternion messageExpectedInitialOrientation = message.getExpectedInitialOrientation();
      if (messageExpectedInitialOrientation != null)
      {
         expectedInitialOrientation.setIncludingFrame(worldFrame, messageExpectedInitialOrientation);
      }

      Point3D[] originalPositionWaypointList = message.getCustomPositionWaypoints();
      customPositionWaypoints.clear();
      if (originalPositionWaypointList != null)
      {
         for (int i = 0; i < originalPositionWaypointList.length; i++)
            customPositionWaypoints.add().setIncludingFrame(trajectoryFrame, originalPositionWaypointList[i]);
      }

      SE3TrajectoryPointMessage[] messageSwingTrajectory = message.getSwingTrajectory();
      swingTrajectory.clear();
      if (messageSwingTrajectory != null)
      {
         for (int i = 0; i < messageSwingTrajectory.length; i++)
         {
            FrameSE3TrajectoryPoint point = swingTrajectory.add();
            point.setToZero(trajectoryFrame);
            messageSwingTrajectory[i].packData(point);
         }
      }

      ArrayList<Point2D> originalPredictedContactPoints = message.getPredictedContactPoints();
      predictedContactPoints.clear();
      if (originalPredictedContactPoints != null)
      {
         for (int i = 0; i < originalPredictedContactPoints.size(); i++)
            predictedContactPoints.add().set(originalPredictedContactPoints.get(i));
      }

      swingDuration = message.swingDuration;
      transferDuration = message.transferDuration;
      
      this.executionDelayTime = message.executionDelayTime;
   }

   @Override
   public void set(FootstepDataCommand other)
   {
      robotSide = other.robotSide;
      trajectoryType = other.trajectoryType;
      swingHeight = other.swingHeight;
      swingTrajectoryBlendDuration = other.swingTrajectoryBlendDuration;
      position.setIncludingFrame(other.position);
      orientation.setIncludingFrame(other.orientation);
      expectedInitialPosition.setIncludingFrame(other.expectedInitialPosition);
      expectedInitialOrientation.setIncludingFrame(other.expectedInitialOrientation);

      RecyclingArrayList<FramePoint> otherWaypointList = other.customPositionWaypoints;
      customPositionWaypoints.clear();
      for (int i = 0; i < otherWaypointList.size(); i++)
         customPositionWaypoints.add().setIncludingFrame(otherWaypointList.get(i));

      RecyclingArrayList<FrameSE3TrajectoryPoint> otherSwingTrajectory = other.swingTrajectory;
      swingTrajectory.clear();
      for (int i = 0; i < otherSwingTrajectory.size(); i++)
         swingTrajectory.add().setIncludingFrame(otherSwingTrajectory.get(i));

      RecyclingArrayList<Point2D> otherPredictedContactPoints = other.predictedContactPoints;
      predictedContactPoints.clear();
      for (int i = 0; i < otherPredictedContactPoints.size(); i++)
         predictedContactPoints.add().set(otherPredictedContactPoints.get(i));

      swingDuration = other.swingDuration;
      transferDuration = other.transferDuration;
      this.executionDelayTime = other.executionDelayTime;
   }

   public void set(ReferenceFrame trajectoryFrame, FootstepDataMessage message)
   {
      this.trajectoryFrame = trajectoryFrame;
      set(message);
   }

   public void setRobotSide(RobotSide robotSide)
   {
      this.robotSide = robotSide;
   }

   public void setPose(Point3D position, Quaternion orientation)
   {
      this.position.set(position);
      this.orientation.set(orientation);
   }

   public void setExpectedInitialPose(Point3D position, Quaternion orientation)
   {
      this.expectedInitialPosition.set(position);
      this.expectedInitialOrientation.set(orientation);
   }

   public void setSwingTrajectoryBlendDuration(double swingTrajectoryBlendDuration)
   {
      this.swingTrajectoryBlendDuration = swingTrajectoryBlendDuration;
   }

   public void setSwingHeight(double swingHeight)
   {
      this.swingHeight = swingHeight;
   }

   public void setTrajectoryType(TrajectoryType trajectoryType)
   {
      this.trajectoryType = trajectoryType;
   }

   public void setPredictedContactPoints(RecyclingArrayList<Point2D> predictedContactPoints)
   {
      this.predictedContactPoints.clear();
      for (int i = 0; i < predictedContactPoints.size(); i++)
         this.predictedContactPoints.add().set(predictedContactPoints.get(i));
   }

   public RobotSide getRobotSide()
   {
      return robotSide;
   }

   public TrajectoryType getTrajectoryType()
   {
      return trajectoryType;
   }

   public ReferenceFrame getTrajectoryFrame()
   {
      return trajectoryFrame;
   }

   public RecyclingArrayList<FramePoint> getCustomPositionWaypoints()
   {
      return customPositionWaypoints;
   }

   public RecyclingArrayList<FrameSE3TrajectoryPoint> getSwingTrajectory()
   {
      return swingTrajectory;
   }

   public double getSwingTrajectoryBlendDuration()
   {
      return swingTrajectoryBlendDuration;
   }

   public double getSwingHeight()
   {
      return swingHeight;
   }

   public FramePoint getPosition()
   {
      return position;
   }

   public FrameOrientation getOrientation()
   {
      return orientation;
   }

   public FramePoint getExpectedInitialPosition()
   {
      return expectedInitialPosition;
   }

   public FrameOrientation getExpectedInitialOrientation()
   {
      return expectedInitialOrientation;
   }

   public RecyclingArrayList<Point2D> getPredictedContactPoints()
   {
      return predictedContactPoints;
   }

   public double getSwingDuration()
   {
      return swingDuration;
   }

   public double getTransferDuration()
   {
      return transferDuration;
   }

   @Override
   public Class<FootstepDataMessage> getMessageClass()
   {
      return FootstepDataMessage.class;
   }

   @Override
   public boolean isCommandValid()
   {
      return robotSide != null;
   }
   
   /**
    * returns the amount of time this command is delayed on the controller side before executing
    * @return the time to delay this command in seconds
    */
   @Override
   public double getExecutionDelayTime()
   {
      return executionDelayTime;
   }
   
   /**
    * sets the amount of time this command is delayed on the controller side before executing
    * @param delayTime the time in seconds to delay after receiving the command before executing
    */
   @Override
   public void setExecutionDelayTime(double delayTime)
   {
      this.executionDelayTime = delayTime;
   }

}
