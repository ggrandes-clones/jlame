package app;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import libmp3lame.Jhip;
import libmp3lame.Jlame_global_flags;
import libmp3lame.Jmp3data_struct;

/*
 *      Get Audio routines source file
 *
 *      Copyright (c) 1999 Albert L Faber
 *                    2008-2012 Robert Hegemann
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/* $Id: get_audio.c,v 1.167 2017/09/06 15:07:29 robert Exp $ */

// get_audio.c

final class Jget_audio {

	private static final long MAX_U_32_NUM = 0xFFFFFFFFL;

	/* Seek method constants */

	private static final int SEEK_CUR = 1;
	private static final int SEEK_END = 2;
	private static final int SEEK_SET = 0;

// #define FLOAT_TO_UNSIGNED(f) ((unsigned long)(((long)((f) - 2147483648.0)) + 2147483647L + 1))
// #define UNSIGNED_TO_FLOAT(u) (((double)((long)((u) - 2147483647L - 1))) + 2147483648.0)

	private static final int uint32_high_low(final byte[] bytes, int offset) {
		final int hh = (int)bytes[offset++];
		final int hl = (int)bytes[offset++] & 0xff;
		final int lh = (int)bytes[offset++] & 0xff;
		final int ll = (int)bytes[offset  ] & 0xff;
		return (hh << 24) | (hl << 16) | (lh << 8) | ll;
	}

	// from https://github.com/shibatch/sleef/blob/master/java/org/naokishibata/sleef/FastMath.java
	/**
	    Returns the result of multiplying the floating-point number x
	    by 2 raised to the power q
	 */
	public static double ldexp(double x, int q) {
		int m = q >> 31;
		m = (((m + q) >> 9) - m) << 7;
		q = q - (m << 2);
		m += 0x3ff;
		m = m < 0     ? 0     : m;
		m = m > 0x7ff ? 0x7ff : m;
		double u = Double.longBitsToDouble(((long)m) << 52);
		x = x * u * u * u * u;
		u = Double.longBitsToDouble(((long)(q + 0x3ff)) << 52);
		return x * u;
	}
	private static final double read_ieee_extended_high_low(final RandomAccessFile fp) throws IOException {
		final byte bytes[] = new byte[10];// java: already zeroed
		fp.read( bytes, 0, 10 );
		{
			final int s = ((int)bytes[0] & 0x80);
			final int e_h = ((int)bytes[0] & 0x7F);
			final int e_l = (int)bytes[1] & 0xff;
			int e = (e_h << 8) | e_l;
			final int hm = uint32_high_low( bytes, 2 );
			final int lm = uint32_high_low( bytes, 6 );
			double result = 0;
			if( e != 0 || hm != 0 || lm != 0 ) {
				if( e == 0x7fff ) {
					result = Double.MAX_VALUE;// HUGE_VAL;
				}
				else {
					final double mantissa_h = (((double)(hm - 2147483647 - 1)) + 2147483648.0);// UNSIGNED_TO_FLOAT( hm );
					final double mantissa_l = (((double)(lm - 2147483647 - 1)) + 2147483648.0);// UNSIGNED_TO_FLOAT( lm );
					e -= 0x3fff;
					e -= 31;
					result = ldexp( mantissa_h, e );
					e -= 32;
					result += ldexp( mantissa_l, e );
				}
			}
			return s != 0 ? -result : result;
		}
	}

	private static final int read_16_bits_low_high(final RandomAccessFile fp) throws IOException {
		final byte bytes[] = new byte[2];// java: already zeroed = { 0, 0 };
		fp.read( bytes, 0, 2 );
		{
			final int low = (int)bytes[0] & 0xff;
			final int high = (int)bytes[1];
			return (high << 8) | low;
		}
	}

	private static final int read_32_bits_low_high(final RandomAccessFile fp) throws IOException {
		final byte bytes[] = new byte[4];// java: already zeroed = { 0, 0, 0, 0 };
		fp.read( bytes, 0, 4 );
		{
			final int low = (int)bytes[0] & 0xff;
			final int medl = (int)bytes[1] & 0xff;
			final int medh = (int)bytes[2] & 0xff;
			final int high = (int)bytes[3];
			return (high << 24) | (medh << 16) | (medl << 8) | low;
		}
	}

	private static final int read_16_bits_high_low(final RandomAccessFile fp) throws IOException {
		final byte bytes[] = new byte[2];// java: already zeroed = { 0, 0 };
		fp.read( bytes, 0, 2 );
		{
			final int low = (int)bytes[1] & 0xff;
			final int high = (int)bytes[0];
			return (high << 8) | low;
		}
	}

	private static final int read_32_bits_high_low(final RandomAccessFile fp) throws IOException {
		final byte bytes[] = new byte[4];// java: already zeroed = { 0, 0, 0, 0 };
		fp.read( bytes, 0, 4 );
		{
			final int low = (int)bytes[3] & 0xff;
			final int medl = (int)bytes[2] & 0xff;
			final int medh = (int)bytes[1] & 0xff;
			final int high = (int)bytes[0];
			return (high << 24) | (medh << 16) | (medl << 8) | low;
		}
	}

	private static final void write_16_bits_low_high(final RandomAccessFile fp, final int val) throws IOException {
		final byte bytes[] = new byte[2];// java: already zeroed = { 0, 0 };
		bytes[0] = (byte)(val);
		bytes[1] = (byte)(val >> 8);
		fp.write( bytes, 0, 2 );
	}

	private static final void write_32_bits_low_high(final RandomAccessFile fp, final int val) throws IOException {
		final byte bytes[] = new byte[4];// java: already zeroed = { 0, 0, 0, 0 };
		bytes[0] = (byte)val;
		bytes[1] = (byte)(val >> 8);
		bytes[2] = (byte)(val >> 16);
		bytes[3] = (byte)(val >> 24);
		fp.write( bytes, 0, 4 );
	}

	private static final Jget_audio_global_data global = new Jget_audio_global_data( );

	/* Replacement for forward fseek(,,SEEK_CUR), because fseek() fails on pipes */
	private static final int fskip(final RandomAccessFile fp, long offset, final int whence) throws IOException {
		final byte buffer[] = new byte[4096];

// # ifdef S_ISFIFO
		/* fseek is known to fail on pipes with several C-Library implementations
		   workaround: 1) test for pipe
		   2) for pipes, only relatvie seeking is possible
		   3)            and only in forward direction!
		   else fallback to old code
		 */
		{
			if( ! (fp instanceof RandomAccessFile) ) {
				if( whence != SEEK_CUR || offset < 0 ) {
					return -1;
				}
				while( offset > 0 ) {
					final int bytes_to_skip = (int)(buffer.length <= offset ? buffer.length : offset);
					final int read = fp.read( buffer, 0, bytes_to_skip );
					if( read < 1 ) {
						return -1;
					}
                    // assert( read <= LONG_MAX );
					offset -= (long)read;
				}
				return 0;
			}
		}
// #endif
		if( whence == SEEK_SET ) {
			fp.seek( offset );
			return 0;
		}
		if( whence == SEEK_CUR ) {
			fp.seek( offset + fp.getFilePointer() );
			return 0;
		}
		if( whence == SEEK_END ) {
			fp.seek( fp.length() + offset );
			return 0;
		}
/*
		if( whence != SEEK_CUR || offset < 0 ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("fskip problem: Mostly the return status of functions is not evaluate so it is more secure to polute <stderr>.\n");
			}
			return -1;
		}

		while( offset > 0 ) {
			final int bytes_to_skip = (int)(buffer.length <= offset ? buffer.length : offset);
			final int read = fp.read( buffer, 0, bytes_to_skip );
			if( read < 1 ) {
				return -1;
			}
			offset -= read;
		}
*/
		return 0;
	}
/*
	private static final long lame_get_file_size(final RandomAccessFile fp) {
		final struct stat sb;
		final int     fd = fileno( fp );

		if( 0 == fstat( fd, &sb ) ) {
			return sb.st_size;
		}
		return (off_t) - 1;
	}
*/
	static final RandomAccessFile init_outfile(final String outPath, final boolean decode ) {
		/* open the output file */
		if( 0 == outPath.compareTo("-") ) {
			// outf = System.out;
			// lame_set_stream_binary_mode( outf  );
			Jconsole.error_printf("java: Output to stdout is not implemented");
			return null;
		}// else {
			try {
				return new RandomAccessFile( outPath, Jparse.global_writer.flush_write ? "rws" : "rw"  );
			} catch(final FileNotFoundException fe) {
			}
		//}
		return null;
	}


