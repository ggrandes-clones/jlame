package libmp3lame;

import java.io.PrintStream;

/*
 *	lame utility library source file
 *
 *	Copyright (c) 1999 Albert L Faber
 *	Copyright (c) 2000-2005 Alexander Leidinger
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/* $Id: util.c,v 1.159 2017/09/06 15:07:30 robert Exp $ */

// util.c

public final class Jutil {
	static final long MAX_U_32_NUM = 0xffffffffL;
	/** smallest such that 1.0+DBL_EPSILON != 1.0 */
	static final double DBL_EPSILON = 2.2204460492503131e-016;
	/* smallest such that 1.0+FLT_EPSILON != 1.0 */
	private static final float FLT_EPSILON  = 1.192092896e-07F;

	//private static final float LOG2 = (float)Math.log( 2 );// 0.69314718055994530942;
	static final float LOG10 = (float)Math.log( 10 );// 2.30258509299404568402
	static final float SQRT2 = (float)Math.sqrt( 2 );// 1.41421356237309504880
	/* log/log10 approximations */// java: extracted inplace
/* #ifdef USE_FAST_LOG
	static final float FAST_LOG10(final float x)                 { return fast_log2( x ) * (LOG2 / LOG10); }
	static final float FAST_LOG(final float x)                   { return fast_log2( x ) * LOG2; }
	static final float FAST_LOG10_X(final float x, final float y){ return fast_log2( x ) * (LOG2 / LOG10 * y); }
	static final float FAST_LOG_X(final float x, final float y)  { return fast_log2( x ) * (LOG2 * y); }
#else
	static final float FAST_LOG10(final float x)                 { return (float)Math.log10( (double)x ); }
	static final float FAST_LOG(final float x)                   { return (float)Math.log( (double)x ); }
	static final float FAST_LOG10_X(final float x, final float y){ return ((float)Math.log10( (double)x ) * (y)); }
	static final float FAST_LOG_X(final float x, final float y)  { return ((float)Math.log( (double)x ) * (y)); }
#endif */

	//private static final int CRC16_POLYNOMIAL = 0x8005;

	static final int MAX_BITS_PER_CHANNEL = 4095;
	static final int MAX_BITS_PER_GRANULE = 7680;

	/***********************************************************************
	*
	*  Global Function Definitions
	*
	***********************************************************************/

	/* those ATH formulas are returning their minimum value for input = -1*/

	private static final float ATHformula_GB(float f, final float value, final float f_min, final float f_max)
	{
		/* from Painter & Spanias
		   modified by Gabriel Bouvigne to better fit the reality
		   ath =    3.640 * pow(f,-0.8)
		   - 6.800 * exp(-0.6*pow(f-3.4,2.0))
		   + 6.000 * exp(-0.15*pow(f-8.7,2.0))
		   + 0.6* 0.001 * pow(f,4.0);


		   In the past LAME was using the Painter &Spanias formula.
		   But we had some recurrent problems with HF content.
		   We measured real ATH values, and found the older formula
		   to be inacurate in the higher part. So we made this new
		   formula and this solved most of HF problematic testcases.
		   The tradeoff is that in VBR mode it increases a lot the
		   bitrate. */


		/*this curve can be udjusted according to the VBR scale:
		it adjusts from something close to Painter & Spanias
		on V9 up to Bouvigne's formula for V0. This way the VBR
		bitrate is more balanced according to the -V value.*/

		/* the following Hack allows to ask for the lowest value */
		if( f < -.3 ) {
			f = 3410;
		}

		f /= 1000;          /* convert to khz */
		f = ( f_min >= f ? f_min : f );
		f = ( f_max <= f ? f_max : f );

		final float ath = (float)(3.640 * Math.pow( f, -0.8 )
			- 6.800 * Math.exp(-0.6 * Math.pow( f - 3.4, 2.0 ))
			+ 6.000 * Math.exp(-0.15 * Math.pow( f - 8.7, 2.0 ))
			+ (0.6 + 0.04 * value) * 0.001 * Math.pow( f, 4.0 ));
		return ath;
	}

