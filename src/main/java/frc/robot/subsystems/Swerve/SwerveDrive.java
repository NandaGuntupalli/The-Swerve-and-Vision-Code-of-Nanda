// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.Swerve;

import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StructPublisher;
import edu.wpi.first.wpilibj.DataLogManager;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Robot;
import frc.robot.Constants.SwerveDriveConstants;
import frc.robot.Constants.SwerveModuleConstants;
import frc.robot.subsystems.VisionSystem;

public class SwerveDrive extends SubsystemBase {
  /** Creates a new SweveDrive. */

  private final SwerveModule frontRight;
  private final SwerveModule frontLeft;
  private final SwerveModule backRight;
  private final SwerveModule backLeft;

  private final SwerveDrivePoseEstimator poseEstimator;

  private SwerveDriveKinematics kinematics =
      new SwerveDriveKinematics(SwerveDriveConstants.wheelLocations);

  private GyroIO gyro;

  private VisionSystem vision;

  private Field2d field;

  StructPublisher<Pose2d> posePublisher = NetworkTableInstance.getDefault()
    .getStructTopic("MyPose", Pose2d.struct).publish();

  public static SwerveDrive create() {
    if (Robot.isReal()) {
      return new SwerveDrive(
        new Pigeon2IO(), 
        new KrakenModuleIO(SwerveModuleConstants.FRDriveMotorID, SwerveModuleConstants.FRTurnMotorID, SwerveModuleConstants.FRCanCoderID, "Front Right"), 
        new KrakenModuleIO(SwerveModuleConstants.FLDriveMotorID, SwerveModuleConstants.FLTurnMotorID, SwerveModuleConstants.FLCanCoderID, "Front Left"), 
        new KrakenModuleIO(SwerveModuleConstants.BRDriveMotorID, SwerveModuleConstants.BRTurnMotorID, SwerveModuleConstants.BRCanCoderID, "Back Right"), 
        new KrakenModuleIO(SwerveModuleConstants.BLDriveMotorID, SwerveModuleConstants.BLTurnMotorID, SwerveModuleConstants.BLCanCoderID, "Back Left"));
    } if (Robot.isSimulation()) {
      return new SwerveDrive(
        new NoGyro(),
        new SimModuleIO("Front Right", 6, 7, 9, 8),
        new SimModuleIO("Front Left", 4, 5, 11, 10),
        new SimModuleIO("Back Right", 2, 3, 13, 12),
        new SimModuleIO("Back Left", 0, 1, 15, 14)
      );
    } else {
      return new SwerveDrive(
        new NoGyro(),
        new NoModule(),
        new NoModule(),
        new NoModule(),
        new NoModule()
      );
    }
  }
  
  public SwerveDrive(GyroIO gyro, ModuleIO frontRight, ModuleIO frontLeft, ModuleIO backRight, ModuleIO backLeft) {
    this.gyro = gyro;
    this.frontRight = new SwerveModule(frontRight, "Front Right");
    this.frontLeft = new SwerveModule(frontLeft, "Front Left");
    this.backRight = new SwerveModule(backRight, "Back Right");
    this.backLeft = new SwerveModule(backLeft, "Back Left");

    DataLogManager.log("[Swerve] Initializing");

    poseEstimator =
    new SwerveDrivePoseEstimator(
        kinematics,
        getHeading(),
        getModulePositions(),
        new Pose2d(0.0, 0.0, getHeading()));

    vision = new VisionSystem();

    field = new Field2d();
  }

  public void drive(
    double forward,
    double strafe,
    double turn,
    boolean isOpenLoop) {
    ChassisSpeeds chassisSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(forward, -strafe, turn, getHeading());
    setChassisSpeeds(chassisSpeeds, isOpenLoop);
  }

  public void setChassisSpeeds(ChassisSpeeds speeds, boolean isOpenLoop) {
    speeds = ChassisSpeeds.discretize(speeds, 0.020);

    setModuleStates(kinematics.toSwerveModuleStates(speeds), isOpenLoop);
  }

  public void setModuleStates(SwerveModuleState[] states, boolean isOpenLoop) {
    frontLeft.setState(states[0], isOpenLoop);
    frontRight.setState(states[1], isOpenLoop);
    backLeft.setState(states[2], isOpenLoop);
    backRight.setState(states[3], isOpenLoop);
  }

  public void lockModules() {
    setModuleStates(
        new SwerveModuleState[] {
          new SwerveModuleState(0, Rotation2d.fromDegrees(45)),
          new SwerveModuleState(0, Rotation2d.fromDegrees(-45)),
          new SwerveModuleState(0, Rotation2d.fromDegrees(-45)),
          new SwerveModuleState(0, Rotation2d.fromDegrees(45))
        },
        false);
  }

  public SwerveModulePosition[] getModulePositions() {
    return new SwerveModulePosition[] {
      frontLeft.getModulePosition(),
      frontRight.getModulePosition(),
      backLeft.getModulePosition(),
      backRight.getModulePosition(),
    };
  }

  public SwerveModuleState[] getModuleStates() {
    return new SwerveModuleState[] {
      frontLeft.getModuleState(),
      frontRight.getModuleState(),
      backLeft.getModuleState(),
      backRight.getModuleState(),
    };
  }

  public ChassisSpeeds getChassisSpeeds() {
    return kinematics.toChassisSpeeds(getModuleStates());
  }

  public Pose2d getPose() {
    return poseEstimator.getEstimatedPosition();
  }

  public Rotation2d getYaw() {
    return gyro.yawValue();
  }

  public Rotation2d getHeading() {
    return gyro.getRotation();
  }

  public void addVisionToPoseEstimate() {
    if (!vision.hasTargets()) return;

    poseEstimator.addVisionMeasurement(vision.getPose(), vision.getLatency(), 
    vision.getStandardDeviations(vision.getPoseFromVisionPoseEstimator(), vision.getTagTotalDistance(), 
    vision.getTagCount()));
  }

  public void updateOdometry() {
    poseEstimator.update(getHeading(), getModulePositions());
    addVisionToPoseEstimate();
  }

  public void resetOdometry(Rotation2d pose, SwerveModulePosition[] states, Pose2d drivePose) {
    poseEstimator.resetPosition(pose, states, drivePose);
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    posePublisher.set(getPose());

    SmartDashboard.putData("Field", field);

    updateOdometry();
    field.setRobotPose(getPose());
  }

  @Override
  public void simulationPeriodic() {
    frontRight.periodic();
    frontLeft.periodic();
    backRight.periodic();
    backLeft.periodic();

    field.setRobotPose(getPose());
    field.getObject("Swerve Modules").setPoses(getPose());

    SwerveModuleState[] moduleStates = {
      frontLeft.getModuleState(),
      frontRight.getModuleState(),
      backLeft.getModuleState(),
      backRight.getModuleState()
    };

    var chassisSpeed = kinematics.toChassisSpeeds(moduleStates);

    SmartDashboard.putData("Field", field);
  }
}