	private static final void setSkipStartAndEnd(final Jlame_global_flags gfp, final int enc_delay, final int enc_padding) {
		int skip_start = 0, skip_end = 0;

		if( Jparse.global_decoder.mp3_delay_set ) {
			skip_start = Jparse.global_decoder.mp3_delay;
		}

		switch( Jparse.global_reader.input_format ) {
		case Jsound_file_format.sf_mp123:
			break;

		case Jsound_file_format.sf_mp3:
			if( skip_start == 0 ) {
				if( enc_delay > -1 || enc_padding > -1 ) {
					if( enc_delay > -1 ) {
						skip_start = enc_delay + 528 + 1;
					}
					if( enc_padding > -1 ) {
						skip_end = enc_padding - (528 + 1 );
					}
				} else {
					skip_start = gfp.lame_get_encoder_delay() + 528 + 1;
				}
			} else {
				/* user specified a value of skip. just add for decoder */
				skip_start += 528 + 1; /* mp3 decoder has a 528 sample delay, plus user supplied "skip" */
			}
			break;
		case Jsound_file_format.sf_mp2:
			skip_start += 240 + 1;
			break;
		case Jsound_file_format.sf_mp1:
			skip_start += 240 + 1;
			break;
		case Jsound_file_format.sf_raw:
			skip_start += 0; /* other formats have no delay *//* is += 0 not better ??? */
			break;
		case Jsound_file_format.sf_wave:
			skip_start += 0; /* other formats have no delay *//* is += 0 not better ??? */
			break;
		case Jsound_file_format.sf_aiff:
			skip_start += 0; /* other formats have no delay *//* is += 0 not better ??? */
			break;
		default:
			skip_start += 0; /* other formats have no delay *//* is += 0 not better ??? */
			break;
		}
		skip_start = skip_start < 0 ? 0 : skip_start;
		skip_end = skip_end < 0 ? 0 : skip_end;
		global.pcm16.skip_start = global.pcm32.skip_start = skip_start;
		global.pcm16.skip_end = global.pcm32.skip_end = skip_end;
	}

	static final int init_infile(final Jlame_global_flags gfp, final String inPath ) {
		final int[] enc_delay = { 0 }, enc_padding = { 0 };// TODO java: find a better way
		/* open the input file */
		global.count_samples_carefully = false;
		global.num_samples_read = 0;
		global.pcmbitwidth = Jparse.global_raw_pcm.in_bitwidth;
		global.pcmswapbytes = Jparse.global_reader.swapbytes;
		global.pcm_is_unsigned_8bit = ! Jparse.global_raw_pcm.in_signed;
		global.pcm_is_ieee_float = false;
		global.hip = null;
		global.music_in = null;
		// global.snd_file = 0;
		// global.in_id3v2_size = 0;
		global.in_id3v2_tag = null;
		if( is_mpeg_file_format( Jparse.global_reader.input_format ) != 0 ) {
			global.music_in = open_mpeg_file( gfp, inPath, enc_delay, enc_padding  );
		} else {
			// if( global.snd_file == 0 ) {// FIXME no variants
				global.music_in = open_wave_file( gfp, inPath, enc_delay, enc_padding );
			// }
		}
		global.pcm32.initPcmBuffer( Integer.SIZE / 8 );
		global.pcm16.initPcmBuffer( Short.SIZE / 8 );
		setSkipStartAndEnd( gfp, enc_delay[0], enc_padding[0] );
		{
			final long n = gfp.lame_get_num_samples();
			if( n != MAX_U_32_NUM ) {
				final long discard = (long)global.pcm32.skip_start + (long)global.pcm32.skip_end;
				gfp.lame_set_num_samples( n > discard ? n - discard : 0 );
			}
		}
		return (/*global.snd_file != null || */global.music_in != null) ? 1 : -1;
	}

	static final int samples_to_skip_at_start() {
		return global.pcm32.skip_start;
	}

	static final int samples_to_skip_at_end() {
		return global.pcm32.skip_end;
	}

	static final void close_infile( ) {
// #ifdef HAVE_MPGLIB
		if( global.hip != null ) {
			global.hip.hip_decode_exit(); /* release mp3decoder memory */
			global.hip = null;
		}
// #endif
		close_input_file( global.music_in  );
		global.pcm32.freePcmBuffer();
		global.pcm16.freePcmBuffer();
		global.music_in = null;
		global.in_id3v2_tag = null;
		global.in_id3v2_tag = null;
		// global.in_id3v2_size = 0;
	}

	/************************************************************************
	*
	* get_audio()
	*
	* PURPOSE:  reads a frame of audio data from a file to the buffer,
	*   aligns the data for future processing, and separates the
	*   left and right channels
	*
	************************************************************************/
	static final int get_audio(final Jlame_global_flags gfp, final int buffer[][]/*[2][1152]*/ ) {
		int used = 0, read = 0;
		do {
			read = get_audio_common( gfp, buffer, null );
			used = global.pcm32.addPcmBuffer( buffer[0], buffer[1], read );
		} while( used <= 0 && read > 0 );
		if( read < 0 ) {
			return read;
		}
		if( ! Jparse.global_reader.swap_channel ) {
			return global.pcm32.takePcmBuffer( buffer[0], buffer[1], used, 1152 );
		}// else {
			return global.pcm32.takePcmBuffer( buffer[1], buffer[0], used, 1152 );
		//}
	}

	/**
	  get_audio16 - behave as the original get_audio function, with a limited
	                16 bit per sample output
	*/
	static final int get_audio16(final Jlame_global_flags gfp, final short buffer[][]/*[2][1152]*/) {
		int used = 0, read = 0;
		do {
			read = get_audio_common( gfp, null, buffer );
			used = global.pcm16.addPcmBuffer( buffer[0], buffer[1], read );
		} while( used <= 0 && read > 0 );
		if( read < 0 ) {
			return read;
		}
		if( ! Jparse.global_reader.swap_channel ) {
			return global.pcm16.takePcmBuffer( buffer[0], buffer[1], used, 1152 );
		}// else {
			return global.pcm16.takePcmBuffer( buffer[1], buffer[0], used, 1152 );
		//}
	}

	/************************************************************************
	  get_audio_common - central functionality of get_audio*
	    in: gfp
	        buffer    output to the int buffer or 16-bit buffer
	   out: buffer    int output    (if buffer != NULL)
	        buffer16  16-bit output (if buffer == NULL)
	returns: samples read
	note: either buffer or buffer16 must be allocated upon call
	*/
	private static final int get_audio_common(final Jlame_global_flags gfp, final int buffer[][]/*[2][1152]*/, final short buffer16[][]/*[2][1152]*/)
	{
		final int   num_channels = gfp.lame_get_num_channels();
		final int   framesize = gfp.lame_get_framesize();// FIXME why int if tmp_num_samples is long?
		final int   insamp[] = new int[2 * 1152];
		final short buf_tmp16[][] = new short[2][1152];

		/* sanity checks, that's what we expect to be true */
		if( (num_channels < 1 || 2 < num_channels)
				||(framesize < 1 || 1152 < framesize) ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("Error: internal problem!\n");
			}
			return -1;
		}

		/*
		 * NOTE: LAME can now handle arbritray size input data packets,
		 * so there is no reason to read the input data in chuncks of
		 * size "framesize".  EXCEPT:  the LAME graphical frame analyzer
		 * will get out of sync if we read more than framesize worth of data.
		 */
		int samples_to_read = framesize;

		/* if this flag has been set, then we are carefull to read
		 * exactly num_samples and no more.  This is useful for .wav and .aiff
		 * files which have id3 or other tags at the end.  Note that if you
		 * are using LIBSNDFILE, this is not necessary
		 */
		if( global.count_samples_carefully ) {
			long tmp_num_samples;

			/* get num_samples */
			if( is_mpeg_file_format( Jparse.global_reader.input_format ) != 0 ) {
				tmp_num_samples = Jparse.global_decoder.mp3input_data.nsamp;
			} else {
				tmp_num_samples = gfp.lame_get_num_samples();
			}
			long remaining;
			if( global.num_samples_read < tmp_num_samples ) {
				remaining = tmp_num_samples - global.num_samples_read;
			} else {
				remaining = 0;
			}
			if( remaining < framesize && 0 != tmp_num_samples ) {
				samples_to_read = (int)remaining;// FIXME remaining should be calculated only here, not before
			}
		}

