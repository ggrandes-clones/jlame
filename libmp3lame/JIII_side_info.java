package libmp3lame;

final class JIII_side_info {
	final Jgr_info tt[][] = new Jgr_info[2][2];
	int     main_data_begin;
	int     private_bits;
	int     resvDrain_pre;
	int     resvDrain_post;
	final int scfsi[][] = new int[2][4];
	//
	JIII_side_info() {
		tt[0][0] = new Jgr_info();
		tt[0][1] = new Jgr_info();
		tt[1][0] = new Jgr_info();
		tt[1][1] = new Jgr_info();
	}

	/** convert from L/R <. Mid/Side */
	final void ms_convert(final int gr) {
		final Jgr_info[] t = this.tt[gr];// java
		final float[] xr0 = t[0].xr;// java
		final float[] xr1 = t[1].xr;// java
		for( int i = 0; i < 576; ++i ) {
			final float l = xr0[i];
			final float r = xr1[i];
			xr0[i] = (l + r) * (Jutil.SQRT2 * 0.5f);
			xr1[i] = (l - r) * (Jutil.SQRT2 * 0.5f);
		}
	}
}
