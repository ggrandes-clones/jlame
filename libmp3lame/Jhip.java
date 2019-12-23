package libmp3lame;

import java.io.PrintStream;

import libmpghip.Jmpg123;
import libmpghip.Jmpstr_tag;

public final class Jhip extends Jmpstr_tag {
	private static final int smpls[][] = {// [2][4] = {
			/* Layer   I    II   III */
			{0, 384, 1152, 1152}, /* MPEG-1     */
			{0, 384, 1152, 576} /* MPEG-2(.5) */
		};
	/**
	 * For lame_decode:  return code
	 * -1     error
	 *  0     ok, but need more data before outputing any samples
	 *  n     number of samples output.  either 576 or 1152 depending on MP3 file.
	 *
	 *  java changed: uses check type of the out buffer for choosing decoder
	 */
    private final int decode1_headersB_clipchoice(final byte[] buffer, final int len,
		final Object pcm_l_raw, final Object pcm_r_raw, final int pcmoffset,
		final Jmp3data_struct mp3data,
		final int[] encdelay, final int[] encpadding,
		final Object p, final int psize)
		//final int decoded_sample_size, final JdecoderMP3 decodeMP3_ptr)
	{
		final int processed_mono_samples[] = new int[1];// java: processed_bytes changed to processed_mono_samples
		final int processed_samples; /* processed samples per channel */
		final int len_l = len < 0 ? Integer.MAX_VALUE : len;// len < INT_MAX ? (int) len : INT_MAX;
		final int psize_l = psize < 0 ? Integer.MAX_VALUE : psize;// psize < INT_MAX ? (int) psize : INT_MAX;

		mp3data.header_parsed = false;

		// final int ret = decodeMP3_ptr.decode( pmp, buffer, len_l, p, psize_l, processed_bytes );
		final int ret = (p instanceof short[]) ?
					decodeMP3( buffer, len_l, p, psize_l, processed_mono_samples ) :
					decodeMP3_unclipped( buffer, len_l, p, psize_l, processed_mono_samples );
		/* three cases:
		 * 1. headers parsed, but data not complete
		 *       pmp.header_parsed==1
		 *       pmp.framesize=0
		 *       pmp.fsizeold=size of last frame, or 0 if this is first frame
		 *
		 * 2. headers, data parsed, but ancillary data not complete
		 *       pmp.header_parsed==1
		 *       pmp.framesize=size of frame
		 *       pmp.fsizeold=size of last frame, or 0 if this is first frame
		 *
		 * 3. frame fully decoded:
		 *       pmp.header_parsed==0
		 *       pmp.framesize=0
		 *       pmp.fsizeold=size of frame (which is now the last frame)
		 *
		 */
		if( this.header_parsed || this.fsizeold > 0 || this.framesize > 0 ) {
			mp3data.header_parsed = true;
			mp3data.stereo = this.fr.stereo;
			mp3data.samplerate = Jmpstr_tag.freqs[this.fr.sampling_frequency];
			mp3data.mode = this.fr.mode;
			mp3data.mode_ext = this.fr.mode_ext;
			mp3data.framesize = smpls[this.fr.lsf][this.fr.lay];

	        /* free format, we need the entire frame before we can determine
	         * the bitrate.  If we haven't gotten the entire frame, bitrate=0 */
			if( this.fsizeold > 0 ) {
				mp3data.bitrate = (int)(8 * (4 + this.fsizeold) * mp3data.samplerate /
						(1.e3 * mp3data.framesize) + 0.5);
			} else if( this.framesize > 0 ) {
				mp3data.bitrate = (int)(8 * (4 + this.framesize) * mp3data.samplerate /
						(1.e3 * mp3data.framesize) + 0.5);
			} else {
				mp3data.bitrate = Jmpstr_tag.tabsel_123[this.fr.lsf][this.fr.lay - 1][this.fr.bitrate_index];
			}

			if( this.num_frames > 0 ) {
				/* Xing VBR header found and num_frames was set */
				mp3data.totalframes = this.num_frames;
				mp3data.nsamp = mp3data.framesize * this.num_frames;
				encdelay[0] = this.enc_delay;
				encpadding[0] = this.enc_padding;
			}
		}

		switch( ret ) {
		case Jmpg123.MP3_OK:
			switch( this.fr.stereo ) {
			case 1:
				//processed_samples = processed_bytes[0] / decoded_sample_size;
				if( p instanceof short[] ) {// if( decoded_sample_size == (Short.SIZE / 8) ) {
					processed_samples = processed_mono_samples[0];// >> 1;// 2 bytes per short
					final short[] pcm_l = (short[])pcm_l_raw;
					final short[] p_samples = (short[])p;
					for( int i = 0, pi = 0, po = pcmoffset; i < processed_samples; i++ ) {
						pcm_l[po++] = p_samples[pi++];
					}
				} else {
					processed_samples = processed_mono_samples[0];// >> 2;// 4 bytes per float
					final float[] pcm_l = (float[])pcm_l_raw;
					final float[] p_samples = (float[])p;
					for( int i = 0, pi = 0, po = pcmoffset; i < processed_samples; i++ ) {
						pcm_l[po++] = p_samples[pi++];
					}
				}
				break;
			case 2:
				// processed_samples = (processed_bytes[0] / decoded_sample_size) >> 1;
				if( p instanceof short[] ) {// if( decoded_sample_size == (Short.SIZE / 8) ) {
					processed_samples = processed_mono_samples[0] >> 1;//(1 + 1);// 2 bytes per short, 2 channels
					final short[] pcm_l = (short[])pcm_l_raw, pcm_r = (short[])pcm_r_raw;
					final short[] p_samples = (short[])p;
					for( int i = 0, pi = 0, po = pcmoffset; i < processed_samples; i++ ) {
						pcm_l[po  ] = p_samples[pi++];
						pcm_r[po++] = p_samples[pi++];
					}
				} else {
					processed_samples = processed_mono_samples[0] >> 1;//(2 + 1);// 4 bytes per float, 2 channels
					final float[] pcm_l = (float[])pcm_l_raw, pcm_r = (float[])pcm_r_raw;
					final float[] p_samples = (float[])p;
					for( int i = 0, pi = 0, po = pcmoffset; i < processed_samples; i++ ) {
						pcm_l[po  ] = p_samples[pi++];
						pcm_r[po++] = p_samples[pi++];
					}
				}
				break;
			default:
				processed_samples = -1;
				break;
			}
			break;

		case Jmpg123.MP3_NEED_MORE:
			processed_samples = 0;
			break;

		case Jmpg123.MP3_ERR:
			processed_samples = -1;
			break;

		default:
			processed_samples = -1;
			break;
		}

		/*fprintf(stderr,"ok, more, err:  %d %d %d\n", MP3_OK, MP3_NEED_MORE, MP3_ERR ); */
		/*fprintf(stderr,"ret = %d out=%d\n", ret, processed_samples ); */
		return processed_samples;
	}