	static final float ATHformula(final JSessionConfig cfg, final float f) {
		float ath;
		switch( cfg.ATHtype ) {
		case 0:
			ath = ATHformula_GB( f, 9, 0.1f, 24.0f );
			break;
		case 1:
			ath = ATHformula_GB( f, -1, 0.1f, 24.0f ); /*over sensitive, should probably be removed */
			break;
		case 2:
			ath = ATHformula_GB( f, 0, 0.1f, 24.0f );
			break;
		case 3:
			ath = ATHformula_GB( f, 1, 0.1f, 24.0f ) + 6; /*modification of GB formula by Roel */
			break;
		case 4:
			ath = ATHformula_GB( f, cfg.ATHcurve, 0.1f, 24.0f );
			break;
		case 5:
			ath = ATHformula_GB( f, cfg.ATHcurve, 3.41f, 16.1f );
			break;
		default:
			ath = ATHformula_GB( f, 0, 0.1f, 24.0f );
			break;
		}
		return ath;
	}

	/* see for example "Zwicker: Psychoakustik, 1982; ISBN 3-540-11401-7 */
	/** input: freq in hz  output: barks */
	static final float freq2bark(float freq) {
		if( freq < 0 ) {
			freq = 0;
		}
		freq *= 0.001f;
		return 13.0f * (float)Math.atan( (double)(.76f * freq) ) + 3.5f * (float)Math.atan( (double)(freq * freq / (7.5f * 7.5f)) );
	}

	/**
	 *
	 * @param bRate legal rates from 8 to 320
	 * @param version MPEG-1 or MPEG-2 LSF
	 * @param samplerate
	 * @return
	 */
	static final int FindNearestBitrate(final int bRate, int version, final int samplerate) {
		if( samplerate < 16000 ) {
			version = 2;
		}

		final int[] bitrate_table = Jtables.bitrate_table[version];// java
		int bitrate = bitrate_table[1];

		for( int i = 2; i <= 14; i++ ) {
			final int v = bitrate_table[i];// java
			if( v > 0 ) {
				if( Math.abs( v - bRate ) < Math.abs( bitrate - bRate ) ) {
					bitrate = v;
				}
			}
		}
		return bitrate;
	}

	private static final int full_bitrate_table[] =
		{ 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320 };
	/** Used to find table index when
	 * we need bitrate-based values
	 * determined using tables
	 *
	 * bitrate in kbps
	 *
	 * Gabriel Bouvigne 2002-11-03
	 */
	static final int nearestBitrateFullIndex(final int bitrate) {
		/* borrowed from DM abr presets */
		int lower_range = 0, lower_range_kbps = 0, upper_range = 0, upper_range_kbps = 0;

		/* We assume specified bitrate will be 320kbps */
		upper_range_kbps = full_bitrate_table[16];
		upper_range = 16;
		lower_range_kbps = full_bitrate_table[16];
		lower_range = 16;

	    /* Determine which significant bitrates the value specified falls between,
	     * if loop ends without breaking then we were correct above that the value was 320
	     */
		for( int b = 1; b < 17; b++ ) {
			if( Math.max( bitrate, full_bitrate_table[b] ) != bitrate ) {
				upper_range = b;
				upper_range_kbps = full_bitrate_table[b];
				lower_range = b - 1;
				lower_range_kbps = full_bitrate_table[lower_range];
				break;      /* We found upper range */
			}
		}

		/* Determine which range the value specified is closer to */
		if( (upper_range_kbps - bitrate) > (bitrate - lower_range_kbps) ) {
			return lower_range;
		}
		return upper_range;
	}

	/** map frequency to a valid MP3 sample frequency
	 *
	 * Robert Hegemann 2000-07-01
	 */
	static final int map2MP3Frequency(final int freq) {
		if( freq <= 8000 ) {
			return 8000;
		}
		if( freq <= 11025 ) {
			return 11025;
		}
		if( freq <= 12000 ) {
			return 12000;
		}
		if( freq <= 16000 ) {
			return 16000;
		}
		if( freq <= 22050 ) {
			return 22050;
		}
		if( freq <= 24000 ) {
			return 24000;
		}
		if( freq <= 32000 ) {
			return 32000;
		}
		if( freq <= 44100 ) {
			return 44100;
		}

		return 48000;
	}

