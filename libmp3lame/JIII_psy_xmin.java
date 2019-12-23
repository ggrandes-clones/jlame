package libmp3lame;

final class JIII_psy_xmin {
	final float l[] = new float[Jencoder.SBMAX_l];
	final float s[][] = new float[Jencoder.SBMAX_s][3];
	//
	JIII_psy_xmin() {
	}
	JIII_psy_xmin(final JIII_psy_xmin p) {
		copyFrom( p );
	}
	final void copyFrom(final JIII_psy_xmin p) {
		System.arraycopy( p.l, 0, this.l, 0, Jencoder.SBMAX_l );
		int i = Jencoder.SBMAX_s;
		final float ibuf[][] = p.s;
		final float buf[][] = this.s;
		do {
			final float[] ib = ibuf[--i];
			final float[] b = buf[i];
			b[0] = ib[0];
			b[1] = ib[1];
			b[2] = ib[2];
		} while( i > 0 );
	}
}
