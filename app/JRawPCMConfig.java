package app;

final class JRawPCMConfig {
	//typedef enum ByteOrder {
	// static final int ByteOrderLittleEndian = 0;
	// static final int ByteOrderBigEndian = 1;
	//}

	int     in_bitwidth;
	boolean in_signed;
	// int /*ByteOrder*/ in_endian;
	boolean is_big_endian;
	//
	JRawPCMConfig(final int bitwidth, final boolean isSigned, final boolean isBigEndian) {
		this.in_bitwidth = bitwidth;
		this.in_signed = isSigned;
		this.is_big_endian = isBigEndian;
	}
}