	/**
	 * convert bitrate in kbps to index
	 *
	 * @param bRate legal rates from 32 to 448 kbps
	 * @param version MPEG-1 or MPEG-2/2.5 LSF
	 * @param samplerate
	 * @return
	 */
	static final int BitrateIndex(final int bRate, int version, final int samplerate) {
		if( samplerate < 16000 ) {
			version = 2;
		}
		for( int i = 0; i <= 14; i++ ) {
			if( Jtables.bitrate_table[version][i] > 0 ) {
				if( Jtables.bitrate_table[version][i] == bRate ) {
					return i;
				}
			}
		}
		return -1;
	}

	/** convert samp freq in Hz to index */
	static final int SmpFrqIndex(final int sample_freq) {
		switch( sample_freq ) {
		case 44100:
			return 0;
		case 48000:
			return 1;
		case 32000:
			return 2;
		case 22050:
			return 0;
		case 24000:
			return 1;
		case 16000:
			return 2;
		case 11025:
			return 0;
		case 12000:
			return 1;
		case 8000:
			return 2;
		default:
			return -1;
		}
	}
	/** convert samp freq in Hz to version */
	static final int getVersion(final int sample_freq) {
		switch( sample_freq ) {
		case 44100:
			return 1;
		case 48000:
			return 1;
		case 32000:
			return 1;
		case 22050:
			return 0;
		case 24000:
			return 0;
		case 16000:
			return 0;
		case 11025:
			return 0;
		case 12000:
			return 0;
		case 8000:
			return 0;
		default:;
			return 0;
		}
	}

	/*****************************************************************************
	*
	*  End of bit_stream.c package
	*
	*****************************************************************************/

	/* resampling via FIR filter, blackman window */
	private static final float blackman(float x, final float fcn, final int l) {
	    /* This algorithm from:
	       SIGNAL PROCESSING ALGORITHMS IN FORTRAN AND C
	       S.D. Stearns and R.A. David, Prentice-Hall, 1992
	     */
		final double wcn = (Math.PI * fcn);

		x /= l;
		if( x < 0 ) {
			x = 0;
		}
		if( x > 1 ) {
			x = 1;
		}
		final double x2 = x - .5;

		final double bkwn = 0.42 - 0.5 * Math.cos( 2. * Math.PI * x ) + 0.08 * Math.cos( 4. * Math.PI * x );
		if( ( x2 < 0 ? -x2 : x2 ) < 1e-9 ) {
			return (float)(wcn / Math.PI);
		}// else {
			return (float)(bkwn * Math.sin( l * wcn * x2 ) / (Math.PI * l * x2));
		//}
	}

	/** gcd - greatest common divisor
	 * Joint work of Euclid and M. Hendry */
	private static final int gcd(final int i, final int j) {
		return j != 0 ? gcd( j, i % j ) : i;
	}