		int samples_read;
		if( is_mpeg_file_format( Jparse.global_reader.input_format ) != 0 ) {
			if( buffer != null ) {
				samples_read = read_samples_mp3( gfp, global.music_in, buf_tmp16 );
			} else {
				samples_read = read_samples_mp3( gfp, global.music_in, buffer16 );
			}
			if( samples_read < 0 ) {
				return samples_read;
			}
		} else {
			/* if( global.snd_file ) {
				samples_read = 0;
			} else */{
				samples_read = read_samples_pcm( global.music_in, insamp, num_channels * samples_to_read );
			}
			if( samples_read < 0 ) {
				return samples_read;
			}
			int p = samples_read;// insamp[ p ];
			samples_read /= num_channels;
			if( buffer != null ) { /* output to int buffer */
				if( num_channels == 2 ) {
					for( int i = samples_read; --i >= 0; ) {
						buffer[1][i] = insamp[ --p ];
						buffer[0][i] = insamp[ --p ];
					}
				} else if( num_channels == 1 ) {
					for( int i = samples_read; --i >= 0; ) {
						buffer[0][i] = insamp[ --p ];
						buffer[1][i] = 0;
					}
				}
			} else {          /* convert from int; output to 16-bit buffer */
				if( num_channels == 2 ) {
					for( int i = samples_read; --i >= 0; ) {
						buffer16[1][i] = (short)(insamp[ --p ] >> (Integer.SIZE - 16));
						buffer16[0][i] = (short)(insamp[ --p ] >> (Integer.SIZE - 16));
					}
				} else if( num_channels == 1 ) {
					for( int i = samples_read; --i >= 0; ) {
						buffer16[0][i] = (short)(insamp[ --p ] >> (Integer.SIZE - 16 ));
						buffer16[1][i] = 0;
					}
				}
			}
		}

		/* LAME mp3 output 16bit -  convert to int, if necessary */
		if( is_mpeg_file_format( Jparse.global_reader.input_format ) != 0 ) {
			if( buffer != null ) {
				if( num_channels == 2 ) {
					for( int i = samples_read; --i >= 0; ) {
						buffer[0][i] = buf_tmp16[0][i] << (Integer.SIZE - 16);
						buffer[1][i] = buf_tmp16[1][i] << (Integer.SIZE - 16);
					}
				} else if( num_channels == 1 ) {
					for( int i = samples_read; --i >= 0; ) {
						buffer[0][i] = buf_tmp16[0][i] << (Integer.SIZE - 16);
						buffer[1][i] = 0;
					}
				}
			}
		}


		/* if ... then it is considered infinitely long.
		   Don't count the samples */
		if( global.count_samples_carefully ) {
			global.num_samples_read += samples_read;
		}

		return samples_read;
	}

	static int read_samples_mp3(final Jlame_global_flags gfp, final RandomAccessFile musicin, final short mpg123pcm[][]/*[2][1152]*/) {
// #if defined(AMIGA_MPEGA)  ||  defined(HAVE_MPGLIB)
		final String type_name = "MP3 file";

		int out = lame_decode_fromfile( musicin, mpg123pcm[0], mpg123pcm[1], Jparse.global_decoder.mp3input_data );
		/*
		* out < 0:  error, probably EOF
		* out = 0:  not possible with lame_decode_fromfile() ???
		* out > 0:  number of output samples
		*/
		if( out < 0 ) {
			int i = 1152;
			do {
				mpg123pcm[0][--i] = 0;
				mpg123pcm[1][i] = 0;
			} while( i > 0 );
			return 0;
		}

		if( gfp.lame_get_num_channels() != Jparse.global_decoder.mp3input_data.stereo ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("Error: number of channels has changed in %s - not supported\n", type_name );
			}
			out = -1;
		}
		int samplerate = Jparse.global_reader.input_samplerate;
		if( samplerate == 0 ) {
			samplerate = Jparse.global_decoder.mp3input_data.samplerate;
		}
		if( gfp.lame_get_in_samplerate() != samplerate ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("Error: sample frequency has changed in %s - not supported\n", type_name );
			}
			out = -1;
		}
