package us.ihmc.commonWalkingControlModules.wrenchDistribution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;

import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.YoContactPoint;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.YoPlaneContactState;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.PlaneContactStateCommand;
import us.ihmc.euclid.axisAngle.AxisAngle;
import us.ihmc.euclid.geometry.tools.EuclidGeometryTools;
import us.ihmc.euclid.matrix.RotationMatrix;
import us.ihmc.euclid.referenceFrame.FramePoint2D;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.interfaces.FramePoint2DReadOnly;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple2D.interfaces.Vector2DReadOnly;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.robotics.linearAlgebra.MatrixTools;
import us.ihmc.robotics.math.frames.YoMatrix;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.robotics.screwTheory.SpatialForceVector;
import us.ihmc.robotics.screwTheory.Wrench;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoFramePoint2D;

public class PlaneContactStateToWrenchMatrixHelper
{
   /**
    * This is used when determining whether two contact points are at the same location. If that is the case
    * one of them will rotate it's friction cone approximation to get better coverage of the cone through the
    * basis vectors.
    */
   private static final double distanceThresholdBetweenTwoContactPoint = 0.01;

   /**
    * If the size of the foothold is below this threshold CoP objectives for this plane will be ignored.
    */
   private static final double minFootholdSizeForCoPObjectives = 1.0e-3;

   private final int maxNumberOfContactPoints;
   private final int numberOfBasisVectorsPerContactPoint;
   private final double basisVectorAngleIncrement;

   private final int rhoSize;

   private final DenseMatrix64F rhoMatrix;
   private final DenseMatrix64F wrenchJacobianInCoMFrame;
   private final DenseMatrix64F wrenchJacobianInPlaneFrame;

   private final DenseMatrix64F fzRow = new DenseMatrix64F(0, 0);
   private final DenseMatrix64F singleCopRow = new DenseMatrix64F(0, 0);
   private final DenseMatrix64F copRegularizationJacobian;
   private final DenseMatrix64F copRateRegularizationJacobian;

   private final DenseMatrix64F rhoMaxMatrix;
   private final DenseMatrix64F rhoWeightMatrix;
   private final DenseMatrix64F rhoRateWeightMatrix;
   private final DenseMatrix64F copRegularizationWeightMatrix = new DenseMatrix64F(2, 2);
   private final DenseMatrix64F copRateRegularizationWeightMatrix = new DenseMatrix64F(2, 2);

   private final DenseMatrix64F activeRhoMatrix;

   private final YoPlaneContactState yoPlaneContactState;

   private final YoBoolean hasReset;
   private final YoBoolean resetRequested;

   private final YoMatrix yoRho;

   private final FrameVector3D contactNormalVector = new FrameVector3D();
   private final AxisAngle normalContactVectorRotation = new AxisAngle();

   private final ReferenceFrame centerOfMassFrame;
   private final PoseReferenceFrame planeFrame;

   private final YoFramePoint2D desiredCoP;
   private final YoFramePoint2D previousCoP;

   private final YoBoolean deactivateRhoWhenNotInContact;

   private final List<YoBoolean> rhoEnabled = new ArrayList<>();
   private final List<FramePoint3D> basisVectorsOrigin = new ArrayList<>();
   private final List<FrameVector3D> basisVectors = new ArrayList<>();
   private final HashMap<YoContactPoint, YoDouble> maxContactForces = new HashMap<>();
   private final HashMap<YoContactPoint, YoDouble> rhoWeights = new HashMap<>();

   private final RotationMatrix normalContactVectorRotationMatrix = new RotationMatrix();

   private final FramePoint2D contactPoint2d = new FramePoint2D();
   private final FrictionConeRotationCalculator coneRotationCalculator;

