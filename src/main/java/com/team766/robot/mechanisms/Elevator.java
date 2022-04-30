package com.team766.robot.mechanisms;

import com.team766.framework.Mechanism;
import com.team766.hal.RobotProvider;
import com.team766.hal.MotorController.ControlMode;
import com.team766.logging.Category;
import com.team766.hal.MotorController;

public class Elevator extends Mechanism {
	private MotorController motor;

	private double setpoint;

	public Elevator() {
		loggerCategory = Category.ELEVATOR;
		motor = RobotProvider.instance.getMotor("climber.elevator");
	}

	public void setSetpoint(double position) {
		checkContextOwnership();

		motor.set(ControlMode.Position, position);
		setpoint = position;
	}

	public double getSetpoint() {
		return setpoint;
	}

	public double getPosition() {
		return motor.getSensorPosition();
	}

	@Override
	public void run() {
		log("Setpoint: %f  Position: %f", getSetpoint(), getPosition());
	}
}