	/**
	 * @return java: n_out | (num_used << 32)
	 */
	private static final long fill_buffer_resample(final Jlame_internal_flags gfc,
		final float[] outbuf, final int outoffset,
		final int desired_len,
		final float[] inbuf, int inoffset,
		final int len/*, final int[] num_used*/, final int ch)
	{
		final JSessionConfig cfg = gfc.cfg;
		final JEncStateVar esv = gfc.sv_enc;
		final double resample_ratio = (double)cfg.samplerate_in / (double)cfg.samplerate_out;
		float   offset, xvalue;
		float   fcn, intratio;
		float[] inbuf_old;
		/* number of convolution functions to pre-compute */
		int bpc = cfg.samplerate_out / gcd(cfg.samplerate_out, cfg.samplerate_in);
		if( bpc > JEncStateVar.BPC ) {
			bpc = JEncStateVar.BPC;
		}
		final int bpc2 = bpc << 1;// java

		intratio = (Math.abs(resample_ratio - Math.floor(.5 + resample_ratio)) < FLT_EPSILON) ? 1 : 0;
		fcn = (float)(1.00 / resample_ratio);
		if( fcn > 1.00f ) {
			fcn = 1.00f;
		}
		int filter_l = 31;     /* must be odd */
		filter_l += intratio; /* unless resample_ratio=int, it must be even */

		final int BLACKSIZE = filter_l + 1; /* size of data needed for FIR */

		final float[][] blackfilt = esv.blackfilt;// java
		if( ! gfc.fill_buffer_resample_init ) {
			esv.inbuf_old[0] = new float[ BLACKSIZE ];
			esv.inbuf_old[1] = new float[ BLACKSIZE ];

			for( int i = 0; i <= bpc2; ++i ) {
				blackfilt[i] = new float[ BLACKSIZE ];
			}

			esv.itime[0] = 0;
			esv.itime[1] = 0;

			/* precompute blackman filter coefficients */
			for( int j = 0; j <= bpc2; j++ ) {
				float sum = 0.f;
				offset = (j - bpc) / (float)bpc2;
				final float[] blackfilt_j = blackfilt[j];// java
				for( int i = 0; i <= filter_l; i++ ) {
					sum += blackfilt_j[i] = blackman(i - offset, fcn, filter_l);
				}
				for( int i = 0; i <= filter_l; i++ ) {
					blackfilt_j[i] /= sum;
				}
			}
			gfc.fill_buffer_resample_init = true;
		}

		inbuf_old = esv.inbuf_old[ch];

		/* time of j'th element in inbuf = itime + j/ifreq; */
		/* time of k'th element in outbuf   =  j/ofreq */
		final int filter_l2 = filter_l >> 1;// java
		int j = 0;
		int k = 0;
		for( ; k < desired_len; k++ ) {
			final double time0 = k * resample_ratio; /* time of k'th output sample */

			j = (int)Math.floor( time0 - esv.itime[ch] );

			/* check if we need more input data */
			final int jfilter_l2 = j - filter_l2;// java
			if( (filter_l + jfilter_l2) >= len ) {
				break;
			}

			/* blackman filter.  by default, window centered at j+.5(filter_l%2) */
			/* but we want a window centered at time0.   */
			offset = (float)(time0 - esv.itime[ch] - (j + .5 * (filter_l & 1)));

			/* find the closest precomputed window for this offset: */
			final int joff = (int)Math.floor((offset * bpc2) + bpc + .5);
			final float[] blackfilt_joff = blackfilt[joff];// java

			xvalue = 0.f;
			for( int i = 0; i <= filter_l; ++i ) {
				final int j2 = i + jfilter_l2;
				final float y = (j2 < 0) ? inbuf_old[BLACKSIZE + j2] : inbuf[inoffset + j2];
// #ifdef PRECOMPUTE
				xvalue += y * blackfilt_joff[i];
// #else
//				xvalue += y * blackman( i - offset, fcn, filter_l ); /* very slow! */
// #endif
			}
			outbuf[outoffset + k] = xvalue;
		}

		/* k = number of samples added to outbuf */
		/* last k sample used data from [j-filter_l/2,j+filter_l-filter_l/2]  */

		/* how many samples of input data were used:  */
		final int num_used = Math.min(len, filter_l + j - filter_l2);

		/* adjust our input time counter.  Incriment by the number of samples used,
		* then normalize so that next output sample is at time 0, next
		* input buffer is at time itime[ch] */
		esv.itime[ch] += num_used - k * resample_ratio;

		/* save the last BLACKSIZE samples into the inbuf_old buffer */
		if( num_used >= BLACKSIZE ) {
			inoffset += num_used - BLACKSIZE;
			for( int i = 0; i < BLACKSIZE; i++, inoffset++ ) {
				inbuf_old[i] = inbuf[inoffset];
			}
		} else {
			/* shift in *num_used samples into inbuf_old  */
			final int n_shift = BLACKSIZE - num_used; /* number of samples to shift */

	        /* shift n_shift samples by *num_used, to make room for the
	         * num_used new samples */
			int i = 0;
			for( j = num_used; i < n_shift; ++i, ++j ) {
				inbuf_old[i] = inbuf_old[j];
			}

			/* shift in the *num_used samples */
			for( ; i < BLACKSIZE; ++i, inoffset++ ) {
				inbuf_old[i] = inbuf[inoffset];
			}
		}
		return (long)k | ((long)num_used << 32);           /* return the number samples created at the new samplerate */
	}

	static final boolean isResamplingNecessary(final JSessionConfig cfg) {
		final int l = (int)(cfg.samplerate_out * 0.9995f);
		final int h = (int)(cfg.samplerate_out * 1.0005f);
		return (cfg.samplerate_in < l) || (h < cfg.samplerate_in);
	}

