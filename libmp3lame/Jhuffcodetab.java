package libmp3lame;

final class Jhuffcodetab {
	/** max. x-index+   */
	final int xlen;
	/** max number to be stored in linbits */
	final int linmax;
	/** pointer to array[xlen][ylen]  */
	final char[] table;
	/** pointer to array[xlen][ylen]  */
	final byte[] hlen;
	//
	Jhuffcodetab(final int ixlen, final int ilinmax, final char[] itable, final byte[] ihlen) {
		this.xlen = ixlen;
		this.linmax = ilinmax;
		this.table = itable;
		this.hlen = ihlen;
	}
}
