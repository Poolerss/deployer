package com.deployer.deploy;

public class DeployBusyException extends RuntimeException {

	public DeployBusyException() {
		super("Деплой уже выполняется");
	}
}