   public PlaneContactStateToWrenchMatrixHelper(ContactablePlaneBody contactablePlaneBody, ReferenceFrame centerOfMassFrame, int maxNumberOfContactPoints,
                                                int numberOfBasisVectorsPerContactPoint, FrictionConeRotationCalculator coneRotationCalculator,
                                                YoVariableRegistry parentRegistry)
   {
      List<FramePoint2D> contactPoints2d = contactablePlaneBody.getContactPoints2d();

      if (contactPoints2d.size() > maxNumberOfContactPoints)
         throw new RuntimeException("Unexpected number of contact points: " + contactPoints2d.size());

      this.centerOfMassFrame = centerOfMassFrame;
      this.maxNumberOfContactPoints = maxNumberOfContactPoints;
      this.numberOfBasisVectorsPerContactPoint = numberOfBasisVectorsPerContactPoint;
      this.coneRotationCalculator = coneRotationCalculator;

      rhoSize = maxNumberOfContactPoints * numberOfBasisVectorsPerContactPoint;
      basisVectorAngleIncrement = 2.0 * Math.PI / numberOfBasisVectorsPerContactPoint;

      rhoMatrix = new DenseMatrix64F(rhoSize, 1);
      wrenchJacobianInCoMFrame = new DenseMatrix64F(SpatialForceVector.SIZE, rhoSize);
      copRegularizationJacobian = new DenseMatrix64F(2, rhoSize);
      copRateRegularizationJacobian = new DenseMatrix64F(2, rhoSize);
      wrenchJacobianInPlaneFrame = new DenseMatrix64F(Wrench.SIZE, rhoSize);

      rhoMaxMatrix = new DenseMatrix64F(rhoSize, 1);
      rhoWeightMatrix = new DenseMatrix64F(rhoSize, rhoSize);
      rhoRateWeightMatrix = new DenseMatrix64F(rhoSize, rhoSize);

      activeRhoMatrix = new DenseMatrix64F(rhoSize, 1);
      CommonOps.fill(activeRhoMatrix, 1.0);

      CommonOps.fill(rhoMaxMatrix, Double.POSITIVE_INFINITY);

      String bodyName = contactablePlaneBody.getName();
      String namePrefix = bodyName + "WrenchMatrixHelper";
      YoVariableRegistry registry = new YoVariableRegistry(namePrefix);

      RigidBody rigidBody = contactablePlaneBody.getRigidBody();
      planeFrame = new PoseReferenceFrame(namePrefix + "ContactFrame", rigidBody.getBodyFixedFrame());
      planeFrame.setPoseAndUpdate(contactablePlaneBody.getSoleFrame().getTransformToDesiredFrame(rigidBody.getBodyFixedFrame()));
      yoPlaneContactState = new YoPlaneContactState(namePrefix, rigidBody, planeFrame, contactPoints2d, 0.0, registry);
      yoPlaneContactState.clear();
      yoPlaneContactState.computeSupportPolygon();

      hasReset = new YoBoolean(namePrefix + "HasReset", registry);
      resetRequested = new YoBoolean(namePrefix + "ResetRequested", registry);
      deactivateRhoWhenNotInContact = new YoBoolean(namePrefix + "DeactivateRhoWhenNotInContact", registry);

      for (int i = 0; i < contactPoints2d.size(); i++)
      {
         YoDouble rhoWeight = new YoDouble(namePrefix + "RhoWeight" + i, registry);
         YoDouble maxContactForce = new YoDouble(namePrefix + "MaxContactForce" + i, registry);
         maxContactForce.set(Double.POSITIVE_INFINITY);

         rhoWeights.put(yoPlaneContactState.getContactPoints().get(i), rhoWeight);
         maxContactForces.put(yoPlaneContactState.getContactPoints().get(i), maxContactForce);
      }

      yoRho = new YoMatrix(namePrefix + "Rho", rhoSize, 1, registry);

      for (int i = 0; i < rhoSize; i++)
      {
         rhoEnabled.add(new YoBoolean("Rho" + i + "Enabled", registry));
         basisVectors.add(new FrameVector3D(centerOfMassFrame));
         basisVectorsOrigin.add(new FramePoint3D(centerOfMassFrame));
      }

      previousCoP = new YoFramePoint2D(namePrefix + "PreviousCoP", planeFrame, registry);
      desiredCoP = new YoFramePoint2D(namePrefix + "DesiredCoP", planeFrame, registry);

      fzRow.reshape(1, rhoSize);
      singleCopRow.reshape(1, rhoSize);

      parentRegistry.addChild(registry);
   }

   public void setDeactivateRhoWhenNotInContact(boolean deactivateRhoWhenNotInContact)
   {
      this.deactivateRhoWhenNotInContact.set(deactivateRhoWhenNotInContact);
   }