//#else
//		out = -1;
//#endif
		return out;
	}

	@SuppressWarnings("boxing")
	private static final boolean set_input_num_channels(final Jlame_global_flags gfp, final int num_channels)
	{
		if( gfp != null ) {
			if( gfp.lame_set_num_channels( num_channels ) ) {
				if( Jparse.global_ui_config.silent < 10 ) {
					Jconsole.error_printf("Unsupported number of channels: %d\n", num_channels);
				}
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings("boxing")
	private static final boolean set_input_samplerate(final Jlame_global_flags gfp, final int input_samplerate)
	{
		if( gfp != null ) {
			int sr = Jparse.global_reader.input_samplerate;
			if( sr == 0 ) {
				sr = input_samplerate;
			}
			if( gfp.lame_set_in_samplerate( sr ) ) {
				if( Jparse.global_ui_config.silent < 10 ) {
					Jconsole.error_printf("Unsupported sample rate: %d\n", sr);
				}
				return false;
			}
		}
		return true;
	}

	static final int WriteWaveHeader(final RandomAccessFile fp, final int pcmbytes, final int freq, final int channels, final int bits ) {
		final int bytes = (bits + 7) >> 3;

		try {
			/* quick and dirty, but documented */
			fp.write("RIFF".getBytes(), 0, 4 ); /* label */
			write_32_bits_low_high( fp, pcmbytes + 44 - 8 ); /* length in bytes without header */
			fp.write("WAVEfmt ".getBytes(), 0, 8 ); /* 2 labels */
			write_32_bits_low_high( fp, 2 + 2 + 4 + 4 + 2 + 2 ); /* length of PCM format declaration area */
			write_16_bits_low_high( fp, 1 ); /* is PCM? */
			write_16_bits_low_high( fp, channels ); /* number of channels */
			write_32_bits_low_high( fp, freq ); /* sample frequency in [Hz] */
			write_32_bits_low_high( fp, freq * channels * bytes ); /* bytes per second */
			write_16_bits_low_high( fp, channels * bytes ); /* bytes per sample time */
			write_16_bits_low_high( fp, bits ); /* bits per sample */
			fp.write("data".getBytes(), 0, 4 ); /* label */
			write_32_bits_low_high( fp, pcmbytes ); /* length in bytes of raw PCM data */
		} catch(final IOException ie) {
			return -1;
		}

		return 0;
	}

	/************************************************************************
	unpack_read_samples - read and unpack signed low-to-high byte or unsigned
	                      single byte input. (used for read_samples function)
	                      Output integers are stored in the native byte order
	                      (little or big endian).  -jd
	 * @param samples_to_read
	 * @param bytes_per_sample
	 * @param swap_order set for high-to-low byte order input stream
	 * @param sample_buffer [out]  (must be allocated up to samples_to_read upon call)
	 * @param pcm_in [i/o]
	 * @return number of samples read
	 * @throws IOException
	 */
	private static final int unpack_read_samples(final int samples_to_read, final int bytes_per_sample,
                    final boolean swap_order, final int[] sample_buffer, final RandomAccessFile pcm_in) throws IOException
	{
		final byte[] ip = new byte[bytes_per_sample * samples_to_read];// java changed (unsigned char *) sample_buffer; /* input pointer */
		// final int b = Integer.SIZE;

		//{
			// samples_read = fread(sample_buffer, bytes_per_sample, samples_to_read, pcm_in);
			pcm_in.readFully( ip );
			final int samples_read = samples_to_read;
			//assert( samples_read_ <= INT_MAX );
			//samples_read = (int) samples_read_;
		//}
		int op = samples_to_read;// java sample_buffer[ op ], sample_buffer + samples_read; /* output pointer */
/*
#define GA_URS_IFLOOP( ga_urs_bps ) \
    if( bytes_per_sample == ga_urs_bps ) \
      for( i = samples_read * bytes_per_sample; (i -= bytes_per_sample) >=0;)
*/

		if( ! swap_order ) {
			//GA_URS_IFLOOP(1)
			if( bytes_per_sample == 1 ) {
				for( int i = samples_read * bytes_per_sample; (i -= bytes_per_sample) >= 0; ) {
					sample_buffer[--op] = ip[i] << (Integer.SIZE - 8);
				}
			}
			//GA_URS_IFLOOP(2)
			if( bytes_per_sample == 2 ) {
				for( int i = samples_read * bytes_per_sample; (i -= bytes_per_sample) >= 0; ) {
					sample_buffer[--op] = ((int)ip[i]&0xff) << (Integer.SIZE - 16) | ip[i + 1] << (Integer.SIZE - 8);
				}
			}
			//GA_URS_IFLOOP(3)
			if( bytes_per_sample == 3 ) {
				for( int i = samples_read * bytes_per_sample; (i -= bytes_per_sample) >= 0; ) {
					sample_buffer[--op] = ((int)ip[i] & 0xff) << (Integer.SIZE - 24) | ((int)ip[i + 1] & 0xff) << (Integer.SIZE - 16) | ip[i + 2] << (Integer.SIZE - 8);
				}
			}
			//GA_URS_IFLOOP(4)
			if( bytes_per_sample == 4 ) {
				for( int i = samples_read * bytes_per_sample; (i -= bytes_per_sample) >= 0; ) {
					sample_buffer[--op] =
						((int)ip[i] & 0xff) << (Integer.SIZE - 32) | ((int)ip[i + 1] & 0xff) << (Integer.SIZE - 24) | ((int)ip[i + 2] & 0xff) << (Integer.SIZE - 16) | ip[i + 3] << (Integer.SIZE - 8);
				}
			}
		} else {
			//GA_URS_IFLOOP(1)
			if( bytes_per_sample == 1 ) {
				for( int i = samples_read * bytes_per_sample; (i -= bytes_per_sample) >= 0; ) {
					sample_buffer[--op] = (ip[i] ^ 0x80) << (Integer.SIZE - 8) | 0x7f << (Integer.SIZE - 16 );
				}
			} /* convert from unsigned */
			//GA_URS_IFLOOP(2)
			if( bytes_per_sample == 2 ) {
				for( int i = samples_read * bytes_per_sample; (i -= bytes_per_sample) >= 0; ) {
					sample_buffer[--op] = ip[i] << (Integer.SIZE - 8) | ((int)ip[i + 1] & 0xff) << (Integer.SIZE - 16 );
				}
			}
			//GA_URS_IFLOOP(3)
			if( bytes_per_sample == 3 ) {
				for( int i = samples_read * bytes_per_sample; (i -= bytes_per_sample) >= 0; ) {
					sample_buffer[--op] = ip[i] << (Integer.SIZE - 8) | ((int)ip[i + 1] & 0xff) << (Integer.SIZE - 16) | ((int)ip[i + 2] & 0xff) << (Integer.SIZE - 24);
				}
			}
			//GA_URS_IFLOOP(4)
			if( bytes_per_sample == 4 ) {
				for( int i = samples_read * bytes_per_sample; (i -= bytes_per_sample) >= 0; ) {
					sample_buffer[--op] =
						ip[i] << (Integer.SIZE - 8) | ((int)ip[i + 1] & 0xff) << (Integer.SIZE - 16) | ((int)ip[i + 2] & 0xff) << (Integer.SIZE - 24) | ((int)ip[i + 3] & 0xff) << (Integer.SIZE - 32);
				}
			}
		}

		if( global.pcm_is_ieee_float ) {
			final float m_max = Integer.MAX_VALUE;
			final float m_min = -(float) Integer.MIN_VALUE;
			// final float *x = (float *) sample_buffer;
			for( int i = 0, k = 0; i < samples_to_read; ++i ) {
				int v = (int)ip[k++] & 0xff;
				v = ((int)ip[k++] & 0xff) << 8;
				v = ((int)ip[k++] & 0xff) << 16;
				v = ((int)ip[k++]) << 24;
				final float u = Float.intBitsToFloat( v );
				if( u >= 1f ) {
					v = Integer.MAX_VALUE;
				} else if( u <= -1f ) {
					v = Integer.MIN_VALUE;
				} else if( u >= 0f ) {
					v = (int) (u * m_max + 0.5f );
				} else {
					v = (int) (u * m_min - 0.5f );
				}
				sample_buffer[i] = v;
			}
		}
		return ( samples_read );
	}

	/************************************************************************
	*
	* read_samples()
	*
	* PURPOSE:  reads the PCM samples from a file to the buffer
	*
	*  SEMANTICS:
	* Reads #samples_read# number of shorts from #musicin# filepointer
	* into #sample_buffer[]#.  Returns the number of samples read.
	*
	************************************************************************/
	private static final int read_samples_pcm(final RandomAccessFile musicin, final int sample_buffer[]/*[2304]*/, final int samples_to_read) {
		final int bytes_per_sample = global.pcmbitwidth >> 3;
		boolean swap_byte_order; /* byte order of input stream */

		switch( global.pcmbitwidth ) {
		case 32:
		case 24:
		case 16:
			if( ! Jparse.global_raw_pcm.in_signed ) {
				if( Jparse.global_ui_config.silent < 10 ) {
					Jconsole.error_printf("Unsigned input only supported with bitwidth 8\n" );
				}
				return -1;
			}
			//swap_byte_order = (Jparse.global_raw_pcm.in_endian != ByteOrderLittleEndian) ? 1 : 0;
			swap_byte_order = Jparse.global_raw_pcm.is_big_endian;
			if( global.pcmswapbytes ) {
				swap_byte_order = !swap_byte_order;
			}
			break;

		case 8:
			swap_byte_order = global.pcm_is_unsigned_8bit;
			break;

		default:
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("Only 8, 16, 24 and 32 bit input files supported \n" );
			}
			return -1;
		}
		if( samples_to_read < 0 || samples_to_read > 2304 ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("Error: unexpected number of samples to read: %d\n", samples_to_read );
			}
			return -1;
		}
		try {
			final int samples_read = unpack_read_samples( samples_to_read, bytes_per_sample, swap_byte_order,
						sample_buffer, musicin );
			return samples_read;
		} catch(final IOException ie) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("Error reading input file\n" );
			}
			return -1;
		}
	}

	/* AIFF Definitions */

	private static final int IFF_ID_FORM = 0x464f524d; /* "FORM" */
	private static final int IFF_ID_AIFF = 0x41494646; /* "AIFF" */
	private static final int IFF_ID_AIFC = 0x41494643; /* "AIFC" */
	private static final int IFF_ID_COMM = 0x434f4d4d; /* "COMM" */
	private static final int IFF_ID_SSND = 0x53534e44; /* "SSND" */
	// private static final int IFF_ID_MPEG = 0x4d504547; /* "MPEG" */

	private static final int IFF_ID_NONE = 0x4e4f4e45; /* "NONE" *//* AIFF-C data format */
	private static final int IFF_ID_2CBE = 0x74776f73; /* "twos" *//* AIFF-C data format */
	private static final int IFF_ID_2CLE = 0x736f7774; /* "sowt" *//* AIFF-C data format */

	private static final int WAV_ID_RIFF = 0x52494646; /* "RIFF" */
	private static final int WAV_ID_WAVE = 0x57415645; /* "WAVE" */
	private static final int WAV_ID_FMT = 0x666d7420; /* "fmt " */
	private static final int WAV_ID_DATA = 0x64617461; /* "data" */

// #ifndef WAVE_FORMAT_PCM
	private static final short WAVE_FORMAT_PCM = 0x0001;
//#endif
// #ifndef WAVE_FORMAT_IEEE_FLOAT
	private static final short WAVE_FORMAT_IEEE_FLOAT = 0x0003;
// #endif
// #ifndef WAVE_FORMAT_EXTENSIBLE
	private static final short WAVE_FORMAT_EXTENSIBLE = (short)0xFFFE;
// #endif

	private static final int make_even_number_of_bytes_in_length(final int x) {
		if( (x & 0x01) != 0 ) {
			return x + 1;
		}
		return x;
	}

	private static final long make_even_number_of_bytes_in_length(final long x) {
		if( (x & 0x01) != 0 ) {
			return x + 1;
		}
		return x;
	}

