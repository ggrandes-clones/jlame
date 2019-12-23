package libmpghip;

public final class Jmpg123 {
	public static final int MP3_ERR = -1;
	public static final int MP3_OK  = 0;
	public static final int MP3_NEED_MORE = 1;

	static final int SBLIMIT = 32;
	static final int SSLIMIT = 18;

	// private static final int MPG_MD_STEREO       = 0;// FIXME never uses
	static final int MPG_MD_JOINT_STEREO = 1;
	// private static final int MPG_MD_DUAL_CHANNEL = 2;// FIXME never uses
	static final int MPG_MD_MONO         = 3;

	static final int MAXFRAMESIZE = 2880;

	/* AF: ADDED FOR LAYER1/LAYER2 */
	static final int SCALE_BLOCK  = 12;

	/** Pre Shift fo 16 to 8 bit converter table */
	// private static final int AUSHIFT = 3;// FIXME never uses

	static final double M_SQRT2 = Math.sqrt( 2. );
}