	private static final int OUTSIZE_CLIPPED = (4096/* * Short.SIZE / 8*/);
	private static final short outclipped[] = new short[OUTSIZE_CLIPPED];

	public static final Jhip hip_decode_init() {
		final Jhip hip = new Jhip();
		hip.InitMP3();
		return hip;
	}

	public final int hip_decode_exit() {
		//if( hip != null ) {
			ExitMP3();
		//}
		return 0;
	}

	/* we forbid input with more than 1152 samples per channel for output in the unclipped mode */
	private static final int OUTSIZE_UNCLIPPED = (1152 * 2/* * Float.SIZE / 8*/);
	private static final float outunclipped[] = new float[OUTSIZE_UNCLIPPED];

	final int hip_decode1_unclipped(final byte[] buffer, final int len, final float pcm_l[], final float pcm_r[])
	{
		final Jmp3data_struct mp3data = new Jmp3data_struct();
		final int encdelay[] = new int[1];// TODO java: find a better way
		final int encpadding[] = new int[1];

		//if( hip != null ) {// java changed: uses check type of the out buffer for choosing decoder
			return decode1_headersB_clipchoice( buffer, len, pcm_l, pcm_r, 0, mp3data,
					encdelay, encpadding, outunclipped, OUTSIZE_UNCLIPPED );//,
					//Float.SIZE / 8, new JdecodeMP3_unclipped() );
		//}
		//return 0;
	}