/*****************************************************************************
 *
 *	Read Microsoft Wave headers
 *
 *	By the time we get here the first 32-bits of the file have already been
 *	read, and we're pretty sure that we're looking at a WAV file.
 *
 *****************************************************************************/

	@SuppressWarnings("boxing")
	private static final int parse_wave_header(final Jlame_global_flags gfp, final RandomAccessFile sf) {
		int     format_tag = 0;
		int     channels = 0;
		int     bits_per_sample = 0;
		int     samples_per_sec = 0;
		boolean is_wav = false;
		long     data_length = 0, subSize = 0;
		int     loop_sanity = 0;

		try {
			read_32_bits_high_low( sf ); /* file_length */
			if( read_32_bits_high_low( sf ) != WAV_ID_WAVE ) {
				return -1;
			}

			for( loop_sanity = 0; loop_sanity < 20; ++loop_sanity ) {
				final int type = read_32_bits_high_low( sf );

				if( type == WAV_ID_FMT ) {
					subSize = read_32_bits_low_high( sf );
					subSize = make_even_number_of_bytes_in_length( subSize );
					if( subSize < 16 ) {
						/*DEBUGF(
						"'fmt' chunk too short (only %ld bytes)!", subSize );  */
						return -1;
					}

					format_tag = read_16_bits_low_high( sf );
					subSize -= 2;
					channels = read_16_bits_low_high( sf );
					subSize -= 2;
					samples_per_sec = read_32_bits_low_high( sf );
					subSize -= 4;
					read_32_bits_low_high( sf );/* avg_bytes_per_sec */
					subSize -= 4;
					read_16_bits_low_high( sf );/* block_align */
					subSize -= 2;
					bits_per_sample = read_16_bits_low_high( sf );
					subSize -= 2;

					/* WAVE_FORMAT_EXTENSIBLE support */
					if( (subSize > 9) && (format_tag == WAVE_FORMAT_EXTENSIBLE) ) {
						read_16_bits_low_high( sf ); /* cbSize */
						read_16_bits_low_high( sf ); /* ValidBitsPerSample */
						read_32_bits_low_high( sf ); /* ChannelMask */
						/* SubType coincident with format_tag for PCM int or float */
						format_tag = read_16_bits_low_high( sf );
						subSize -= 10;
					}

					/* DEBUGF("   skipping %d bytes\n", subSize ); */

					if( subSize > 0 ) {
						if( fskip( sf, (long) subSize, SEEK_CUR) != 0 ) {
							return -1;
						}
					}

				} else if( type == WAV_ID_DATA ) {
					subSize = read_32_bits_low_high( sf );
					data_length = subSize;
					is_wav = true;
					/* We've found the audio data. Read no further! */
					break;

				} else {
					subSize = read_32_bits_low_high( sf );
					subSize = make_even_number_of_bytes_in_length( subSize );
					if( fskip( sf, (long) subSize, SEEK_CUR ) != 0 ) {
						return -1;
					}
				}
			}
			if( is_wav ) {
				if( format_tag == 0x0050 || format_tag == 0x0055 ) {
					return Jsound_file_format.sf_mp123;
				}
				if( format_tag != WAVE_FORMAT_PCM && format_tag != WAVE_FORMAT_IEEE_FLOAT ) {
					if( Jparse.global_ui_config.silent < 10 ) {
						Jconsole.error_printf("Unsupported data format: 0x%04X\n", format_tag );
					}
					return 0;   /* oh no! non-supported format  */
				}

				/* make sure the header is sane */
				if( gfp.lame_set_num_channels( channels ) ) {// FIXME if( ! ret ) is incorrect!
					return 0;
				}
				if( ! set_input_samplerate( gfp, samples_per_sec ) ) {
					return 0;
				}
				/* avoid division by zero */
				if( bits_per_sample < 1 ) {
					if( Jparse.global_ui_config.silent < 10 ) {
						Jconsole.error_printf("Unsupported bits per sample: %d\n", bits_per_sample );
					}
					return -1;
				}
				global.pcmbitwidth = bits_per_sample;
				global.pcm_is_unsigned_8bit = true;
				global.pcm_is_ieee_float = (format_tag == WAVE_FORMAT_IEEE_FLOAT);
				if( data_length == MAX_U_32_NUM ) {
					gfp.lame_set_num_samples( MAX_U_32_NUM );
				} else {
					gfp.lame_set_num_samples( data_length / (channels * ((bits_per_sample + 7) >> 3)) );
				}
				return 1;
			}
		} catch(final IOException ie) {
		}
		return -1;
	}

	private static final class JblockAlign {
		private int offset;
		private int blockSize;
	}

	private static final class JIFF_AIFF {
		private short  numChannels;
		private int    numSampleFrames;
		private short  sampleSize;
		private double sampleRate;
		private int    sampleType;
		private final JblockAlign blkAlgn = new JblockAlign();
	}

	/************************************************************************
	* aiff_check2
	*
	* PURPOSE:	Checks AIFF header information to make sure it is valid.
	*	        returns 0 on success, 1 on errors
	************************************************************************/

	private static final int aiff_check2(final JIFF_AIFF pcm_aiff_data) {
		if( pcm_aiff_data.sampleType != IFF_ID_SSND ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("ERROR: input sound data is not PCM\n" );
			}
			return 1;
		}
		switch( pcm_aiff_data.sampleSize ) {
		case 32:
		case 24:
		case 16:
		case 8:
			break;
		default:
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("ERROR: input sound data is not 8, 16, 24 or 32 bits\n" );
			}
			return 1;
		}
		if( pcm_aiff_data.numChannels != 1 && pcm_aiff_data.numChannels != 2 ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("ERROR: input sound data is not mono or stereo\n" );
			}
			return 1;
		}
		if( pcm_aiff_data.blkAlgn.blockSize != 0 ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("ERROR: block size of input sound data is not 0 bytes\n" );
			}
			return 1;
		}
		/* A bug, since we correctly skip the offset earlier in the code.
		   if( pcm_aiff_data.blkAlgn.offset != 0 ) {
		   error_printf("Block offset is not 0 bytes in '%s'\n", file_name );
		   return 1;
		   } */

		return 0;
	}


