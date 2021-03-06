package org.team3128.mechanisms;

import org.team3128.common.hardware.motor.MotorGroup;
import org.team3128.common.util.Log;
import org.team3128.common.util.RobotMath;

import com.ctre.CANTalon;
import com.ctre.CANTalon.FeedbackDevice;
import com.ctre.CANTalon.TalonControlMode;

import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.Servo;

public class Turret
{
	//describes possible states the turret can be in
	public enum TurretState
	{
		LAUNCHING("Launching"), //ball is currently being fired
		SPINNING_UP("Spinning Up"), //turret is preparing to launch
		STOPPED("Stopped"); //turret is not doing anything
		
		String name;
		
		private TurretState(String name)
		{
			this.name = name;
		}
		
		public String toString()
		{
			return name;
		}
	}
	
	//describes which direction the turret is turning
	public enum TurretTurn
	{
		CLOCKWISE("Clockwise"),
		COUNTERCLOCKWISE("Counterclockwise"),
		NONE("None");
		
		String direction;
		
		private TurretTurn(String direction)
		{
			this.direction = direction;
		}
		
		public String toString()
		{
			return direction;
		}
	}

	CANTalon launcherWheel;
	MotorGroup rotator;
	MotorGroup intakeRollers;
	Servo hood;
	DigitalInput turnLimitHall;
		
	final static double LAUNCH_WHEEL_SPEED = 100; // RPM
	final static double ALLOWABLE_WHEEL_SPEED_ERROR = LAUNCH_WHEEL_SPEED * .05;
	final static double BALL_HOLDER_LAUNCH_SPEED = .5; //speed to run the middle roller at to release the ball
	final static long LAUNCH_TIME = 1000; //ms - time it takes once we start running the middle roller for the ball to be launched
	
	
	private Thread thread;
	
	// read by thread to know when to start launching
	// after the thread reads it, it sets it to false again
	boolean launchFlag;
	
	// set by thread to tell program what is going on
	private TurretState state;
	private TurretTurn turnDirection;
	
	/**
	 * 
	 * @param launcherWheel
	 * @param rotator
	 * @param ballHolderWheel
	 * @param hood Servo that controls the angle of the hood
	 */
	public Turret(CANTalon launcherWheel, MotorGroup rotator, MotorGroup ballHolderWheel, Servo hood, double servoOffset, DigitalInput turnLimitHall)
	{
		this.launcherWheel = launcherWheel;
		this.rotator = rotator;
		this.intakeRollers = ballHolderWheel;
		this.hood = hood;
		this.turnLimitHall = turnLimitHall;
				
		launcherWheel.changeControlMode(TalonControlMode.Speed);
		launcherWheel.setFeedbackDevice(FeedbackDevice.CtreMagEncoder_Relative);
		launcherWheel.set(0);
		launcherWheel.disableControl();
		
		thread = new Thread(this::turretThread);
		thread.start();
		
		this.state = TurretState.STOPPED;
		this.turnDirection = TurretTurn.NONE;
		
	}
	
	/**
	 * Manages turret state in the background.
	 * Run as a separate thread
	 */
	private void turretThread()
	{
		Log.info("Turret", "Thread Starting...");
		while(true)
		{			
			switch(state)
			{
			case STOPPED:
				if(launchFlag)
				{
					state = TurretState.SPINNING_UP;
					launcherWheel.enableControl();
					launcherWheel.set(LAUNCH_WHEEL_SPEED);
					setLaunchFlag(false);
				}
			case SPINNING_UP:
				if((RobotMath.abs(launcherWheel.getClosedLoopError()) < ALLOWABLE_WHEEL_SPEED_ERROR))
				{
					state = TurretState.LAUNCHING;
					intakeRollers.setTarget(BALL_HOLDER_LAUNCH_SPEED);
					
					try
					{
						Thread.sleep(LAUNCH_TIME);
					}
					catch(InterruptedException e)
					{
						e.printStackTrace();
					}
					
					state = TurretState.STOPPED;
					intakeRollers.setTarget(0);
					
				}

			}
			
			
		}
	}
	
	
	//synchronized setter so that this variable can be modified by multiple threads
	private synchronized void setLaunchFlag(boolean launchFlag)
	{
		this.launchFlag = launchFlag;
	}
	
	/**
	 * Shoot the loaded ball.
	 */
	public void launch()
	{
		if(state == TurretState.STOPPED)
		{
			setLaunchFlag(true);
		}
	}
	
	/**
	 * Set the position of the turret's hood
	 */
	
	public void changeHoodPositionBy(double speed)
	{
		double startingHoodAngle = hood.getAngle();
		double deltaAngle = speed / 5.0;
		double newHoodAngle = startingHoodAngle + deltaAngle;
		if(newHoodAngle > 0 && newHoodAngle < 90)
		{
			hood.setAngle(newHoodAngle);
		}
	}
	
	public double getHoodAngle() {
		return hood.getAngle();
	}
	
	/**
	 * Changes spins the turret
	 * @param power
	 */
	
	public void spinTurret(double power)
	{
		double powerToSet = power / 5.0;
		if (turnLimitHall.get() == false)
		{
			if ((power > 0 && turnDirection == TurretTurn.CLOCKWISE) || (power < 0 && turnDirection == TurretTurn.COUNTERCLOCKWISE)) 
			{
				powerToSet = 0;
			}
		}
		
		if (power > 0)
		{
			turnDirection = TurretTurn.CLOCKWISE;
		}
		else if (power < 0)
		{
			turnDirection = TurretTurn.COUNTERCLOCKWISE;
		}
		else
		{
			turnDirection = TurretTurn.NONE;
		}
		
		rotator.setTarget(powerToSet);
	}
	
	public TurretTurn getTurnDirection()
	{
		return turnDirection;
	}
	
	/**
	 *  Get the state of the turret
	 * @return
	 */
	public TurretState getState()
	{
		return state;
	}
	
	
}
