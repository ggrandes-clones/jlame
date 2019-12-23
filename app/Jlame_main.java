package app;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.Thread.UncaughtExceptionHandler;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import gui.GUI;
import gui.Localization;
import libmp3lame.JVbrTag;
import libmp3lame.Jid3tag;
import libmp3lame.Jlame;
import libmp3lame.Jlame_global_flags;

/*
 *      Command line frontend program
 *
 *      Copyright (c) 1999 Mark Taylor
 *                    2000 Takehiro TOMINAGA
 *                    2010-2011 Robert Hegemann
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/* $Id: lame_main.c,v 1.18 2017/08/31 14:14:46 robert Exp $ */

// lame_main.c

/************************************************************************
*
* main
*
* PURPOSE:  MPEG-1,2 Layer III encoder with GPSYCHO
* psychoacoustic model.
*
************************************************************************/
public final class Jlame_main {
	private static final String CLASS_NAME = "Jlame";

	/** maximum size of albumart image (128KB), which affects LAME_MAXMP3BUFFER
	   as well since lame_encode_buffer() also returns ID3v2 tag data */
	private static final int LAME_MAXALBUMART  = (128 * 1024);

	/** maximum size of mp3buffer needed if you encode at most 1152 samples for
	   each call to lame_encode_buffer.  see lame_encode_buffer() below
	   (LAME_MAXMP3BUFFER is now obsolete)  */
	private static final int LAME_MAXMP3BUFFER = (16384 + LAME_MAXALBUMART);

	private static final RandomAccessFile init_files(final Jlame_global_flags gf, final String inPath, final String outPath) {
		/* Mostly it is not useful to use the same input and output name.
		   This test is very easy and buggy and don't recognize different names
		   assigning the same file
		 */
		if( 0 != "-".compareTo( outPath ) && 0 == inPath.compareTo( outPath ) ) {
			System.err.printf("Input file and Output file are the same. Abort.\n");
			return null;
		}

		/* open the wav/aiff/raw pcm or mp3 input file.  This call will
		 * open the file, try to parse the headers and
		 * set gf.samplerate, gf.num_channels, gf.num_samples.
		 * if you want to do your own file input, skip this call and set
		 * samplerate, num_channels and num_samples yourself.
		 */
		if( Jget_audio.init_infile( gf, inPath ) < 0 ) {
			System.err.printf("Can't init infile '%s'\n", inPath);
			return null;
		}
		RandomAccessFile outf = null;
		if( (outf = Jget_audio.init_outfile( outPath, gf.lame_get_decode_only() )) == null ) {
			System.err.printf("Can't init outfile '%s'\n", outPath);
			return null;
		}

		return outf;
	}


	@SuppressWarnings("boxing")
	private static final void printInputFormat(final Jlame_global_flags gfp/*, final AudioFormat format*/) {
		//Jset_get.lame_get_version( gfp );
		//Jset_get.lame_get_out_samplerate( gfp );
		//System.out.print( format.getEncoding() );
		final int v_main = 2 - gfp.lame_get_version();
		final String v_ex = gfp.lame_get_out_samplerate() < 16000 ? ".5" : "";
		switch( Jparse.global_reader.input_format ) {
		case Jsound_file_format.sf_mp123:     /* FIXME: !!! */
			break;
		case Jsound_file_format.sf_mp3:
			Jconsole.console_printf("MPEG-%d%s Layer %s", v_main, v_ex, "III");
			break;
		case Jsound_file_format.sf_mp2:
			Jconsole.console_printf("MPEG-%d%s Layer %s", v_main, v_ex, "II");
			break;
		case Jsound_file_format.sf_mp1:
			Jconsole.console_printf("MPEG-%d%s Layer %s", v_main, v_ex, "I");
			break;
		case Jsound_file_format.sf_raw:
			Jconsole.console_printf("raw PCM data");
			break;
		case Jsound_file_format.sf_wave:
			Jconsole.console_printf("Microsoft WAVE");
			break;
		case Jsound_file_format.sf_aiff:
			Jconsole.console_printf("SGI/Apple AIFF");
			break;
		default:
			Jconsole.console_printf("unknown");
			break;
		}
	}

	/* the simple lame decoder */
	/* After calling lame_init(), lame_init_params() and
	 * init_infile(), call this routine to read the input MP3 file
	 * and output .wav data to the specified file pointer*/
	/* lame_decoder will ignore the first 528 samples, since these samples
	 * represent the mpglib delay (and are all 0).  skip = number of additional
	 * samples to skip, to (for example) compensate for the encoder delay */