/*****************************************************************************
 *
 *	Read Audio Interchange File Format (AIFF) headers.
 *
 *	By the time we get here the first 32 bits of the file have already been
 *	read, and we're pretty sure that we're looking at an AIFF file.
 *
 *****************************************************************************/

	@SuppressWarnings("boxing")
	private static final int parse_aiff_header(final Jlame_global_flags gfp, final RandomAccessFile sf) {
		int   chunkSize = 0, subSize = 0, typeID = 0, dataType = IFF_ID_NONE;
		final JIFF_AIFF aiff_info = new JIFF_AIFF();// java: already zeroed
		int   seen_comm_chunk = 0, seen_ssnd_chunk = 0;
		long  pcm_data_pos = -1;

		try {
			chunkSize = read_32_bits_high_low( sf );

			typeID = read_32_bits_high_low( sf );
			if( (typeID != IFF_ID_AIFF) && (typeID != IFF_ID_AIFC)) {
				return -1;
			}

			while( chunkSize > 0 ) {
				final int type = read_32_bits_high_low( sf );
				chunkSize -= 4;

				/* DEBUGF(
				"found chunk type %08x '%4.4s'\n", type, (char*)&type ); */

				/* don't use a switch here to make it easier to use 'break' for SSND */
				if( type == IFF_ID_COMM ) {
					seen_comm_chunk = seen_ssnd_chunk + 1;
					subSize = read_32_bits_high_low( sf );
					int ckSize = make_even_number_of_bytes_in_length( subSize );
					chunkSize -= ckSize;

					aiff_info.numChannels = (short) read_16_bits_high_low( sf );
					ckSize -= 2;
					aiff_info.numSampleFrames = read_32_bits_high_low( sf );
					ckSize -= 4;
					aiff_info.sampleSize = (short) read_16_bits_high_low( sf );
					ckSize -= 2;
					aiff_info.sampleRate = read_ieee_extended_high_low( sf );
					ckSize -= 10;
					if( typeID == IFF_ID_AIFC ) {
						dataType = read_32_bits_high_low( sf );
						ckSize -= 4;
					}
					if( fskip( sf, ckSize, SEEK_CUR ) != 0 ) {
						return -1;
					}
				} else if( type == IFF_ID_SSND ) {
					seen_ssnd_chunk = 1;
					subSize = read_32_bits_high_low( sf );
					int ckSize = make_even_number_of_bytes_in_length( subSize );
					chunkSize -= ckSize;

					aiff_info.blkAlgn.offset = read_32_bits_high_low( sf );
					ckSize -= 4;
					aiff_info.blkAlgn.blockSize = read_32_bits_high_low( sf );
					ckSize -= 4;

					aiff_info.sampleType = IFF_ID_SSND;

					if( seen_comm_chunk > 0 ) {
						if( fskip( sf, aiff_info.blkAlgn.offset, SEEK_CUR ) != 0 ) {
							return -1;
						}
						/* We've found the audio data. Read no further! */
						break;
					}
					pcm_data_pos = sf.getFilePointer();
					if( pcm_data_pos >= 0 ) {
						pcm_data_pos += aiff_info.blkAlgn.offset;
					}
					if( fskip( sf, ckSize, SEEK_CUR ) != 0 ) {
						return -1;
					}
				} else {
					subSize = read_32_bits_high_low( sf );
					final int ckSize = make_even_number_of_bytes_in_length( subSize );
					chunkSize -= ckSize;

					if( fskip( sf, ckSize, SEEK_CUR ) != 0 ) {
						return -1;
					}
				}
			}
			if( dataType == IFF_ID_2CLE ) {
				global. pcmswapbytes = Jparse.global_reader.swapbytes;
			}
			else if( dataType == IFF_ID_2CBE ) {
				global. pcmswapbytes = ! Jparse.global_reader.swapbytes;
			}
			else if( dataType == IFF_ID_NONE ) {
				global. pcmswapbytes = ! Jparse.global_reader.swapbytes;
			}
			else {
				return -1;
			}

			/* DEBUGF("Parsed AIFF %d\n", is_aiff ); */
			if( seen_comm_chunk != 0 && (seen_ssnd_chunk > 0 || aiff_info.numSampleFrames == 0) ) {
				/* make sure the header is sane */
				if( 0 != aiff_check2( aiff_info ) ) {
					return 0;
				}
				if( ! set_input_num_channels( gfp, aiff_info.numChannels ) ) {
					return 0;
				}
				if( ! set_input_samplerate( gfp, (int) aiff_info.sampleRate ) ) {
					return 0;
				}
				gfp.lame_set_num_samples( aiff_info.numSampleFrames );
				global.pcmbitwidth = aiff_info.sampleSize;
				global.pcm_is_unsigned_8bit = false;
				global.pcm_is_ieee_float = false; /* FIXME: possible ??? */
				if( pcm_data_pos >= 0 ) {
					try {
						sf.seek( pcm_data_pos );
					} catch(final IOException ie) {
						if( Jparse.global_ui_config.silent < 10 ) {
							Jconsole.error_printf("Can't rewind stream to audio data position\n" );
						}
						return 0;
					}
				}

				return 1;
			}
		} catch(final IOException ie) {
		}
		return -1;
	}

/************************************************************************
*
* parse_file_header
*
* PURPOSE: Read the header from a bytestream.  Try to determine whether
*		   it's a WAV file or AIFF without rewinding, since rewind
*		   doesn't work on pipes and there's a good chance we're reading
*		   from stdin (otherwise we'd probably be using libsndfile).
*
* When this function returns, the file offset will be positioned at the
* beginning of the sound data.
*
************************************************************************/

	private static final int parse_file_header(final Jlame_global_flags gfp, final RandomAccessFile sf) {

		try {
			final int type = read_32_bits_high_low( sf );
			/*
			DEBUGF(
			"First word of input stream: %08x '%4.4s'\n", type, (char*) &type );
			*/
			global.count_samples_carefully = false;
			global.pcm_is_unsigned_8bit = ! Jparse.global_raw_pcm.in_signed;
			/*global_reader.input_format = sf_raw; commented out, because it is better to fail
			here as to encode some hundreds of input files not supported by LAME
			If you know you have RAW PCM data, use the -r switch
			*/

			if( type == WAV_ID_RIFF ) {
				/* It's probably a WAV file */
				final int ret = parse_wave_header( gfp, sf );
				if( ret == Jsound_file_format.sf_mp123 ) {
					global.count_samples_carefully = true;
					return Jsound_file_format.sf_mp123;
				}
				if( ret > 0 ) {
					if( gfp.lame_get_num_samples() == MAX_U_32_NUM || Jparse.global_reader.ignorewavlength )
					{
						global.count_samples_carefully = false;
						gfp.lame_set_num_samples( MAX_U_32_NUM );
					} else {
						global.count_samples_carefully = true;
					}
					return Jsound_file_format.sf_wave;
				}
				if( ret < 0 ) {
					if( Jparse.global_ui_config.silent < 10 ) {
						Jconsole.error_printf("Warning: corrupt or unsupported WAVE format\n" );
					}
				}
			} else if( type == IFF_ID_FORM ) {
				/* It's probably an AIFF file */
				final int ret = parse_aiff_header( gfp, sf );
				if( ret > 0 ) {
					global.count_samples_carefully = true;
					return Jsound_file_format.sf_aiff;
				}
				if( ret < 0 ) {
					if( Jparse.global_ui_config.silent < 10 ) {
						Jconsole.error_printf("Warning: corrupt or unsupported AIFF format\n" );
					}
				}
			} else {
				if( Jparse.global_ui_config.silent < 10 ) {
					Jconsole.error_printf("Warning: unsupported audio format\n" );
				}
			}
		} catch(final IOException ie) {
		}
		return Jsound_file_format.sf_unknown;
	}

	private static final boolean open_mpeg_file_part2(final Jlame_global_flags gfp, final RandomAccessFile musicin, final String inPath, final int[] enc_delay, final int[] enc_padding)
	{
// #ifdef HAVE_MPGLIB
		if( -1 == lame_decode_initfile( musicin, Jparse.global_decoder.mp3input_data, enc_delay, enc_padding ) ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("Error reading headers in mp3 input file %s.\n", inPath);
			}
			return false;
		}
