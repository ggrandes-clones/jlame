package libmp3lame;

/** Layer III side information. */
final class Jscalefac_struct {
	final int l[];// = new int[1 + Jencoder.SBMAX_l];
	final int s[];// = new int[1 + Jencoder.SBMAX_s];
	final int psfb21[];// = new int[1 + Jencoder.PSFB21];
	final int psfb12[];// = new int[1 + Jencoder.PSFB12];
	//
	Jscalefac_struct() {
		this.l = new int[1 + Jencoder.SBMAX_l];
		this.s = new int[1 + Jencoder.SBMAX_s];
		this.psfb21 = new int[1 + Jencoder.PSFB21];
		this.psfb12 = new int[1 + Jencoder.PSFB12];
	}
	Jscalefac_struct(final int[] il, final int[] is, final int[] ipsfb21, final int[] ipsfb12) {
		this.l = il;
		this.s = is;
		this.psfb21 = ipsfb21;
		this.psfb12 = ipsfb12;
	}
}