	@SuppressWarnings("boxing")
	private static int lame_decoder_loop(final Jlame_global_flags gfp, final RandomAccessFile outf, final String inPath, final String outPath) {
		final short Buffer[][] = new short[2][1152];
		final int tmp_num_channels = gfp.lame_get_num_channels();
		final int skip_start = Jget_audio.samples_to_skip_at_start();
		final int skip_end = Jget_audio.samples_to_skip_at_end();
		JDecoderProgress dp = null;

		if( !(tmp_num_channels >= 1 && tmp_num_channels <= 2) ) {
			Jconsole.error_printf("Internal error.  Aborting.");
			return -1;
		}

		if( Jparse.global_ui_config.silent < 9 ) {
			Jconsole.console_printf("\rinput:  %s%s(%.3g kHz, %d channel%s, ",
							! inPath.equals("-") ? inPath : "<stdin>",
							inPath.length() > 26 ? "\n\t" : "  ",
							gfp.lame_get_in_samplerate() / 1.e3,
							tmp_num_channels, tmp_num_channels != 1 ? "s" : "");

			printInputFormat( gfp );

			Jconsole.console_printf(")\noutput: %s%s(16 bit, Microsoft WAVE)\n",
							! outPath.equals("-") ? outPath : "<stdout>",
							outPath.length() > 45 ? "\n\t" : "  ");

			if( skip_start > 0 ) {
				Jconsole.console_printf("skipping initial %d samples (encoder+decoder delay)\n", skip_start );
			}
			if( skip_end > 0 ) {
				Jconsole.console_printf("skipping final %d samples (encoder padding-decoder delay)\n", skip_end );
			}

		switch( Jparse.global_reader.input_format ) {
		case Jsound_file_format.sf_mp3:
		case Jsound_file_format.sf_mp2:
		case Jsound_file_format.sf_mp1:
			dp = Jtimestatus.decoder_progress_init( gfp.lame_get_num_samples(),
					Jparse.global_decoder.mp3input_data.framesize );
			break;
		case Jsound_file_format.sf_raw:
		case Jsound_file_format.sf_wave:
		case Jsound_file_format.sf_aiff:
		default:
				dp = Jtimestatus.decoder_progress_init( gfp.lame_get_num_samples(),
						gfp.lame_get_in_samplerate() < 32000 ? 576 : 1152 );
			break;
		}
		}

		if( ! Jparse.global_decoder.disable_wav_header ) {
			Jget_audio.WriteWaveHeader( outf, 0x7FFFFFFF, gfp.lame_get_in_samplerate(), tmp_num_channels, 16 );
		}
		/* unknown size, so write maximum 32 bit signed value */

		long wavsize = 0;// FIXME why double?
		int iread;
		do {
			iread = Jget_audio.get_audio16( gfp, Buffer ); /* read in 'iread' samples */
			if( iread >= 0 ) {
				wavsize += iread;
				if( dp != null ) {
					Jtimestatus.decoder_progress( dp, Jparse.global_decoder.mp3input_data, iread );
				}
				Jget_audio.put_audio16( outf, Buffer, iread, tmp_num_channels );
			}
		} while( iread > 0 );

		final int i = (16 / 8) * tmp_num_channels;

		if( wavsize <= 0 ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("WAVE file contains 0 PCM samples\n");
			}
			wavsize = 0;
		} else if( wavsize > 0xFFFFFFD0L / i ) {
			if( Jparse.global_ui_config.silent < 10 ) {
				Jconsole.error_printf("Very huge WAVE file, can't set filesize accordingly\n");
			}
			wavsize = 0xFFFFFFD0L;
		} else {
			wavsize *= i;
		}
		/* if outf is seekable, rewind and adjust length */
		if( ! Jparse.global_decoder.disable_wav_header && ! "-".equals( outPath ) ) {
			try {
				outf.seek( 0 );
				Jget_audio.WriteWaveHeader( outf, (int) wavsize, gfp.lame_get_in_samplerate(), tmp_num_channels, 16 );
			} catch(final IOException ie) {
			}
		}
		try { outf.close();
		} catch( final IOException e ) {}
		Jget_audio.close_infile();