//#endif
		if( ! set_input_num_channels( gfp, Jparse.global_decoder.mp3input_data.stereo ) ) {
			return false;
		}
		if( ! set_input_samplerate( gfp, Jparse.global_decoder.mp3input_data.samplerate ) ) {
			return false;
		}
		gfp.lame_set_num_samples( Jparse.global_decoder.mp3input_data.nsamp );
		return true;
	}

	private static final RandomAccessFile open_wave_file(final Jlame_global_flags gfp, final String inPath, final int[] enc_delay, final int[] enc_padding) {
		RandomAccessFile musicin;

		/* set the defaults from info incase we cannot determine them from file */
		gfp.lame_set_num_samples( MAX_U_32_NUM );

		if( 0 == inPath.compareTo("-") ) {//
			// lame_set_stream_binary_mode( musicin = System.in ); /* Read from standard input. */
			Jconsole.error_printf("java: input from stdin is not implemented\n");
			return null;
		} else {
			try {
				musicin = new RandomAccessFile( inPath, "r" );
			} catch(final FileNotFoundException fe) {
				if( Jparse.global_ui_config.silent < 10 ) {
					Jconsole.error_printf("Could not find \"%s\".\n", inPath );
				}
				return null;
			}
		}

		if( Jparse.global_reader.input_format == Jsound_file_format.sf_ogg ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("sorry, vorbis support in LAME is deprecated.\n" );
			}
			close_input_file( musicin );
			return null;
		}
		else if( Jparse.global_reader.input_format == Jsound_file_format.sf_raw ) {
			/* assume raw PCM */
			if( Jparse.global_ui_config.silent < 9 ) {
				Jconsole.console_printf("Assuming raw pcm input file" );
				if( Jparse.global_reader.swapbytes) {
					Jconsole.console_printf(" : Forcing byte-swapping\n" );
				} else {
					Jconsole.console_printf("\n" );
				}
			}
			global. pcmswapbytes = Jparse.global_reader.swapbytes;
		} else {
			Jparse.global_reader.input_format = parse_file_header( gfp, musicin );
		}
		if( Jparse.global_reader.input_format == Jsound_file_format.sf_mp123 ) {
			if( open_mpeg_file_part2( gfp, musicin, inPath, enc_delay, enc_padding ) ) {
				return musicin;
			}
			close_input_file(musicin);
			return null;
		}
		if( Jparse.global_reader.input_format == Jsound_file_format.sf_unknown ) {
			close_input_file( musicin );
			return null;
		}

		if( gfp.lame_get_num_samples() == MAX_U_32_NUM && musicin != (Object)System.in ) {
			try {
				final int tmp_num_channels = gfp.lame_get_num_channels();
				final double flen = (double)musicin.length(); /* try to figure out num_samples */
				if( flen >= 0 && tmp_num_channels > 0 ) {
					/* try file size, assume 2 bytes per sample */
					final long fsize = (long) (flen / (tmp_num_channels << 1));
					gfp.lame_set_num_samples( fsize );
					global.count_samples_carefully = false;
				}
			} catch(final IOException ie) {
			}
		}
		return musicin;
	}

	@SuppressWarnings("boxing")
	private static final RandomAccessFile open_mpeg_file(final Jlame_global_flags gfp, final String inPath, final int[] enc_delay, final int[] enc_padding) {
		RandomAccessFile musicin;

		/* set the defaults from info incase we cannot determine them from file */
		gfp.lame_set_num_samples( MAX_U_32_NUM );

		if( inPath.compareTo("-") == 0 ) {
			// musicin = stdin;
			// lame_set_stream_binary_mode( musicin ); /* Read from standard input. */
			Jconsole.error_printf("java: input from stdin is not implemented\n");
			return null;
		} else {
			try {
				musicin = new RandomAccessFile( inPath, "r" );
			} catch(final IOException ie) {
				if( Jparse.global_ui_config.silent < 10 ) {
					Jconsole.error_printf("Could not find \"%s\".\n", inPath );
				}
				return null;
			}
		}
		if( ! open_mpeg_file_part2( gfp, musicin, inPath, enc_delay, enc_padding ) ) {
			close_input_file( musicin );
			return null;
		}
		if( gfp.lame_get_num_samples() == MAX_U_32_NUM && musicin != (Object)System.in ) {
			try {
				final double flen = (double)musicin.length(); /* try to figure out num_samples */
				/* try file size, assume 2 bytes per sample */
				if( Jparse.global_decoder.mp3input_data.bitrate > 0 ) {
					final double totalseconds =
							(flen * 8.0 / (1000.0 * Jparse.global_decoder.mp3input_data.bitrate) );
					final long tmp_num_samples =
							(long) (totalseconds * gfp.lame_get_in_samplerate() );

					gfp.lame_set_num_samples( tmp_num_samples );
					Jparse.global_decoder.mp3input_data.nsamp = tmp_num_samples;
					global.count_samples_carefully = false;
				}
			} catch(final IOException ie) {
			}
		}
		return musicin;
	}

	private static final int close_input_file(final RandomAccessFile musicin ) {
		if( musicin != (Object)System.in && musicin != null  ) {
			try {
				musicin.close();
			} catch(final IOException ie) {
				if( Jparse.global_ui_config.silent < 10 ) {
					Jconsole.error_printf("Could not close audio input file\n" );
				}
				return -1;
			}
		}
		return 0;
	}

// #if defined(HAVE_MPGLIB)
	private static final boolean check_aid(final byte[] header) {
		return header[0] == 'A' && header[1] == 'i' && header[2] == 'D' && header[3] == '\1';
	}

/*
 * Please check this and don't kill me if there's a bug
 * This is a (nearly?) complete header analysis for a MPEG-1/2/2.5 Layer I, II or III
 * data stream
 */

	private static final boolean is_syncword_mp123(final byte[] header) {
		final byte abl2[/*16*/] = { 0, 7, 7, 7, 0, 7, 0, 0, 0, 0, 0, 8, 8, 8, 8, 8 };

		if( (header[0] & 0xFF) != 0xFF ) {
			return false;       /* first 8 bits must be '1' */
		}
		if( (header[1] & 0xE0) != 0xE0 ) {
			return false;       /* next 3 bits are also */
		}
		if( (header[1] & 0x18) == 0x08) {
			return false;       /* no MPEG-1, -2 or -2.5 */
		}
		switch( header[1] & 0x06 ) {
		default:
		case 0x00:         /* illegal Layer */
			return false;

		case 0x02:         /* Layer3 */
			if( Jparse.global_reader.input_format != Jsound_file_format.sf_mp3 && Jparse.global_reader.input_format != Jsound_file_format.sf_mp123 ) {
				return false;
			}
			Jparse.global_reader.input_format = Jsound_file_format.sf_mp3;
			break;

		case 0x04:         /* Layer2 */
			if( Jparse.global_reader.input_format != Jsound_file_format.sf_mp2 && Jparse.global_reader.input_format != Jsound_file_format.sf_mp123 ) {
				return false;
			}
			Jparse.global_reader.input_format = Jsound_file_format.sf_mp2;
			break;

		case 0x06:         /* Layer1 */
			if( Jparse.global_reader.input_format != Jsound_file_format.sf_mp1 && Jparse.global_reader.input_format != Jsound_file_format.sf_mp123 ) {
				return false;
			}
			Jparse.global_reader.input_format = Jsound_file_format.sf_mp1;
			break;
		}
		if( (header[1] & 0x06) == 0x00 ) {
			return false;       /* no Layer I, II and III */
		}
		if( (header[2] & 0xF0) == 0xF0 ) {
			return false;       /* bad bitrate */
		}
		if( (header[2] & 0x0C) == 0x0C ) {
			return false;       /* no sample frequency with (32,44.1,48)/(1,2,4)     */
		}
		if( (header[1] & 0x18) == 0x18 && (header[1] & 0x06) == 0x04 && ((abl2[((int)header[2] & 0xff) >> 4] & (1 << (((int)header[3] & 0xff) >> 6))) != 0) ) {
			return false;
		}
		if( (header[3] & 3) == 2 ) {
			return false;       /* reserved enphasis mode */
		}
		return true;
	}

	private static final int lenOfId3v2Tag(final byte[] buf, int offset) {
		final int b0 = (int)buf[offset++] & 127;
		final int b1 = (int)buf[offset++] & 127;
		final int b2 = (int)buf[offset++] & 127;
		final int b3 = (int)buf[offset  ] & 127;
		return (((((b0 << 7) + b1) << 7) + b2) << 7) + b3;
	}

	@SuppressWarnings("boxing")
	private static final int lame_decode_initfile(final RandomAccessFile fd, final Jmp3data_struct mp3data, final int[] enc_delay, final int[] enc_padding) {
		/*  VBRTAGDATA pTagData; */
		/* int xing_header,len2,num_frames; */
		final byte buf[] = new byte[100];
		final short pcm_l[] = new short[1152];
		final short pcm_r[] = new short[1152];
		boolean freeformat = false;

		// mp3data.clear();// TODO java check if need
		if( global.hip != null ) {
			global.hip.hip_decode_exit();
		}
		global.hip = Jhip.hip_decode_init();
		//Jmpglib_interface.hip_set_msgf( global.hip, Jparse.global_ui_config.silent < 10 ? Jconsole.frontend_msgf : null );
		global.hip.hip_set_msgf( Jparse.global_ui_config.silent < 10 ? Jconsole.Console_IO.Console_fp : null );
		//Jmpglib_interface.hip_set_errorf( global.hip, Jparse.global_ui_config.silent < 10 ? Jconsole.frontend_errorf : null );
		global.hip.hip_set_errorf( Jparse.global_ui_config.silent < 10 ? Jconsole.Console_IO.Error_fp : null );
		//Jmpglib_interface.hip_set_debugf( global.hip, Jconsole.frontend_debugf );
		global.hip.hip_set_debugf( Jconsole.Console_IO.Report_fp );

		try {
			int len = 4;
			if( fd.read( buf, 0, len ) != len ) {
				return -1;
			}      /* failed */
			while( buf[0] == 'I' && buf[1] == 'D' && buf[2] == '3' ) {
				len = 6;
				if( fd.read( buf, 4, len ) != len ) {
					return -1;
				}  /* failed */
				len = lenOfId3v2Tag( buf, 6 );
				if( global.in_id3v2_tag == null ) {// if( global.in_id3v2_size < 1 ) {
					// global.in_id3v2_size = 10 + len;
					global.in_id3v2_tag = new byte[ 10 + len ];
					//if( global.in_id3v2_tag != null ) {
						System.arraycopy( buf, 0, global.in_id3v2_tag, 0, 10 );
						if( fd.read( global.in_id3v2_tag, 10, len ) != len ) {
							return -1;
						}  /* failed */
						len = 0; /* copied, nothing to skip */
					//} else {
					//	global.in_id3v2_size = 0;
					//}
				}
				// assert( len <= LONG_MAX );
				fskip( fd, (long)len, SEEK_CUR );
				len = 4;
				if( fd.read( buf, 0, len ) != len ) {
					return -1;
				}  /* failed */
			}
			if( check_aid( buf ) ) {
				if( fd.read( buf, 0, 2 ) != 2 ) {
					return -1;
				}  /* failed */
				final int aid_header = ((int)buf[0] & 0xff) + (((int)buf[1] & 0xff) << 8);
				if( Jparse.global_ui_config.silent < 9 ) {
					Jconsole.console_printf("Album ID found.  length=%d \n", aid_header );
				}
				/* skip rest of AID, except for 6 bytes we have already read */
				fskip( fd, aid_header - 6, SEEK_CUR );

				/* read 4 more bytes to set up buffer for MP3 header check */
				if( fd.read( buf, 0, len ) != len ) {
					return -1;
				}  /* failed */
			}
			len = 3;// java
			while( ! is_syncword_mp123( buf ) ) {
				for( int i = 0; i < len; i++ ) {
					buf[i] = buf[i + 1];
				}
				if( fd.read( buf, len, 1 ) != 1 ) {
					return -1;
				}  /* failed */
			}
			len++;// java

			if( (buf[2] & 0xf0) == 0 ) {
				if( Jparse.global_ui_config.silent < 9 ) {
					Jconsole.console_printf("Input file is freeformat.\n" );
				}
				freeformat = true;
			}
			/* now parse the current buffer looking for MP3 headers.    */
			/* (as of 11/00: mpglib modified so that for the first frame where  */
			/* headers are parsed, no data will be decoded.   */
			/* However, for freeformat, we need to decode an entire frame, */
			/* so mp3data.bitrate will be 0 until we have decoded the first */
			/* frame.  Cannot decode first frame here because we are not */
			/* yet prepared to handle the output. */
			int ret = global.hip.hip_decode1_headersB( buf, len, pcm_l, pcm_r, 0, mp3data, enc_delay, enc_padding );
			if( -1 == ret ) {
				return -1;
			}

			/* repeat until we decode a valid mp3 header.  */
			while( ! mp3data.header_parsed ) {
				len = fd.read( buf );
				if( len != buf.length ) {
					return -1;
				}
				ret = global.hip.hip_decode1_headersB( buf, len, pcm_l, pcm_r, 0, mp3data, enc_delay, enc_padding );
				if( -1 == ret ) {
					return -1;
				}
			}

			if( mp3data.bitrate == 0 && ! freeformat ) {
				if( Jparse.global_ui_config.silent < 10 ) {
					Jconsole.error_printf("fail to sync...\n" );
				}
				return lame_decode_initfile( fd, mp3data, enc_delay, enc_padding );
			}

			if( mp3data.totalframes > 0 ) {
				/* mpglib found a Xing VBR header and computed nsamp & totalframes */
			} else {
				/* set as unknown.  Later, we will take a guess based on file size
				 * ant bitrate */
				mp3data.nsamp = MAX_U_32_NUM;
			}

			/*
			   report_printf("ret = %d NEED_MORE=%d \n",ret,MP3_NEED_MORE );
			   report_printf("stereo = %d \n",mp.fr.stereo );
			   report_printf("samp = %d  \n",freqs[mp.fr.sampling_frequency] );
			   report_printf("framesize = %d  \n",framesize );
			   report_printf("bitrate = %d  \n",mp3data.bitrate );
			   report_printf("num frames = %d  \n",num_frames );
			   report_printf("num samp = %d  \n",mp3data.nsamp );
			   report_printf("mode     = %d  \n",mp.fr.mode );
			 */

			return 0;
		} catch(final IOException ie) {
			return -1;
		}
	}

