package us.ihmc.humanoidRobotics.footstep.footstepSnapper;

import java.util.ArrayList;
import java.util.List;

import us.ihmc.euclid.matrix.RotationMatrix;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage;
import us.ihmc.humanoidRobotics.footstep.Footstep;
import us.ihmc.robotics.dataStructures.HeightMapWithPoints;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.geometry.FramePose2d;
import us.ihmc.robotics.geometry.InsufficientDataException;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.screwTheory.RigidBody;

/**
 * Created by agrabertilton on 1/28/15.
 */
public class AdjustingFootstepSnapper implements QuadTreeFootstepSnapper
{
   private QuadTreeFootstepSnappingParameters footstepSnappingParameters;
   private double distanceAdjustment;
   private double angleAdjustment;
   private ConvexHullFootstepSnapper convexHullFootstepSnapper;

   public AdjustingFootstepSnapper(FootstepValueFunction valueFunction, QuadTreeFootstepSnappingParameters parameters)
   {
      convexHullFootstepSnapper = new ConvexHullFootstepSnapper(valueFunction, parameters);
      this.footstepSnappingParameters = parameters;
      this.distanceAdjustment = parameters.getDistanceAdjustment();
      this.angleAdjustment = parameters.getAngleAdjustment();
   }

   @Override
   public void setMask(List<Point2D> footShape)
   {
      convexHullFootstepSnapper.setMask(footShape);
   }

   @Override
   public void setUseMask(boolean useMask, double kernelMaskSafetyBuffer, double boundingBoxDimension)
   {
      convexHullFootstepSnapper.setUseMask(useMask, kernelMaskSafetyBuffer, boundingBoxDimension);
   }

   @Override
   public List<Point3D> getPointList()
   {
      return convexHullFootstepSnapper.getPointList();
   }

   public void updateParameters(QuadTreeFootstepSnappingParameters newParameters)
   {
      this.footstepSnappingParameters = newParameters;
      convexHullFootstepSnapper.updateParameters(newParameters);
   }

   public QuadTreeFootstepSnappingParameters getParameters()
   {
      return footstepSnappingParameters;
   }

   @Override
   public void adjustFootstepWithoutHeightmap(FootstepDataMessage footstep, double height, Vector3D planeNormal)
   {
      convexHullFootstepSnapper.adjustFootstepWithoutHeightmap(footstep, height, planeNormal);
   }

   @Override
   public void adjustFootstepWithoutHeightmap(Footstep footstep, double height, Vector3D planeNormal)
   {
      convexHullFootstepSnapper.adjustFootstepWithoutHeightmap(footstep, height, planeNormal);
   }


   @Override
   public Footstep generateFootstepWithoutHeightMap(FramePose2d desiredSolePosition, RigidBody foot, ReferenceFrame soleFrame, RobotSide robotSide,
           double height, Vector3D planeNormal)
   {
      return convexHullFootstepSnapper.generateFootstepWithoutHeightMap(desiredSolePosition, foot, soleFrame, robotSide, height, planeNormal);
   }

   @Override
   public Footstep generateSnappedFootstep(double soleX, double soleY, double yaw, RigidBody foot, ReferenceFrame soleFrame, RobotSide robotSide,
           HeightMapWithPoints heightMap)
           throws InsufficientDataException
   {
      FramePose2d footPose2d = new FramePose2d(ReferenceFrame.getWorldFrame(), new Point2D(soleX, soleY), yaw);

      return generateFootstepUsingHeightMap(footPose2d, foot, soleFrame, robotSide, heightMap);
   }

   @Override
   public Footstep generateFootstepUsingHeightMap(FramePose2d desiredSolePosition, RigidBody foot, ReferenceFrame soleFrame, RobotSide robotSide,
           HeightMapWithPoints heightMap)
           throws InsufficientDataException
   {
      Footstep toReturn = convexHullFootstepSnapper.generateFootstepUsingHeightMap(desiredSolePosition, foot, soleFrame, robotSide, heightMap);
      snapFootstep(toReturn, heightMap);

      return toReturn;
   }