   public void setPlaneContactStateCommand(PlaneContactStateCommand command)
   {
      RigidBodyTransform contactFramePose = command.getContactFramePoseInBodyFixedFrame();
      if (!contactFramePose.containsNaN())
         planeFrame.setPoseAndUpdate(contactFramePose);

      yoPlaneContactState.updateFromPlaneContactStateCommand(command);
      yoPlaneContactState.computeSupportPolygon();

      if (yoPlaneContactState.pollContactHasChangedNotification())
      {
         resetRequested.set(true);
      }

      for (int i = 0; i < command.getNumberOfContactPoints(); i++)
      {
         rhoWeights.get(yoPlaneContactState.getContactPoints().get(i)).set(command.getRhoWeight(i));
         if (command.hasMaxContactPointNormalForce())
         {
            maxContactForces.get(yoPlaneContactState.getContactPoints().get(i)).set(command.getMaxContactPointNormalForce(i));
         }
      }
   }

   public void computeMatrices(double defaultRhoWeight, double rhoRateWeight, Vector2DReadOnly copRegularizationWeight, Vector2DReadOnly copRateRegularizationWeight)
   {
      int numberOfContactPointsInContact = yoPlaneContactState.getNumberOfContactPointsInContact();
      if (numberOfContactPointsInContact > maxNumberOfContactPoints)
         throw new RuntimeException("Unhandled number of contact points: " + numberOfContactPointsInContact);

      // Compute the orientation of the normal contact vector and the corresponding transformation matrix
      computeNormalContactVectorRotation(normalContactVectorRotationMatrix);

      List<YoContactPoint> contactPoints = yoPlaneContactState.getContactPoints();

      int rhoIndex = 0;

      for (int contactPointIndex = 0; contactPointIndex < yoPlaneContactState.getTotalNumberOfContactPoints(); contactPointIndex++)
      {
         YoContactPoint contactPoint = contactPoints.get(contactPointIndex);
         boolean inContact = contactPoint.isInContact();

         // rotate each friction cone approximation to point one vector towards the center of the foot
         double angleOffset = coneRotationCalculator.computeConeRotation(yoPlaneContactState, contactPointIndex);

         // in case the contact point is close to another point rotate it
         if (inContact)
         {
            int matches = 0;
            for (int j = contactPointIndex + 1; j < contactPoints.size(); j++)
            {
               YoContactPoint candidateForMatch = contactPoints.get(j);
               candidateForMatch.getPosition2d(contactPoint2d);
               if (candidateForMatch.isInContact() && contactPoint.epsilonEquals(contactPoint2d, distanceThresholdBetweenTwoContactPoint))
               {
                  matches++;
               }
            }
            // TODO: If there are more then two contacts in the same spot we should probably disable them.
            if (matches > 0)
            {
               angleOffset += basisVectorAngleIncrement / 2.0;
            }
         }

         for (int basisVectorIndex = 0; basisVectorIndex < numberOfBasisVectorsPerContactPoint; basisVectorIndex++)
         {
            FramePoint3D basisVectorOrigin = basisVectorsOrigin.get(rhoIndex);
            FrameVector3D basisVector = basisVectors.get(rhoIndex);
            rhoEnabled.get(rhoIndex).set(inContact);

            if (inContact)
            {
               contactPoint.getPosition(basisVectorOrigin);
               computeBasisVector(basisVectorIndex, angleOffset, normalContactVectorRotationMatrix, basisVector);

               double rhoWeight = rhoWeights.get(yoPlaneContactState.getContactPoints().get(contactPointIndex)).getDoubleValue();
               if(Double.isNaN(rhoWeight))
               {
                  rhoWeight = defaultRhoWeight;
               }

               rhoWeightMatrix.set(rhoIndex, rhoIndex, rhoWeight * maxNumberOfContactPoints / numberOfContactPointsInContact);

               if (resetRequested.getBooleanValue())
                  rhoRateWeightMatrix.set(rhoIndex, rhoIndex, 0.0);
               else
                  rhoRateWeightMatrix.set(rhoIndex, rhoIndex, rhoRateWeight);

               activeRhoMatrix.set(rhoIndex, 0, 1.0);
            }
            else
            {
               clear(rhoIndex);

               if (deactivateRhoWhenNotInContact.getBooleanValue())
                  activeRhoMatrix.set(rhoIndex, 0, 0.0);
            }

            //// TODO: 6/5/17 scale this by the vertical magnitude
            rhoMaxMatrix.set(rhoIndex, 0, maxContactForces.get(yoPlaneContactState.getContactPoints().get(contactPointIndex)).getDoubleValue() / numberOfBasisVectorsPerContactPoint);

            rhoIndex++;
         }
      }

      computeWrenchJacobianInFrame(centerOfMassFrame, wrenchJacobianInCoMFrame);
      computeWrenchJacobianInFrame(planeFrame, wrenchJacobianInPlaneFrame);
      computeCopObjectiveJacobian(copRegularizationJacobian, desiredCoP);
      computeCopObjectiveJacobian(copRateRegularizationJacobian, previousCoP);

      if (yoPlaneContactState.inContact() && !resetRequested.getBooleanValue() && canHandleCoPCommand())
      {
         copRegularizationWeightMatrix.set(0, 0, copRegularizationWeight.getX());
         copRegularizationWeightMatrix.set(1, 1, copRegularizationWeight.getY());
         copRateRegularizationWeightMatrix.set(0, 0, copRateRegularizationWeight.getX());
         copRateRegularizationWeightMatrix.set(1, 1, copRateRegularizationWeight.getY());
      }
      else
      {
         copRegularizationWeightMatrix.zero();
         copRateRegularizationWeightMatrix.zero();
      }

      hasReset.set(resetRequested.getBooleanValue()); // So it is visible from SCS when the reset has been processed.
      resetRequested.set(false);

      // Should not get there as long as the number of contact points of the contactable body is less or equal to maxNumberOfContactPoints.
      for (; rhoIndex < rhoSize; rhoIndex++)
         clear(rhoIndex);
   }