	/**
	 * same as hip_decode1, but returns at most one frame and mp3 header data
	 *
	 * For hip_decode:  return code
	 *  -1     error
	 *   0     ok, but need more data before outputing any samples
	 *   n     number of samples output.  Will be at most one frame of
	 *         MPEG data.
	 */
	public final int hip_decode1_headers(final byte[] buffer,
		final int len, final short pcm_l[], final short pcm_r[], final int pcmoffset, final Jmp3data_struct mp3data)
	{
		final int encdelay[] = new int[1];// TODO java: find a better way
		final int encpadding[] = new int[1];
		return hip_decode1_headersB( buffer, len, pcm_l, pcm_r, pcmoffset, mp3data, encdelay, encpadding );
	}

	/** same as hip_decode, but returns at most one frame */
	public final int hip_decode1(final byte[] buffer, final int len, final short pcm_l[], final short pcm_r[]) {
		final Jmp3data_struct mp3data = new Jmp3data_struct();
		return hip_decode1_headers( buffer, len, pcm_l, pcm_r, 0, mp3data );
	}

	/**
	 * same as hip_decode, and also returns mp3 header data
	 *
	 * For hip_decode:  return code
	 *  -1     error
	 *   0     ok, but need more data before outputing any samples
	 *   n     number of samples output.  a multiple of 576 or 1152 depending on MP3 file.
	 */
	private final int hip_decode_headers(final byte[] buffer,
		int len, final short pcm_l[], final short pcm_r[], final Jmp3data_struct mp3data)
	{
		int     ret;
		int     totsize = 0;     /* number of decoded samples per channel */

		for( ;; ) {
			switch( ret = hip_decode1_headers( buffer, len, pcm_l, pcm_r, totsize, mp3data ) ) {
			case -1:
				return ret;
			case 0:
				return totsize;
			default:
				totsize += ret;
				len = 0;    /* future calls to decodeMP3 are just to flush buffers */
				break;
			}
		}
	}

	/*********************************************************************
	 * input 1 mp3 frame, output (maybe) pcm data.
	 *
	 *  nout = hip_decode(hip, mp3buf,len,pcm_l,pcm_r);
	 *
	 * input:
	 *    len          :  number of bytes of mp3 data in mp3buf
	 *    mp3buf[len]  :  mp3 data to be decoded
	 *
	 * output:
	 *    nout:  -1    : decoding error
	 *            0    : need more data before we can complete the decode
	 *           >0    : returned 'nout' samples worth of data in pcm_l,pcm_r
	 *    pcm_l[nout]  : left channel data
	 *    pcm_r[nout]  : right channel data
	 *
	 *********************************************************************/
	public final int hip_decode(final byte[] buffer, final int len, final short pcm_l[], final short pcm_r[]) {
		final Jmp3data_struct mp3data = new Jmp3data_struct();
		return hip_decode_headers( buffer, len, pcm_l, pcm_r, mp3data );
	}

	/** same as hip_decode1_headers, but also returns enc_delay and enc_padding
	   from VBR Info tag, (-1 if no info tag was found) */
	public final int hip_decode1_headersB(final byte[] buffer,
			final int len,
			final short pcm_l[], final short pcm_r[], final int pcmoffset,
			final Jmp3data_struct mp3data,
			final int[] encdelay, final int[] encpadding)
	{
		//if( hip != null ) {// java changed: uses check type of the out buffer for choosing decoder
			return decode1_headersB_clipchoice( buffer, len, pcm_l, pcm_r, pcmoffset, mp3data,
				   encdelay, encpadding, outclipped, OUTSIZE_CLIPPED );//,
				   //Short.SIZE / 8, new JdecodeMP3() );
		//}
		//return -1;
	}

	final void hip_set_pinfo(final Jplotting_data pd) {
		//if( hip != null ) {
			this.pinfo = pd;
		//}
	}

	public final void hip_set_errorf(final PrintStream func) {
		//if( hip != null ) {
			this.report_err = func;
		//}
	}

	public final void hip_set_debugf(final PrintStream func) {
		//if( hip != null ) {
			this.report_dbg = func;
		//}
	}

	public final void hip_set_msgf(final PrintStream func) {
		//if( hip != null ) {
			this.report_msg = func;
		//}
	}
}