   @Override
   public Footstep.FootstepType snapFootstep(Footstep footstep, HeightMapWithPoints heightMap)
   {
      // can only snap footsteps in world frame
      footstep.getFootstepPose().checkReferenceFrameMatch(ReferenceFrame.getWorldFrame());

      FootstepDataMessage originalFootstep = new FootstepDataMessage(footstep);
      //set to the sole pose
      FramePoint3D position = new FramePoint3D();
      FrameOrientation orientation = new FrameOrientation();
      footstep.getPose(position, orientation);
      originalFootstep.setLocation(position.getPoint());
      originalFootstep.setOrientation(orientation.getQuaternion());

      //get the footstep
      Footstep.FootstepType type = snapFootstep(originalFootstep, heightMap);
      if (type == Footstep.FootstepType.FULL_FOOTSTEP && originalFootstep.getPredictedContactPoints() != null){
         throw new RuntimeException(this.getClass().getSimpleName() + "Full Footstep should have null contact points");
      }
      footstep.setPredictedContactPoints(originalFootstep.getPredictedContactPoints());
      footstep.setFootstepType(type);
      FramePose solePoseInWorld = new FramePose(ReferenceFrame.getWorldFrame(), originalFootstep.getLocation(), originalFootstep.getOrientation());
      footstep.setPose(solePoseInWorld);

      footstep.setSwingHeight(originalFootstep.getSwingHeight());
      footstep.setTrajectoryType(originalFootstep.getTrajectoryType());

      return type;
   }

   @Override
   public Footstep.FootstepType snapFootstep(FootstepDataMessage footstep, HeightMapWithPoints heightMap)
   {
      Footstep.FootstepType footstepFound = convexHullFootstepSnapper.snapFootstep(footstep, heightMap);

      if (footstepFound != Footstep.FootstepType.BAD_FOOTSTEP)
      {
         if (footstepFound == Footstep.FootstepType.FULL_FOOTSTEP && footstep.getPredictedContactPoints() != null){
            throw new RuntimeException(this.getClass().getSimpleName() + "Full Footstep should have null contact points");
         }
         return footstepFound;
      }

      FootstepDataMessage originalFootstepFound = new FootstepDataMessage(footstep);

      Vector3D position = new Vector3D();
      RotationMatrix orientation = new RotationMatrix();
      Vector3D zOrientation = new Vector3D();

      position.set(originalFootstepFound.getLocation());
      orientation.set(originalFootstepFound.getOrientation());
      orientation.getColumn(2, zOrientation);
      double originalYaw = originalFootstepFound.getOrientation().getYaw();

      double[] angleOffsets;
      if (angleAdjustment > 0)
      {
         angleOffsets = new double[] {0.0, -angleAdjustment, angleAdjustment};
      }
      else
      {
         angleOffsets = new double[] {0.0};
      }

      ArrayList<Point2D> possiblePositions = new ArrayList<Point2D>();
      Point2D originalPosition = new Point2D(position.getX(), position.getY());
      possiblePositions.add(originalPosition);

      if (distanceAdjustment > 0)
      {
         for (int i = 0; i < 8; i++)
         {
            double angle = Math.PI / 4 * i + originalYaw;
            possiblePositions.add(new Point2D(originalPosition.getX() + Math.cos(angle) * distanceAdjustment,
                                              originalPosition.getY() + Math.sin(angle) * distanceAdjustment));
         }
      }

      boolean isOriginalPosition = true;
      FramePose2d desiredSolePosition = new FramePose2d(ReferenceFrame.getWorldFrame(), originalPosition, originalYaw);
      FramePose2d newDesiredSolePosition = new FramePose2d(desiredSolePosition);
      for (int i = 0; i < angleOffsets.length; i++)
      {
         for (Point2D point2d : possiblePositions)
         {
            if (isOriginalPosition)
            {
               isOriginalPosition = false;

               continue;
            }

            newDesiredSolePosition.setIncludingFrame(desiredSolePosition.getReferenceFrame(), point2d.getX(), point2d.getY(), originalYaw + angleOffsets[i]);
            footstepFound = convexHullFootstepSnapper.snapFootstep(footstep, heightMap);

            if (footstepFound!= Footstep.FootstepType.BAD_FOOTSTEP)
            {
               if (footstepFound == Footstep.FootstepType.FULL_FOOTSTEP && footstep.getPredictedContactPoints() != null){
                  throw new RuntimeException(this.getClass().getSimpleName() + "Full Footstep should have null contact points");
               }
               return footstepFound;
            }
         }
      }


      footstep.location = originalFootstepFound.location;
      footstep.setOrientation(originalFootstepFound.getOrientation());
      return Footstep.FootstepType.BAD_FOOTSTEP;
   }
}