   public void computeCopObjectiveJacobian(DenseMatrix64F jacobianToPack, FramePoint2DReadOnly desiredCoP)
   {
      if (desiredCoP.containsNaN())
      {
         jacobianToPack.reshape(2, rhoSize);
         jacobianToPack.zero();
         return;
      }

      desiredCoP.checkReferenceFrameMatch(planeFrame);

      int fzIndex = 5;
      CommonOps.extractRow(wrenchJacobianInPlaneFrame, fzIndex, fzRow);

      // [x_cop * J_fz + J_ty] * rho == 0
      int tauYIndex = 1;
      CommonOps.extractRow(wrenchJacobianInPlaneFrame, tauYIndex, singleCopRow);
      CommonOps.add(desiredCoP.getX(), fzRow, 1.0, singleCopRow, singleCopRow);
      CommonOps.insert(singleCopRow, jacobianToPack, 0, 0);

      // [y_cop * J_fz - J_tx] * rho == 0
      int tauXIndex = 0;
      CommonOps.extractRow(wrenchJacobianInPlaneFrame, tauXIndex, singleCopRow);
      CommonOps.add(desiredCoP.getY(), fzRow, -1.0, singleCopRow, singleCopRow);
      CommonOps.insert(singleCopRow, jacobianToPack, 1, 0);
   }

   private void clear(int rhoIndex)
   {
      FramePoint3D basisVectorOrigin = basisVectorsOrigin.get(rhoIndex);
      FrameVector3D basisVector = basisVectors.get(rhoIndex);

      basisVectorOrigin.setToZero(centerOfMassFrame);
      basisVector.setToZero(centerOfMassFrame);

      rhoMaxMatrix.set(rhoIndex, 0, Double.POSITIVE_INFINITY);
      rhoWeightMatrix.set(rhoIndex, rhoIndex, 1.0); // FIXME why is this setting to 1.0????
      rhoRateWeightMatrix.set(rhoIndex, rhoIndex, 0.0);
   }

   private final Wrench wrenchFromRho = new Wrench();
   private final DenseMatrix64F totalWrenchMatrix = new DenseMatrix64F(SpatialForceVector.SIZE, 1);

   public void computeWrenchFromRho(int startIndex, DenseMatrix64F allRobotRho)
   {
      CommonOps.extract(allRobotRho, startIndex, startIndex + rhoSize, 0, 1, rhoMatrix, 0, 0);
      yoRho.set(rhoMatrix);

      ReferenceFrame bodyFixedFrame = getRigidBody().getBodyFixedFrame();
      if (yoPlaneContactState.inContact())
      {
         CommonOps.mult(wrenchJacobianInPlaneFrame, rhoMatrix, totalWrenchMatrix);
         wrenchFromRho.set(bodyFixedFrame, planeFrame, totalWrenchMatrix);

         previousCoP.setX(-wrenchFromRho.getAngularPartY() / wrenchFromRho.getLinearPartZ());
         previousCoP.setY(wrenchFromRho.getAngularPartX() / wrenchFromRho.getLinearPartZ());
      }
      else
      {
         wrenchFromRho.setToZero(bodyFixedFrame, planeFrame);
         previousCoP.setToZero();
      }
   }

