package com.wit.gotthatspot.model;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.util.Date;

public class ParkingLocation {
	@SerializedName(value = "id")
	private Long id;

	@SerializedName(value = "lat")
	private double latitude;

	@SerializedName(value = "lng")
	private double longitude;

	@SerializedName(value = "name")
	private String name;

	@SerializedName(value = "cost_per_minute")
	private BigDecimal costPerMinute;

	@SerializedName(value = "max_reserve_time_mins")
	private Integer maxReservationTimeInMinutes;

	@SerializedName(value = "min_reserve_time_mins")
	private Integer minReservationTimeInMinutes;

	@SerializedName(value = "is_reserved")
	private Boolean reserved;

	@SerializedName(value = "reserved_until")
	private Date reservedUntil;

	public BigDecimal getCostPerMinute() {
		return this.costPerMinute;
	}

	public Long getId() {
		return this.id;
	}

	public double getLatitude() {
		return this.latitude;
	}

	public double getLongitude() {
		return this.longitude;
	}

	public Integer getMaxReservationTimeInMinutes() {
		return this.maxReservationTimeInMinutes;
	}

	public Integer getMinReservationTimeInMinutes() {
		return this.minReservationTimeInMinutes;
	}

	public String getName() {
		return this.name;
	}

	public Boolean getReserved() {
		return this.reserved;
	}

	public Date getReservedUntil() {
		return this.reservedUntil;
	}
}