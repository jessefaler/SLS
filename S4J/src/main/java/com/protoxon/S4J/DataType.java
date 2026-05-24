

package com.protoxon.S4J;

public enum DataType {

	//	MB((byte) 0, 1,       "Megabyte"),
	//	GB((byte) 1, 1024,    "Gigabyte"),
	//	TB((byte) 2, 1048576, "Terabyte");

	B((byte) 0, 1, "Byte"),
	KB((byte) 1, 1024, "Kilobyte"),
	MB((byte) 2, 1048576, "Megabyte"),
	GB((byte) 3, 1_073_741_824, "Gigabyte"),
	TB((byte) 4, 1_099_511_627_776L, "Terabyte");

	private final byte identifier;
	private final long byteValue;
	private final String friendlyName;

	DataType(byte identifier, long byteValue, String friendlyName) {
		this.identifier = identifier;
		this.byteValue = byteValue;
		this.friendlyName = friendlyName;
	}

	public static DataType getByIdentifier(byte identifier) {
		for (DataType dataType : values()) {
			if (dataType.getIdentifier() == identifier) {
				return dataType;
			}
		}
		return null;
	}

	public byte getIdentifier() {
		return identifier;
	}

	public long getByteValue() {
		return byteValue;
	}

	public long getMbValue() {
		return byteValue / MB.getByteValue();
	}

	public String getFriendlyName() {
		return friendlyName;
	}
}