   public Wrench getWrench()
   {
      return wrenchFromRho;
   }

   private final SpatialForceVector unitSpatialForceVector = new SpatialForceVector();

   public void computeWrenchJacobianInFrame(ReferenceFrame frame, DenseMatrix64F matrixToPack)
   {
      matrixToPack.reshape(Wrench.SIZE, rhoSize);
      for (int rhoIndex = 0; rhoIndex < rhoSize; rhoIndex++)
      {
         if (rhoEnabled.get(rhoIndex).getValue())
         {
            FramePoint3D basisVectorOrigin = basisVectorsOrigin.get(rhoIndex);
            FrameVector3D basisVector = basisVectors.get(rhoIndex);
            basisVectorOrigin.changeFrame(frame);
            basisVector.changeFrame(frame);
            unitSpatialForceVector.setIncludingFrame(basisVector, basisVectorOrigin);
            unitSpatialForceVector.getMatrixColumn(matrixToPack, rhoIndex);
         }
         else
         {
            MatrixTools.zeroColumn(rhoIndex, matrixToPack);
         }
      }
   }

   private void computeNormalContactVectorRotation(RotationMatrix normalContactVectorRotationMatrixToPack)
   {
      yoPlaneContactState.getContactNormalFrameVector(contactNormalVector);
      contactNormalVector.changeFrame(planeFrame);
      contactNormalVector.normalize();
      EuclidGeometryTools.axisAngleFromZUpToVector3D(contactNormalVector, normalContactVectorRotation);
      normalContactVectorRotationMatrixToPack.set(normalContactVectorRotation);
   }

   private void computeBasisVector(int basisVectorIndex, double rotationOffset, RotationMatrix normalContactVectorRotationMatrix, FrameVector3D basisVectorToPack)
   {
      double angle = rotationOffset + basisVectorIndex * basisVectorAngleIncrement;
      double mu = yoPlaneContactState.getCoefficientOfFriction();

      // Compute the linear part considering a normal contact vector pointing z-up
      basisVectorToPack.setIncludingFrame(planeFrame, Math.cos(angle) * mu, Math.sin(angle) * mu, 1.0);

      // Transforming the result to consider the actual normal contact vector
      normalContactVectorRotationMatrix.transform(basisVectorToPack);
      basisVectorToPack.normalize();
   }

   public RigidBody getRigidBody()
   {
      return yoPlaneContactState.getRigidBody();
   }

   public int getRhoSize()
   {
      return rhoSize;
   }

   public DenseMatrix64F getLastRho()
   {
      return rhoMatrix;
   }

   public DenseMatrix64F getRhoJacobian()
   {
      return wrenchJacobianInCoMFrame;
   }

   public DenseMatrix64F getActiveRhoMatrix()
   {
      return activeRhoMatrix;
   }

   public DenseMatrix64F getRhoMax()
   {
      return rhoMaxMatrix;
   }

   public DenseMatrix64F getRhoWeight()
   {
      return rhoWeightMatrix;
   }

   public DenseMatrix64F getRhoRateWeight()
   {
      return rhoRateWeightMatrix;
   }

   public Wrench getWrenchFromRho()
   {
      return wrenchFromRho;
   }

   public DenseMatrix64F getCoPRegularizationJacobian()
   {
      return copRegularizationJacobian;
   }

   public DenseMatrix64F getCoPRateRegularizationJacobian()
   {
      return copRateRegularizationJacobian;
   }

   public DenseMatrix64F getCoPRegularizationWeight()
   {
      return copRegularizationWeightMatrix;
   }

   public DenseMatrix64F getCoPRateRegularizationWeight()
   {
      return copRateRegularizationWeightMatrix;
   }

   public List<FramePoint3D> getBasisVectorsOrigin()
   {
      return basisVectorsOrigin;
   }

   public List<FrameVector3D> getBasisVectors()
   {
      return basisVectors;
   }

   public boolean hasReset()
   {
      return hasReset.getBooleanValue();
   }

   public DenseMatrix64F getWrenchJacobianMatrix()
   {
      return wrenchJacobianInPlaneFrame;
   }

   public ReferenceFrame getPlaneFrame()
   {
      return planeFrame;
   }

   public boolean canHandleCoPCommand()
   {
      return yoPlaneContactState.getFootholdArea() > minFootholdSizeForCoPObjectives;
   }

}
