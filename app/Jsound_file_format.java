package app;

public final class Jsound_file_format {
	static final int sf_unknown	= 0;
	static final int sf_raw	= 1;
	static final int sf_wave	= 2;
	static final int sf_aiff	= 3;
	/** MPEG Layer 1, aka mpg */
	static final int sf_mp1	= 4;
	/** MPEG Layer 2 */
	static final int sf_mp2	= 5;
	/** MPEG Layer 3 */
	static final int sf_mp3	= 6;
	/** MPEG Layer 1,2 or 3; whatever .mp3, .mp2, .mp1 or .mpg contains */
	static final int sf_mp123	= 7;
	static final int sf_ogg	= 8;
}