	/** copy in new samples from in_buffer into mfbuf, with resampling
	   if necessary.  n_in = number of samples from the input buffer that
	   were used.  n_out = number of samples copied into mfbuf

	   @return java: n_out | (n_in << 32)
	  */
	static final long fill_buffer(final Jlame_internal_flags gfc,
		final float mfbuf[/* 2 */][],
		final float in_buffer[/* 2 */][], final int inoffset,
		final int nsamples/*, final int[] n_in, final int[] n_out*/)
	{
		final JSessionConfig cfg = gfc.cfg;
		final int mf_size = gfc.sv_enc.mf_size;
		final int framesize = 576 * cfg.mode_gr;
		int ch = 0;
		final int nch = cfg.channels_out;

		/* copy in new samples into mfbuf, with resampling if necessary */
		if( isResamplingNecessary( cfg ) ) {
			long tmp;
			do {
				tmp/* nout */= fill_buffer_resample( gfc, mfbuf[ch], mf_size, framesize, in_buffer[ch], inoffset, nsamples/*, n_in*/, ch );
			} while( ++ch < nch );
			// n_out[0] = nout;
			return tmp;
		}// else {
			final int nout = (framesize <= nsamples ? framesize : nsamples);
			do {
				System.arraycopy( in_buffer[ch], inoffset, mfbuf[ch], mf_size, nout );
			} while( ++ch < nch );
			//n_out[0] = nout;
			//n_in[0] = nout;
			return (long)nout | ((long)nout << 32);
		//}
	}

	/***********************************************************************
	*
	*  Message Output
	*
	***********************************************************************/

	//private static final void lame_report_def(final String format, final Object... args) {
	//	System.err.printf( format, args );
	//	System.err.flush(); /* an debug function should flush immediately */
	//}
	public static final PrintStream lame_report_def = System.err;

	public static final void lame_report_fnc(final PrintStream print_f, final String format, final Object... args) {
		if( print_f != null ) {
			print_f.printf( format, args );
		}
	}

	static final void lame_debugf(final Jlame_internal_flags gfc, final String format, final Object... args) {
		if( gfc != null && gfc.report_dbg != null ) {
			gfc.report_dbg.printf( format, args );
		}
	}

	static final void lame_msgf(final Jlame_internal_flags gfc, final String format, final Object... args) {
		if( gfc != null && gfc.report_msg != null ) {
			gfc.report_msg.printf( format, args );
		}
	}

	static final void lame_errorf(final Jlame_internal_flags gfc, final String format, final Object... args) {
		if( gfc != null && gfc.report_err != null ) {
			gfc.report_err.printf( format, args );
		}
	}

/***********************************************************************
 *
 * Fast Log Approximation for log2, used to approximate every other log
 * (log10 and log)
 * maximum absolute error for log10 is around 10-6
 * maximum *relative* error can be high when x is almost 1 because error/log10(x) tends toward x/e
 *
 * use it if typical RESULT values are > 1e-5 (for example if x>1.00001 or x<0.99999)
 * or if the relative precision in the domain around 1 is not important (result in 1 is exact and 0)
 *
 ***********************************************************************/

	private static final int LOG2_SIZE = 512;
	// private static final int LOG2_SIZE_L2 = 9;

	private static final float log_table[] = new float[LOG2_SIZE + 1];
	private static boolean init = false;

	static final void init_log_table() {
		/* Range for log2(x) over [1,2[ is [0,1[ */
		// assert((1 << LOG2_SIZE_L2) == LOG2_SIZE);

		if( ! init ) {
			for( int j = 0; j < LOG2_SIZE + 1; j++ ) {
				log_table[j] = (float)(Math.log( 1.0 + j / (double) LOG2_SIZE) / Math.log( 2.0 ));
			}
		}
		init = true;
	}
/*
	private static final float fast_log2(final float x) {
		final int fi = Float.floatToRawIntBits( x );
		int mantisse = fi & 0x7fffff;
		float log2val = ((fi >> 23) & 0xFF) - 0x7f;
		float partial = (mantisse & ((1 << (23 - LOG2_SIZE_L2)) - 1));
		partial *= 1.0f / ((1 << (23 - LOG2_SIZE_L2)));

		mantisse >>= (23 - LOG2_SIZE_L2);

		// log2val += log_table[mantisse];  without interpolation the results are not good
		log2val += log_table[mantisse] * (1.0f - partial) + log_table[mantisse + 1] * partial;

		return log2val;
	}
*/
}