		if( dp != null ) {
			Jtimestatus.decoder_progress_finish( dp );
		}
		return 0;
	}

	private static final int lame_decoder(final Jlame_global_flags gfp, final RandomAccessFile outf, final String inPath, final String outPath)
	{
		final int ret = lame_decoder_loop( gfp, outf, inPath, outPath );
		try { outf.close();/* close the output file */
		} catch( final IOException e ) {}
		Jget_audio.close_infile();     /* close the input file */
		return ret;
	}

	@SuppressWarnings("boxing")
	private static final void print_trailing_info(final Jlame_global_flags gf) {
		if( gf.lame_get_findReplayGain() ) {
			final int RadioGain = gf.lame_get_RadioGain();
			System.out.printf("ReplayGain: %s%.1fdB\n", RadioGain > 0 ? "+" : "",
						((float) RadioGain) / 10.0);
			if( RadioGain > 0x1FE || RadioGain < -0x1FE ) {
				System.err.printf("WARNING: ReplayGain exceeds the -51dB to +51dB range. Such a result is too\n" +
								"         high to be stored in the header.\n");
			}
		}

		/* if( the user requested printing info about clipping) and (decoding
		   on the fly has actually been performed) */
		if( Jparse.global_ui_config.print_clipping_info && gf.lame_get_decode_on_the_fly() ) {
			final float noclipGainChange = (float) gf.lame_get_noclipGainChange() / 10.0f;
			final float noclipScale = gf.lame_get_noclipScale();

			if( noclipGainChange > 0.0 ) { /* clipping occurs */
				System.out.printf("WARNING: clipping occurs at the current gain. Set your decoder to decrease\n" +
									"         the  gain  by  at least %.1fdB or encode again ", noclipGainChange );

				/* advice the user on the scale factor */
				if( noclipScale > 0 ) {
					System.out.printf("using  --scale %.2f\n", noclipScale * gf.lame_get_scale() );
					System.out.printf("         or less (the value under --scale is approximate).\n");
				} else {
					/* the user specified his own scale factor. We could suggest
					 * the scale factor of (32767.0/gfp->PeakSample)*(gfp->scale)
					 * but it's usually very inaccurate. So we'd rather advice him to
					 * disable scaling first and see our suggestion on the scale factor then. */
					System.out.printf("using --scale <arg>\n" +
								   "         (For   a   suggestion  on  the  optimal  value  of  <arg>  encode\n" +
								   "         with  --scale 1  first)\n");
				}

			} else {          /* no clipping */
				if( noclipGainChange > -0.1 ) {
					System.out.printf("\nThe waveform does not clip and is less than 0.1dB away from full scale.\n");
				} else {
					System.out.printf("\nThe waveform does not clip and is at least %.1fdB away from full scale.\n",
									-noclipGainChange );
				}
			}
		}
	}

	@SuppressWarnings("boxing")
	private static final int write_xing_frame(final Jlame_global_flags gf, final RandomAccessFile outf, final int offset) {
		final byte mp3buffer[] = new byte[LAME_MAXMP3BUFFER];

		final int imp3 = JVbrTag.lame_get_lametag_frame( gf, mp3buffer, mp3buffer.length );
		if( imp3 <= 0 ) {
			return 0;       /* nothing to do */
		}
		if( Jparse.global_ui_config.silent <= 0 ) {
			System.out.printf("Writing LAME Tag...");
		}
		if( imp3 > mp3buffer.length ) {
			System.err.printf("Error writing LAME-tag frame: buffer too small: buffer size=%d  frame size=%d\n",
					mp3buffer.length, imp3 );
			return -1;
		}
		// assert( offset <= LONG_MAX );
		try {
			outf.seek( offset );
		} catch(final IOException ie) {
			System.err.printf("fatal error: can't update LAME-tag frame!\n");
			return -1;
		}
		try {
			outf.write( mp3buffer, 0, imp3 );
		} catch(final IOException ie) {
			System.err.printf("Error writing LAME-tag \n");
			return -1;
		}
		if( Jparse.global_ui_config.silent <= 0 ) {
			System.out.printf("done\n");
		}
		// assert( imp3 <= INT_MAX );
		return imp3;
	}

	@SuppressWarnings("boxing")
	private static final int write_id3v1_tag(final Jlame_global_flags gf, final RandomAccessFile outf) {
		final byte mp3buffer[] = new byte[128];

		final int imp3 = Jid3tag.lame_get_id3v1_tag( gf, mp3buffer, mp3buffer.length );
		if( imp3 <= 0 ) {
			return 0;
		}
		if( imp3 > mp3buffer.length ) {
			System.err.printf("Error writing ID3v1 tag: buffer too small: buffer size=%d  ID3v1 size=%d\n",
							mp3buffer.length, imp3 );
			return 0;       /* not critical */
		}
		try {
			outf.write( mp3buffer, 0, imp3 );
		} catch(final IOException ie) {
			System.err.printf("Error writing ID3v1 tag \n");
			return 1;
		}
		return 0;
	}

	@SuppressWarnings("boxing")
	private static final int lame_encoder_loop(final Jlame_global_flags gf, final RandomAccessFile outf, final boolean nogap, final String inPath, final String outPath) {
		final byte mp3buffer[] = new byte[LAME_MAXMP3BUFFER];
		final int Buffer[][] = new int[2][1152];

		Jtimestatus.encoder_progress_begin( gf, inPath, outPath );

		int id3v2_size = Jid3tag.lame_get_id3v2_tag( gf, null, 0 );
		if( id3v2_size > 0 ) {// FIXME buffer null, so id3v2_size always 0!
			final byte[] id3v2tag = new byte[ id3v2_size ];
			final int n_bytes = Jid3tag.lame_get_id3v2_tag( gf, id3v2tag, id3v2_size );
			try {
				outf.write( id3v2tag, 0, n_bytes );
			} catch(final IOException ie) {
				Jtimestatus.encoder_progress_end( gf );
				System.err.printf("Error writing ID3v2 tag \n");
				return 1;
			}
		} else {
			final byte[] id3v2tag = Jget_audio.getOldTag( gf );
			id3v2_size = Jget_audio.sizeOfOldTag( gf );
			if( id3v2_size > 0 ) {
				try {
					outf.write( id3v2tag, 0, id3v2_size );
				} catch(final IOException ie) {
					Jtimestatus.encoder_progress_end( gf );
					System.err.printf("Error writing ID3v2 tag \n");
					return 1;
				}
			}
		}
		/* if( Jparse.global_writer.flush_write ) {
			outf.flush();// java: not supported
		} */

	    /* do not feed more than in_limit PCM samples in one encode call
	       otherwise the mp3buffer is likely too small
	     */
		int in_limit = gf.lame_get_maximum_number_of_samples( mp3buffer.length );
		if( in_limit < 1 ) {
			in_limit = 1;
		}

		/* encode until we hit eof */
		int iread;
		do {
			/* read in 'iread' samples */
			iread = Jget_audio.get_audio( gf, Buffer );

			if( iread >= 0 ) {
				// const int* buffer_l = Buffer[0];
				// const int* buffer_r = Buffer[1];
				int buffer_lr = 0;// java Buffer[0][buffer_lr], Buffer[1][buffer_lr]
				int     rest = iread;
				do {
					final int chunk = rest < in_limit ? rest : in_limit;
					Jtimestatus.encoder_progress( gf );

					/* encode */
					final int imp3 = Jlame.lame_encode_buffer_int( gf, Buffer[0], Buffer[1], buffer_lr, chunk, mp3buffer, 0, mp3buffer.length );
					buffer_lr += chunk;
					rest -= chunk;

					/* was our output buffer big enough? */
					if( imp3 < 0 ) {
						if( imp3 == -1 ) {
							System.err.printf("mp3 buffer is not big enough... \n");
						} else {
							System.err.printf("mp3 internal error:  error code=%d\n", imp3 );
						}
						return 1;
					}
					try {
						outf.write( mp3buffer, 0, imp3 );
					} catch(final IOException ie) {
						System.err.printf("Error writing mp3 output \n");
						return 1;
					}
				} while( rest > 0 );
			}
			/*if( Jparse.global_writer.flush_write ) {
				outf.flush();
			}*/
		} while( iread > 0 );

		int imp3 = nogap ?/* may return one more mp3 frame */
				Jlame.lame_encode_flush_nogap( gf, mp3buffer, mp3buffer.length )
				:
				Jlame.lame_encode_flush( gf, mp3buffer, mp3buffer.length );

		if( imp3 < 0 ) {
			if( imp3 == -1 ) {
				System.err.printf("mp3 buffer is not big enough... \n");
			} else {
				System.err.printf("mp3 internal error:  error code=%d\n", imp3);
			}
			return 1;
		}

		Jtimestatus.encoder_progress_end( gf );

		try {
			outf.write( mp3buffer, 0, imp3 );
		} catch(final IOException ie) {
			System.err.printf("Error writing mp3 output \n");
			return 1;
		}
		/*if( Jparse.global_writer.flush_write ) {
			outf.flush();
		}*/
		imp3 = write_id3v1_tag( gf, outf );
		/*if( Jparse.global_writer.flush_write ) {
			outf.flush();
		}*/
		if( imp3 != 0 ) {
			return 1;
		}
		write_xing_frame( gf, outf, id3v2_size );
		/*if( Jparse.global_writer.flush_write ) {
			outf.flush();
		}*/
		if( Jparse.global_ui_config.silent <= 0 ) {
			print_trailing_info( gf );
		}
		return 0;
	}


	private static final int lame_encoder(final Jlame_global_flags gf, final RandomAccessFile outf, final boolean nogap, final String inPath, final String outPath) {
		final int ret = lame_encoder_loop( gf, outf, nogap, inPath, outPath );
		try { outf.close();/* close the output file */
		} catch( final IOException e ) {}
		Jget_audio.close_infile();     /* close the input file */
		return ret;
	}


	private static final int MAX_NOGAP = 200;

	public static final int lame_main(final Jlame_global_flags gf, final String[] args) {
		gf.lame_set_msgf( System.out );
		gf.lame_set_errorf( System.err );
		gf.lame_set_debugf( System.out );
		if( args.length <= 1 - 1 ) {// java: -1, no path
			Jparse.usage( System.err, CLASS_NAME ); /* no command-line args, print usage, exit  */
			return 1;
		}

		final StringBuilder inPath = new StringBuilder();
		final StringBuilder outPath = new StringBuilder();
		String nogapdir = "";
		/* support for "nogap" encoding of up to 200 .wav files */
		boolean nogapout = false;
		final int max_nogap[] = { MAX_NOGAP };
		final String nogap_inPath[] = new String[MAX_NOGAP];
		final StringBuilder nogap_outPath[] = new StringBuilder[MAX_NOGAP];

		RandomAccessFile outf = null;
		/* parse the command line arguments, setting various flags in the
		 * struct 'gf'.  If you want to parse your own arguments,
		 * or call libmp3lame from a program which uses a GUI to set arguments,
		 * skip this call and set the values of interest in the gf struct.
		 * (see the file API and lame.h for documentation about these parameters)
		 */
		int ret = Jparse.parse_args( gf, CLASS_NAME, args, inPath, outPath, nogap_inPath, max_nogap );
		if( ret < 0 ) {
			return ( ret == -2 ? 0 : 1 );
		}
		if( Jparse.global_ui_config.update_interval < 0.) {
			Jparse.global_ui_config.update_interval = 2.f;
		}

		if( outPath.length() != 0 && max_nogap[0] > 0 ) {
			nogapdir = outPath.toString();
			nogapout = true;
		}

		/* initialize input file.  This also sets samplerate and as much
		   other data on the input file as available in the headers */
		if( max_nogap[0] > 0 ) {
			/* for nogap encoding of multiple input files, it is not possible to
			 * specify the output file name, only an optional output directory. */
			for( int i = 0; i < max_nogap[0]; ++i ) {
				String outdir = nogapout ? nogapdir : "";
				nogap_outPath[i] = new StringBuilder();

				/* if( Jparse.generateOutPath( nogap_inPath[i], outdir, ".mp3", nogap_outPath[i] ) ) {
					Jconsole.error_printf("processing nogap file %d: %s\n", i + 1, nogap_inPath[i] );
					return -1;
				} */ // java changed

				final int j = nogap_inPath[i].lastIndexOf(".");
				if( j >= 0 ) {
					nogap_outPath[i].setLength( 0 );
					if( outdir.endsWith("\\") || outdir.endsWith("/") ) {
						outdir = outdir.substring( 0, outdir.length() - 1 );
					}
					outPath.append( outdir ).append( File.pathSeparatorChar ).append( nogap_inPath[i].substring( 0, j ) ).append( ".mp3" );
				}
			}
			outf = init_files( gf, nogap_inPath[0], nogap_outPath[0].toString() );
		} else {
			outf = init_files( gf, inPath.toString(), outPath.toString() );
		}
		if( outf == null ) {
			Jget_audio.close_infile();
			return -1;
		}
		/* turn off automatic writing of ID3 tag data into mp3 stream
		 * we have to call it before 'lame_init_params', because that
		 * function would spit out ID3v2 tag data.
		 */
		gf.lame_set_write_id3tag_automatic( false );

		/* Now that all the options are set, lame needs to analyze them and
		 * set some more internal options and check for problems
		 */
		ret = Jlame.lame_init_params( gf );
		if( ret < 0 ) {
			if( ret == -1 ) {
				Jparse.display_bitrates( System.err );
			}
			System.err.printf("fatal error during initialization\n");
			try { outf.close(); } catch( final IOException e ) { }
			Jget_audio.close_infile();
			return ret;
		}

		if( Jparse.global_ui_config.silent > 0 ) {
			Jparse.global_ui_config.brhist = false; /* turn off VBR histogram */
		}

		if( gf.lame_get_decode_only() ) {
			/* decode an mp3 file to a .wav */
			ret = lame_decoder( gf, outf, inPath.toString(), outPath.toString() );
		} else if( max_nogap[0] == 0 ) {
			/* encode a single input file */
			ret = lame_encoder( gf, outf, false, inPath.toString(), outPath.toString() );
		} else {
			/* encode multiple input files using nogap option */
			for( int i = 0; i < max_nogap[0]; ++i ) {
				final boolean use_flush_nogap = (i != (max_nogap[0] - 1));
				if( i > 0 ) {
					/* note: if init_files changes anything, like
					   samplerate, num_channels, etc, we are screwed */
					outf = init_files( gf, nogap_inPath[i], nogap_outPath[i].toString() );
					if( outf == null ) {
						Jget_audio.close_infile();
						return -1;
					}
					/* reinitialize bitstream for next encoding.  this is normally done
					 * by lame_init_params(), but we cannot call that routine twice */
					Jlame.lame_init_bitstream( gf );
				}
				gf.lame_set_nogap_total( max_nogap[0] );
				gf.lame_set_nogap_currentindex( i );
				ret = lame_encoder( gf, outf, use_flush_nogap, nogap_inPath[i], nogap_outPath[i].toString() );
			}
		}
		return ret;
	}

	/**
	 * Exception text view
	 * @param t the exception
	 */
	public static final void log(final Throwable t) {
		System.err.println( t.getMessage() );
		t.printStackTrace();
		//
		final String newline = "\n";
		final JTextArea message = new JTextArea( t.toString() );
		message.append( newline );
		message.setFont( new Font( "Dialog", Font.PLAIN, 12 ) );
		final StackTraceElement[] stack = t.getStackTrace();
		for( final StackTraceElement s : stack ) {
			message.append("    ");
			message.append( s.toString() );
			message.append( newline );
		}
		final JScrollPane scroll = new JScrollPane( message );
		scroll.setPreferredSize( new Dimension( 640, 320 ) );
		final JPanel p = new JPanel( new BorderLayout() );
		p.add( new JLabel( Localization.get("MsgError"), JLabel.CENTER ), BorderLayout.PAGE_START );
		p.add( scroll, BorderLayout.CENTER );
		if( JOptionPane.NO_OPTION ==
			JOptionPane.showConfirmDialog( null, p, Localization.get("MsgErrorTitle"), JOptionPane.ERROR_MESSAGE, JOptionPane.YES_NO_OPTION ) ) {
			System.exit( 1 );
		}
	}
	public static final void main(final String[] args) {
		if( args.length != 0 || GraphicsEnvironment.isHeadless() ) {
			Jconsole.frontend_open_console();
			final Jlame_global_flags gf = Jlame.lame_init();
			final int ret = lame_main( gf, args );
			Jlame.lame_close( gf );
			Jconsole.frontend_close_console();
			System.exit( ret );
			return;
		}
		SwingUtilities.invokeLater( new Runnable() {
				@Override
				public final void run() {
					Thread.currentThread().setName("Main");
					Localization.setLanguage( "en"/*pp_settings.getString("Language", "ru")*/ );
					Thread.setDefaultUncaughtExceptionHandler( new UncaughtExceptionHandler() {
						@Override
						public void uncaughtException(final Thread t, final Throwable e) {
							log( e );
						}
					} );
					try { UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
					} catch (final ClassNotFoundException e1) {
					} catch (final InstantiationException e1) {
					} catch (final IllegalAccessException e1) {
					} catch (final UnsupportedLookAndFeelException e1) {
					}
					new GUI();
				}
			}
		);

		return;
	}
}