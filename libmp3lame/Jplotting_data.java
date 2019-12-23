package libmp3lame;

public final class Jplotting_data {
	int     frameNum;        /* current frame number */
	int     frameNum123;
	int     num_samples;     /* number of pcm samples read for this frame */
	double  frametime;       /* starting time of frame, in seconds */
	final double  pcmdata[][] = new double[2][1600];
	final double  pcmdata2[][] = new double[2][1152 + 1152 - Jencoder.DECDELAY];
	final double  xr[][][] = new double[2][2][576];
	public final double  mpg123xr[][][] = new double[2][2][576];
	final double  ms_ratio[] = new double[2];
	final double  ms_ener_ratio[] = new double[2];

	/* L,R, M and S values */
	final double  energy_save[][] = new double[4][Jencoder.BLKSIZE]; /* psymodel is one ahead */
	final double  energy[][][] = new double[2][4][Jencoder.BLKSIZE];
	final double  pe[][] = new double[2][4];
	final double  thr[][][] = new double[2][4][Jencoder.SBMAX_l];
	final double  en[][][] = new double[2][4][Jencoder.SBMAX_l];
	final double  thr_s[][][] = new double[2][4][3 * Jencoder.SBMAX_s];
	final double  en_s[][][] = new double[2][4][3 * Jencoder.SBMAX_s];
	final double  ers_save[] = new double[4];     /* psymodel is one ahead */
	final double  ers[][] = new double[2][4];

	public final double  sfb[][][] = new double[2][2][Jencoder.SBMAX_l];
	public final double  sfb_s[][][] = new double[2][2][3 * Jencoder.SBMAX_s];
	final double  LAMEsfb[][][] = new double[2][2][Jencoder.SBMAX_l];
	final double  LAMEsfb_s[][][] = new double[2][2][3 * Jencoder.SBMAX_s];

	final int     LAMEqss[][] = new int[2][2];
	public final int     qss[][] = new int[2][2];
	public final int     big_values[][] = new int[2][2];
	public final int     sub_gain[][][] = new int[2][2][3];

	final double  xfsf[][][] = new double[2][2][Jencoder.SBMAX_l];
	final double  xfsf_s[][][] = new double[2][2][3 * Jencoder.SBMAX_s];

	final int     over[][] = new int[2][2];
	final double  tot_noise[][] = new double[2][2];
	final double  max_noise[][] = new double[2][2];
	final double  over_noise[][] = new double[2][2];
	final int     over_SSD[][] = new int[2][2];
	final int     blocktype[][] = new int[2][2];
	public final int     scalefac_scale[][] = new int[2][2];
	public final boolean preflag[][] = new boolean[2][2];
	public final int     mpg123blocktype[][] = new int[2][2];
	public final int     mixed[][] = new int[2][2];
	public final int     mainbits[][] = new int[2][2];
	public final int     sfbits[][] = new int[2][2];
	final int     LAMEmainbits[][] = new int[2][2];
	final int     LAMEsfbits[][] = new int[2][2];
	int     framesize;
	public int stereo;
	public boolean js;
	public boolean ms_stereo;
	public boolean i_stereo;
	public int emph;
	public int bitrate;
	public int sampfreq;
	public int maindata;
	public boolean crc;
	public int padding;
	public final int     scfsi[] = new int[2];
	int mean_bits, resvsize;
	int     totbits;
}