/*
For lame_decode_fromfile:  return code
  -1     error
   n     number of samples output.  either 576 or 1152 depending on MP3 file.


For lame_decode1_headers():  return code
  -1     error
   0     ok, but need more data before outputing any samples
   n     number of samples output.  either 576 or 1152 depending on MP3 file.
*/
	private static final int lame_decode_fromfile(final RandomAccessFile fd, final short pcm_l[], final short pcm_r[], final Jmp3data_struct mp3data) {
		int ret = 0;
		int len = 0;
		final byte buf[] = new byte[1024];

		/* first see if we still have data buffered in the decoder: */
		ret = global.hip.hip_decode1_headers( buf, len, pcm_l, pcm_r, 0, mp3data );
		if( ret != 0 ) {
			return ret;
		}

		/* read until we get a valid output frame */
		try {
			for( ;; ) {
				len = fd.read( buf, 0, 1024 );
				if( len <= 0 ) {// java: len = -1 if eof
					len = 0;// java: len = -1 if eof
					/* we are done reading the file, but check for buffered data */
					ret = global.hip.hip_decode1_headers( buf, len, pcm_l, pcm_r, 0, mp3data );
					if( ret <= 0 ) {
						return -1; /* done with file */
					}
					break;
				}

				ret = global.hip.hip_decode1_headers( buf, len, pcm_l, pcm_r, 0, mp3data );
				if( ret == -1 ) {
					return -1;
				}
				if( ret > 0 ) {
					break;
				}
			}
			return ret;
		} catch(final IOException ie) {
			return -1;
		}
	}
// #endif /* defined(HAVE_MPGLIB) */

	private static final int is_mpeg_file_format(final int input_file_format) {
		switch( input_file_format ) {
		case Jsound_file_format.sf_mp1:
			return 1;
		case Jsound_file_format.sf_mp2:
			return 2;
		case Jsound_file_format.sf_mp3:
			return 3;
		case Jsound_file_format.sf_mp123:
			return -1;
		default:
			break;
		}
		return 0;
	}

	//#define LOW__BYTE(x) (x & 0x00ff)
	//#define HIGH_BYTE(x) ((x >> 8) & 0x00ff)

	static final void put_audio16(final RandomAccessFile outf, final short Buffer[][]/*[2][1152]*/, final int iread, final int nch) {
		final byte data[] = new byte[2 * 1152 * 2];
		int m = 0;

		if( Jparse.global_decoder.disable_wav_header && Jparse.global_reader.swapbytes ) {
			if( nch == 1 ) {
				final short[] buff0 = Buffer[0];// java
				for( int i = 0; i < iread; i++ ) {
					final short x = buff0[i];
					/* write 16 Bits High Low */
					data[m++] = (byte)(x >> 8);
					data[m++] = (byte)(x);
				}
			} else {
				final short[] buff0 = Buffer[0];// java
				final short[] buff1 = Buffer[1];// java
				for( int i = 0; i < iread; i++ ) {
					final short x = buff0[i], y = buff1[i];
					/* write 16 Bits High Low */
					data[m++] = (byte)(x >> 8);
					data[m++] = (byte)(x);
					/* write 16 Bits High Low */
					data[m++] = (byte)(y >> 8);
					data[m++] = (byte)(y);
				}
			}
		} else {
			if( nch == 1 ) {
				final short[] buff0 = Buffer[0];// java
				for( int i = 0; i < iread; i++ ) {
					final short x = buff0[i];
					/* write 16 Bits Low High */
					data[m++] = (byte)(x);
					data[m++] = (byte)(x >> 8);
				}
			} else {
				final short[] buff0 = Buffer[0];// java
				final short[] buff1 = Buffer[1];// java
				for( int i = 0; i < iread; i++ ) {
					final short x = buff0[i], y = buff1[i];
					/* write 16 Bits Low High */
					data[m++] = (byte)(x);
					data[m++] = (byte)(x >> 8);
					/* write 16 Bits Low High */
					data[m++] = (byte)(y);
					data[m++] = (byte)(y >> 8);
				}
			}
		}
		if( m > 0 ) {
			try {// FIXME result don't checked
				outf.write( data, 0, m );
			} catch(final IOException ie) {
			}
		}
		/* if( Jparse.global_writer.flush_write ) {
			outf.flush();
		} */
	}
/*
	private static final Jhip get_hip() {
		return global.hip;
	}
*/
	static final int sizeOfOldTag(final Jlame_global_flags gf) {
		if( global.in_id3v2_tag != null ) {
			return global.in_id3v2_tag.length;// global.in_id3v2_size;
		}
		return 0;
	}

	static final byte[] getOldTag(final Jlame_global_flags gf) {
		return global.in_id3v2_tag;
	}
}