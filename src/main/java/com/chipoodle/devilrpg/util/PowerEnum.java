package com.chipoodle.devilrpg.util;

import java.util.Arrays;

public enum PowerEnum {
	POWER1("Power 1"),
	POWER2("Power 2"),
	POWER3("Power 3"),
	POWER4("Power 4"),
	POWER5("Power 5");

	private final String description;
	PowerEnum(String description) {
		this.description = description;
	}
	
	public String getDescription() {
		return description;
	}
	
	public static PowerEnum getByDescription(String description) {
    	return Arrays.stream(PowerEnum.values()).filter(x->x.description.equals(description)).findAny().orElse(null);
    }
}
