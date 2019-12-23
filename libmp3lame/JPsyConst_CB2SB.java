package libmp3lame;

@SuppressWarnings("unused")
final class JPsyConst_CB2SB {
	final float masking_lower[] = new float[Jencoder.CBANDS];
	final float minval[] = new float[Jencoder.CBANDS];
	final float rnumlines[] = new float[Jencoder.CBANDS];
	final float mld_cb[] = new float[Jencoder.CBANDS];

	final float mld[] = new float[(Jencoder.SBMAX_l >= Jencoder.SBMAX_s ? Jencoder.SBMAX_l : Jencoder.SBMAX_s)];
	final float bo_weight[] = new float[(Jencoder.SBMAX_l >= Jencoder.SBMAX_s ? Jencoder.SBMAX_l : Jencoder.SBMAX_s)]; /* band weight long scalefactor bands, at transition */
	float attack_threshold; /* short block tuning */
	int     s3ind[][] = new int[Jencoder.CBANDS][2];
	int     numlines[] = new int[Jencoder.CBANDS];
	int     bm[] = new int[(Jencoder.SBMAX_l >= Jencoder.SBMAX_s ? Jencoder.SBMAX_l : Jencoder.SBMAX_s)];// FIXME never uses bm
	int     bo[] = new int[(Jencoder.SBMAX_l >= Jencoder.SBMAX_s ? Jencoder.SBMAX_l : Jencoder.SBMAX_s)];
	int     npart;
	int     n_sb; /* SBMAX_l or SBMAX_s */
	float[] s3;
	//
	final void copyFrom(final JPsyConst_CB2SB p) {
		System.arraycopy( p.masking_lower, 0, this.masking_lower, 0, Jencoder.CBANDS );
		System.arraycopy( p.minval, 0, this.minval, 0, Jencoder.CBANDS );
		System.arraycopy( p.rnumlines, 0, this.rnumlines, 0, Jencoder.CBANDS );
		System.arraycopy( p.mld_cb, 0, this.mld_cb, 0, Jencoder.CBANDS );
		System.arraycopy( p.mld, 0, this.mld, 0, (Jencoder.SBMAX_l >= Jencoder.SBMAX_s ? Jencoder.SBMAX_l : Jencoder.SBMAX_s) );
		System.arraycopy( p.bo_weight, 0, this.bo_weight, 0, (Jencoder.SBMAX_l >= Jencoder.SBMAX_s ? Jencoder.SBMAX_l : Jencoder.SBMAX_s) );
		this.attack_threshold = p.attack_threshold;
		int i = Jencoder.CBANDS;
		final int[][] buf = this.s3ind;
		final int[][] ibuf = p.s3ind;
		do {
			final int[] ib = ibuf[--i];
			final int[] b = buf[i];
			b[0] = ib[0];
			b[1] = ib[1];
		} while( i > 0 );
		System.arraycopy( p.numlines, 0, this.numlines, 0, Jencoder.CBANDS );
		System.arraycopy( p.bm, 0, this.bm, 0, (Jencoder.SBMAX_l >= Jencoder.SBMAX_s ? Jencoder.SBMAX_l : Jencoder.SBMAX_s) );
		System.arraycopy( p.bo, 0, this.bo, 0, (Jencoder.SBMAX_l >= Jencoder.SBMAX_s ? Jencoder.SBMAX_l : Jencoder.SBMAX_s) );
		this.npart = p.npart;
		this.n_sb = p.n_sb;
		this.s3 = p.s3;
	}
}
