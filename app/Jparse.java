package app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import libmp3lame.Jid3tag;
import libmp3lame.Jid3tag_handler;
import libmp3lame.Jlame;
import libmp3lame.Jlame_global_flags;
import libmp3lame.Jlame_version;
import libmp3lame.Jtables;

/*
 *      Command line parsing related functions
 *
 *      Copyright (c) 1999 Mark Taylor
 *                    2000-2012 Robert Hegemann
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

/* $Id: parse.c,v 1.307 2017/09/26 12:25:07 robert Exp $ */

// parse.c

final class Jparse implements Jid3tag_handler {

	//static int const lame_alpha_version_enabled = LAME_ALPHA_VERSION;
	private static final boolean internal_opts_enabled = false;

	/* GLOBAL VARIABLES.  set by parse_args() */
	/* we need to clean this up */

	static final JReaderConfig global_reader = new JReaderConfig( Jsound_file_format.sf_unknown, false, false, 0, false );
	static final JWriterConfig global_writer = new JWriterConfig();// { 0 };

	static final JUiConfig global_ui_config = new JUiConfig();// = {0,0,0,0};

	static final JDecoderConfig global_decoder = new JDecoderConfig();

	static final JRawPCMConfig global_raw_pcm = new JRawPCMConfig(/* in_bitwidth */ 16, /* in_signed   */ true, /* in_endian   */ false /*ByteOrderLittleEndian*/ );

	/* possible text encodings */
	//typedef enum TextEncoding
	/** bytes will be stored as-is into ID3 tags, which are Latin1 per definition */
	// private static final int TENC_RAW = 0;
	/** text will be converted from local encoding to Latin1, as ID3 needs it */
	private static final int TENC_LATIN1 = 1;
	/** text will be converted from local encoding to Unicode, as ID3v2 wants it */
	private static final int TENC_UTF16 = 2;
	//} TextEncoding;
/*
	char* toLatin1(final char const* s)
	{
	return utf8ToLatin1(s);
	}

	unsigned short* toUtf16(char const* s)
	{
	return utf8ToUtf16(s);
	}
*/

	private static final int set_id3v2tag(final Jlame_global_flags gfp, final int type, final String str) {
		switch( type ) {
		case 'a': return Jid3tag.id3tag_set_textinfo( gfp, "TPE1", str, true );
		case 't': return Jid3tag.id3tag_set_textinfo( gfp, "TIT2", str, true );
		case 'l': return Jid3tag.id3tag_set_textinfo( gfp, "TALB", str, true );
		case 'g': return Jid3tag.id3tag_set_textinfo( gfp, "TCON", str, true );
		case 'c': return Jid3tag.id3tag_set_comment( gfp, null, null, str, true );
		case 'n': return Jid3tag.id3tag_set_textinfo( gfp, "TRCK", str, true );
		case 'y': return Jid3tag.id3tag_set_textinfo( gfp, "TYER", str, true );
		case 'v': return Jid3tag.id3tag_set_fieldvalue( gfp, str, true );
		}
		return 0;
	}

	private static final int set_id3tag(final Jlame_global_flags gfp, final int type, final String str) {
		switch( type ) {
		case 'a': Jid3tag.id3tag_set_artist( gfp, str ); return 0;
		case 't': Jid3tag.id3tag_set_title( gfp, str ); return 0;
		case 'l': Jid3tag.id3tag_set_album( gfp, str ); return 0;
		case 'g': Jid3tag.id3tag_set_genre( gfp, str ); return 0;
		case 'c': Jid3tag.id3tag_set_comment( gfp, str ); return 0;
		case 'n': return Jid3tag.id3tag_set_track( gfp, str );
		case 'y': Jid3tag.id3tag_set_year( gfp, str ); return 0;
		case 'v': return Jid3tag.id3tag_set_fieldvalue( gfp, str, false );
		}
		return 0;
	}

	private static final int id3_tag(final Jlame_global_flags gfp, final int type, final int /*TextEncoding*/ enc, final String str ) {
		int result;
		if( enc == TENC_UTF16 && type != 'v' ) {
			id3_tag( gfp, type, TENC_LATIN1, str ); /* for id3v1 */
		}
		String x = str;
	//	switch( enc )
	//	{
	//	default:
//# ifdef ID3TAGS_EXTENDED
	//	case TENC_LATIN1: x = toLatin1( str ); break;
	//	case TENC_UTF16:  x = toUtf16( str );   break;
/* #else
		case TENC_RAW:    x = strdup(str);   break;
#endif */
	//	}
		switch( enc )
		{
		default:
//# ifdef ID3TAGS_EXTENDED
		case TENC_LATIN1: result = set_id3tag( gfp, type, x );   break;
		case TENC_UTF16:  result = set_id3v2tag( gfp, type, x ); break;
/* #else
		case TENC_RAW:    result = set_id3tag(gfp, type, x);   break;
#endif */
		}
		x = null;
		return result;
	}

	/************************************************************************
	*
	* license
	*
	* PURPOSE:  Writes version and license to the file specified by fp
	*
	************************************************************************/

	static int lame_version_print(final PrintStream fp) {
		final String b = Jlame_version.get_lame_os_bitness();
		final String v = Jlame_version.get_lame_version();
		final String u = Jlame_version.get_lame_url();
		final int lenb = b.length();
		final int lenv = v.length();
		final int lenu = u.length();
		final int lw = 80;       /* line width of terminal in characters */
		final int sw = 16;       /* static width of text */

		if( lw >= lenb + lenv + lenu + sw || lw < lenu + 2 ) {
			if( lenb > 0 ) {
				fp.printf("LAME %s version %s (%s)\n\n", b, v, u );
			} else {
				fp.printf("LAME version %s (%s)\n\n", v, u );
			}
		} else {
			final int n_white_spaces = ((lenu + 2) > lw ? 0 : lw - 2 - lenu);
			/* text too long, wrap url into next line, right aligned */
			if( lenb > 0 ) {
				// fp.printf("LAME %s version %s\n%*s(%s)\n\n", b, v, n_white_spaces, "", u);
				final StringBuilder sb = new StringBuilder( String.format("LAME %s version %s\n", b, v ) );
				if( n_white_spaces > 0 ) {
					final String format = "%" + n_white_spaces + "s";
					sb.append( String.format( format, "" ) );
				}
				sb.append( String.format("(%s)\n\n", u ) );
				fp.printf( sb.toString() );
			} else {
				// fp.printf("LAME version %s\n%*s(%s)\n\n", v, n_white_spaces, "", u);
				final StringBuilder sb = new StringBuilder( String.format("LAME version %s\n", v ) );
				if( n_white_spaces > 0 ) {
					final String format = "%" + n_white_spaces + "s";
					sb.append( String.format( format, "" ) );
				}
				sb.append( String.format("(%s)\n\n", u ) );
				fp.printf( sb.toString() );
			}
		}
		/* if( lame_alpha_version_enabled ) {
			fp.printf("warning: alpha versions should be used for testing only\n\n");
		}*/

		return 0;
	}

	/**
	 * print version & license
	 *
	 * @param fp an output stream
	 * @return a status
	 */
	private static final int print_license(final PrintStream fp) {
		lame_version_print( fp );
		fp.printf(
			"Copyright (c) 1999-2011 by The LAME Project\n" +
			"Copyright (c) 1999,2000,2001 by Mark Taylor\n" +
			"Copyright (c) 1998 by Michael Cheng\n" +
			"Copyright (c) 1995,1996,1997 by Michael Hipp: mpglib\n\n");
		fp.printf(
			"This library is free software; you can redistribute it and/or\n" +
			"modify it under the terms of the GNU Library General Public\n" +
			"License as published by the Free Software Foundation; either\n" +
			"version 2 of the License, or (at your option) any later version.\n" +
			"\n");
		fp.printf("This library is distributed in the hope that it will be useful,\n" +
			"but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
			"MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU\n" +
			"Library General Public License for more details.\n" +
			"\n");
		fp.printf("You should have received a copy of the GNU Library General Public\n" +
			"License along with this program. If not, see\n" +
			"<http://www.gnu.org/licenses/>.\n");
		return 0;
	}

	/************************************************************************
	*
	* usage
	*
	* PURPOSE:  Writes command line syntax to the file specified by fp
	*
	************************************************************************/
	/** print general syntax */
	static final int usage(final PrintStream fp, final String ProgramName ) {
		lame_version_print( fp );
		fp.printf(
			"usage: %s [options] <infile> [outfile]\n" +
			"\n" +
			"    <infile> and/or <outfile> can be \"-\", which means stdin/stdout.\n" +
			"\n" +
			"Try:\n" +
			"     \"%s --help\"           for general usage information\n" +
			" or:\n" +
			"     \"%s --preset help\"    for information on suggested predefined settings\n" +
			" or:\n" +
			"     \"%s --longhelp\"\n" +
			"  or \"%s -?\"              for a complete options list\n\n",
			ProgramName, ProgramName, ProgramName, ProgramName, ProgramName);
		return 0;
	}

	/************************************************************************
	*
	* usage
	*
	* PURPOSE:  Writes command line syntax to the file specified by fp
	*           but only the most important ones, to fit on a vt100 terminal
	*
	************************************************************************/
	/** print short syntax help */
	@SuppressWarnings("boxing")
	static final int short_help(final Jlame_global_flags gfp, final PrintStream fp, final String ProgramName ) {
		lame_version_print( fp );
		fp.printf(
			"usage: %s [options] <infile> [outfile]\n" +
			"\n" +
			"    <infile> and/or <outfile> can be \"-\", which means stdin/stdout.\n" +
			"\nRECOMMENDED:\n    lame -V2 input.wav output.mp3\n\n", ProgramName);
		fp.printf(
			"OPTIONS:\n" +
			"    -b bitrate      set the bitrate, default 128 kbps\n" +
			"    -h              higher quality, but a little slower.\n" +
			"    -f              fast mode (lower quality)\n" +
			"    -V n            quality setting for VBR.  default n=%d\n" +
			"                    0=high quality,bigger files. 9.999=smaller files\n",
			gfp.lame_get_VBR_q() );
		fp.printf(
			"    --preset type   type must be \"medium\", \"standard\", \"extreme\", \"insane\",\n" +
			"                    or a value for an average desired bitrate and depending\n" +
			"                    on the value specified, appropriate quality settings will\n" +
			"                    be used.\n" +
			"                    \"--preset help\" gives more info on these\n\n");
		fp.printf(
			"    --priority type  sets the process priority\n" +
			"                     0,1 = Low priority\n" +
			"                     2   = normal priority\n" +
			"                     3,4 = High priority\n\n" +
/*
			"    --priority type  sets the process priority\n"
			"                     0 = Low priority\n"
			"                     1 = Medium priority\n"
			"                     2 = Regular priority\n"
			"                     3 = High priority\n"
			"                     4 = Maximum priority\n" "\n"
*/
			"    --help id3      ID3 tagging related options\n\n" +
			"    --longhelp      full list of options\n\n" +
			"    --license       print License information\n\n"
		);

		return 0;
	}

	/************************************************************************
	*
	* usage
	*
	* PURPOSE:  Writes command line syntax to the file specified by fp
	*
	************************************************************************/

	private static final void wait_for(final PrintStream fp, final boolean lessmode ) {
		if( lessmode ) {
			fp.flush();
			// getchar();// TODO java: try to fix it
		} else {
			fp.printf("\n");
		}
		fp.printf("\n");
	}

	static void help_id3tag(final PrintStream fp ) {
		fp.printf(
			"  ID3 tag options:\n" +
			"    --tt <title>    audio/song title (max 30 chars for version 1 tag)\n" +
			"    --ta <artist>   audio/song artist (max 30 chars for version 1 tag)\n" +
			"    --tl <album>    audio/song album (max 30 chars for version 1 tag)\n" +
			"    --ty <year>     audio/song year of issue (1 to 9999)\n" +
			"    --tc <comment>  user-defined text (max 30 chars for v1 tag, 28 for v1.1)\n");
		fp.printf(
			"    --tn <track[/total]>   audio/song track number and (optionally) the total\n" +
			"                           number of tracks on the original recording. (track\n" +
			"                           and total each 1 to 255. just the track number\n" +
			"                           creates v1.1 tag, providing a total forces v2.0).\n");
		fp.printf("    --tg <genre>    audio/song genre (name or number in list)\n" +
			"    --ti <file>     audio/song albumArt (jpeg/png/gif file, v2.3 tag)\n" +
			"    --tv <id=value> user-defined frame specified by id and value (v2.3 tag)\n" +
			"                    syntax: --tv \"TXXX=description=content\"\n"
			);
		fp.printf(
			"    --add-id3v2     force addition of version 2 tag\n" +
			"    --id3v1-only    add only a version 1 tag\n" +
			"    --id3v2-only    add only a version 2 tag\n" +
// #ifdef ID3TAGS_EXTENDED
			"    --id3v2-utf16   add following options in unicode text encoding\n" +
			"    --id3v2-latin1  add following options in latin-1 text encoding\n" +
//#endif
			"    --space-id3v1   pad version 1 tag with spaces instead of nulls\n" +
			"    --pad-id3v2     same as '--pad-id3v2-size 128'\n" +
			"    --pad-id3v2-size <value> adds version 2 tag, pad with extra <value> bytes\n" +
			"    --genre-list    print alphabetically sorted ID3 genre list and exit\n" +
			"    --ignore-tag-errors  ignore errors in values passed for tags\n\n"
			);
		fp.printf(
			"    Note: A version 2 tag will NOT be added unless one of the input fields\n" +
			"    won't fit in a version 1 tag (e.g. the title string is longer than 30\n" +
			"    characters), or the '--add-id3v2' or '--id3v2-only' options are used,\n" +
			"    or output is redirected to stdout.\n"
			);
	}

	static void help_developer_switches(final PrintStream fp ) {
		//if( ! internal_opts_enabled ) {
			fp.printf(
			"    Note: Almost all of the following switches aren't available in this build!\n\n"
			);
		//}
		fp.printf(
			"  ATH related:\n" +
			"    --noath         turns ATH down to a flat noise floor\n" +
			"    --athshort      ignore GPSYCHO for short blocks, use ATH only\n" +
			"    --athonly       ignore GPSYCHO completely, use ATH only\n" +
			"    --athtype n     selects between different ATH types [0-4]\n" +
			"    --athlower x    lowers ATH by x dB\n"
			);
		fp.printf(
			"    --athaa-type n  ATH auto adjust: 0 'no' else 'loudness based'\n" +
			"    --athaa-sensitivity x  activation offset in -/+ dB for ATH auto-adjustment\n" +
			"\n");
		fp.printf(
			"  PSY related:\n" +
			"    --short         use short blocks when appropriate\n" +
			"    --noshort       do not use short blocks\n" +
			"    --allshort      use only short blocks\n"
			);
		fp.printf(
			"(1) --temporal-masking x   x=0 disables, x=1 enables temporal masking effect\n" +
			"    --nssafejoint   M/S switching criterion\n" +
			"    --nsmsfix <arg> M/S switching tuning [effective 0-3.5]\n" +
			"(2) --interch x     adjust inter-channel masking ratio\n" +
			"    --ns-bass x     adjust masking for sfbs  0 -  6 (long)  0 -  5 (short)\n" +
			"    --ns-alto x     adjust masking for sfbs  7 - 13 (long)  6 - 10 (short)\n" +
			"    --ns-treble x   adjust masking for sfbs 14 - 21 (long) 11 - 12 (short)\n"
			);
		fp.printf(
			"    --ns-sfb21 x    change ns-treble by x dB for sfb21\n" +
			"    --shortthreshold x,y  short block switching threshold,\n" +
			"                          x for L/R/M channel, y for S channel\n" +
			"    -Z [n]          always do calculate short block maskings\n");
		fp.printf(	"  Noise Shaping related:\n" +
			"(1) --substep n     use pseudo substep noise shaping method types 0-2\n" +
			"(1) -X n[,m]        selects between different noise measurements\n" +
			"                    n for long block, m for short. if m is omitted, m = n\n" +
			" 1: CBR, ABR and VBR-old encoding modes only\n" +
			" 2: ignored\n"
			);
	}

	/** print long syntax help */
	@SuppressWarnings("boxing")
	private static final int long_help(final Jlame_global_flags gfp, final PrintStream fp, final String ProgramName, final boolean lessmode ) {
		lame_version_print( fp );
		fp.printf(
			"usage: %s [options] <infile> [outfile]\n" +
			"\n" +
			"    <infile> and/or <outfile> can be \"-\", which means stdin/stdout.\n" +
			"\nRECOMMENDED:\n    lame -V2 input.wav output.mp3\n\n", ProgramName);
		fp.printf(
			"OPTIONS:\n" +
			"  Input options:\n" +
			"    --scale <arg>   scale input (multiply PCM data) by <arg>\n" +
			"    --scale-l <arg> scale channel 0 (left) input (multiply PCM data) by <arg>\n" +
			"    --scale-r <arg> scale channel 1 (right) input (multiply PCM data) by <arg>\n" +
			"    --swap-channel  swap L/R channels\n" +
			"    --ignorelength  ignore file length in WAV header\n" +
			"    --gain <arg>    apply Gain adjustment in decibels, range -20.0 to +12.0\n"
			);
//#if( defined HAVE_MPGLIB )
		fp.printf("    --mp1input      input file is a MPEG Layer I   file\n" +
			"    --mp2input      input file is a MPEG Layer II  file\n" +
			"    --mp3input      input file is a MPEG Layer III file\n"
			);
//#endif
		fp.printf("    --nogap <file1> <file2> <...>\n" +
			"                    gapless encoding for a set of contiguous files\n" +
			"    --nogapout <dir>\n" +
			"                    output dir for gapless encoding (must precede --nogap)\n" +
			"    --nogaptags     allow the use of VBR tags in gapless encoding\n" +
			"    --out-dir <dir> output dir, must exist\n"
			);
		fp.printf(
			"\n" +
			"  Input options for RAW PCM:\n" +
			"    -r              input is raw pcm\n" +
			"    -s sfreq        sampling frequency of input file (kHz) - default 44.1 kHz\n" +
			"    --signed        input is signed (default)\n" +
			"    --unsigned      input is unsigned\n" +
			"    --bitwidth w    input bit width is w (default 16)\n" +
			"    -x              force byte-swapping of input\n" +
			"    --little-endian input is little-endian (default)\n" +
			"    --big-endian    input is big-endian\n" +
			"    -a              downmix from stereo to mono file for mono encoding\n"
			);

		wait_for( fp, lessmode );
		fp.printf(
			"  Operational options:\n" +
			"    -m <mode>       (j)oint, (s)imple, (f)orce, (d)ual-mono, (m)ono (l)eft (r)ight\n" +
			"                    default is (j)\n" +
			"                    joint  = Uses the best possible of MS and LR stereo\n" +
			"                    simple = force LR stereo on all frames\n" +
			"                    force  = force MS stereo on all frames.\n"
			);
		fp.printf(
			"    --preset type   type must be \"medium\", \"standard\", \"extreme\", \"insane\",\n" +
			"                    or a value for an average desired bitrate and depending\n" +
			"                    on the value specified, appropriate quality settings will\n" +
			"                    be used.\n" +
			"                    \"--preset help\" gives more info on these\n" +
			"    --comp  <arg>   choose bitrate to achieve a compression ratio of <arg>\n");
		fp.printf("    --replaygain-fast   compute RG fast but slightly inaccurately (default)\n" +
//#ifdef DECODE_ON_THE_FLY
			"    --replaygain-accurate   compute RG more accurately and find the peak sample\n" +
//#endif
			"    --noreplaygain  disable ReplayGain analysis\n" +
//#ifdef DECODE_ON_THE_FLY
			"    --clipdetect    enable --replaygain-accurate and print a message whether\n" +
			"                    clipping occurs and how far the waveform is from full scale\n"
//#endif
			);
		fp.printf(
			"    --flush         flush output stream as soon as possible\n" +
			"    --freeformat    produce a free format bitstream\n" +
			"    --decode        input=mp3 file, output=wav\n" +
			"    -t              disable writing wav header when using --decode\n");

		wait_for( fp, lessmode );
		fp.printf(
			"  Verbosity:\n" +
			"    --disptime <arg>print progress report every arg seconds\n" +
			"    -S              don't print progress report, VBR histograms\n" +
			"    --nohist        disable VBR histogram display\n" +
			"    --quiet         don't print anything on screen\n" +
			"    --silent        don't print anything on screen, but fatal errors\n" +
			"    --brief         print more useful information\n" +
			"    --verbose       print a lot of useful information\n\n");
		fp.printf(
			"  Noise shaping & psycho acoustic algorithms:\n" +
			"    -q <arg>        <arg> = 0...9.  Default  -q 3 \n" +
			"                    -q 0:  Highest quality, very slow \n" +
			"                    -q 9:  Poor quality, but fast \n" +
			"    -h              Same as -q 2.   \n" +
			"    -f              Same as -q 7.   Fast, ok quality\n");

		wait_for( fp, lessmode );
		fp.printf(
			"  CBR (constant bitrate, the default) options:\n" +
			"    -b <bitrate>    set the bitrate in kbps, default 128 kbps\n" +
			"    --cbr           enforce use of constant bitrate\n" +
			"\n" +
			"  ABR options:\n" +
			"    --abr <bitrate> specify average bitrate desired (instead of quality)\n\n");
		fp.printf(
			"  VBR options:\n" +
			"    -V n            quality setting for VBR.  default n=%d\n" +
			"                    0=high quality,bigger files. 9=smaller files\n" +
			"    -v              the same as -V 4\n" +
			"    --vbr-old       use old variable bitrate (VBR) routine\n" +
			"    --vbr-new       use new variable bitrate (VBR) routine (default)\n" +
			"    -Y              lets LAME ignore noise in sfb21, like in CBR\n" +
			"                    (Default for V3 to V9.999)\n"
			,
			gfp.lame_get_VBR_q() );
		fp.printf(
			"    -b <bitrate>    specify minimum allowed bitrate, default  32 kbps\n" +
			"    -B <bitrate>    specify maximum allowed bitrate, default 320 kbps\n" +
			"    -F              strictly enforce the -b option, for use with players that\n" +
			"                    do not support low bitrate mp3\n" +
			"    -t              disable writing LAME Tag\n" +
			"    -T              enable and force writing LAME Tag\n");

		wait_for( fp, lessmode );

		fp.printf(
			"  MP3 header/stream options:\n" +
			"    -e <emp>        de-emphasis n/5/c  (obsolete)\n" +
			"    -c              mark as copyright\n" +
			"    -o              mark as non-original\n" +
			"    -p              error protection.  adds 16 bit checksum to every frame\n" +
			"                    (the checksum is computed correctly)\n" +
			"    --nores         disable the bit reservoir\n" +
			"    --strictly-enforce-ISO   comply as much as possible to ISO MPEG spec\n");
		fp.printf(
			"    --buffer-constraint <constraint> available values for constraint:\n" +
			"                                     default, strict, maximum\n" +
			"\n"
			);
		fp.printf(
			"  Filter options:\n" +
			"  --lowpass <freq>        frequency(kHz), lowpass filter cutoff above freq\n" +
			"  --lowpass-width <freq>  frequency(kHz) - default 15%% of lowpass freq\n" +
			"  --highpass <freq>       frequency(kHz), highpass filter cutoff below freq\n" +
			"  --highpass-width <freq> frequency(kHz) - default 15%% of highpass freq\n");
		fp.printf(
			"  --resample <sfreq>  sampling frequency of output file(kHz)- default=automatic\n");

		wait_for( fp, lessmode );
		help_id3tag( fp );
		fp.printf(
			"\n\nJava specific options:\n" +
			"    --priority <type>  sets the process priority:\n" +
			"                         0,1 = Low priority (IDLE_PRIORITY_CLASS)\n" +
			"                         2 = normal priority (NORMAL_PRIORITY_CLASS, default)\n" +
			"                         3,4 = High priority (HIGH_PRIORITY_CLASS))\n" +
			"    Note: Calling '--priority' without a parameter will select priority 0.\n" +
/*
			"\n\nOS/2-specific options:\n"
			"    --priority <type>  sets the process priority:\n"
			"                         0 = Low priority (IDLE, delta = 0)\n"
			"                         1 = Medium priority (IDLE, delta = +31)\n"
			"                         2 = Regular priority (REGULAR, delta = -31)\n"
			"                         3 = High priority (REGULAR, delta = 0)\n"
			"                         4 = Maximum priority (REGULAR, delta = +31)\n"
			"    Note: Calling '--priority' without a parameter will select priority 0.\n"
*/
			"\nMisc:\n    --license       print License information\n\n"
			);

		display_bitrates( fp );

		return 0;
	}

	@SuppressWarnings("boxing")
	private static final void display_bitrate(final PrintStream fp, final String version, final int d, final int indx ) {
		int nBitrates = 14;
		if( d == 4 ) {
			nBitrates = 8;
		}

		fp.printf(
			"\nMPEG-%-3s layer III sample frequencies (kHz):  %2d  %2d  %g\n" +
			"bitrates (kbps):", version, 32 / d, 48 / d, 44.1 / d);
		for( int i = 1; i <= nBitrates; i++ ) {
			fp.printf(" %2d", Jtables.lame_get_bitrate( indx, i ) );
		}
		fp.printf("\n");
	}

	static final int display_bitrates(final PrintStream fp ) {
		display_bitrate( fp, "1", 1, 1 );
		display_bitrate( fp, "2", 2, 0 );
		display_bitrate( fp, "2.5", 4, 0 );
		fp.printf("\n");
		fp.flush();
		return 0;
	}

	/*  note: for presets it would be better to externalize them in a file.
	    suggestion:  lame --preset <file-name> ...
	            or:  lame --preset my-setting  ... and my-setting is defined in lame.ini
	 */

	/*
	Note from GB on 08/25/2002:
	I am merging --presets and --alt-presets. Old presets are now aliases for
	corresponding abr values from old alt-presets. This way we now have a
	unified preset system, and I hope than more people will use the new tuned
	presets instead of the old unmaintained ones.
	*/

	/************************************************************************
	*
	* usage
	*
	* PURPOSE:  Writes presetting info to #stdout#
	*
	************************************************************************/
	private static final void presets_longinfo_dm(final PrintStream msgfp ) {
		msgfp.printf(
			"\n" +
			"The --preset switches are aliases over LAME settings.\n" +
			"\n\n");
		msgfp.printf(
			"To activate these presets:\n" +
			"\n   For VBR modes (generally highest quality):\n\n");
		msgfp.printf(
			"     \"--preset medium\" This preset should provide near transparency\n" +
			"                             to most people on most music.\n" +
			"\n" +
			"     \"--preset standard\" This preset should generally be transparent\n" +
			"                             to most people on most music and is already\n" +
			"                             quite high in quality.\n\n");
		msgfp.printf(
			"     --preset extreme     If you have extremely good hearing and similar\n" +
			"                          equipment, this preset will generally provide\n" +
			"                          slightly higher quality than the \"standard\" mode.\n\n");
		msgfp.printf(
			"   For CBR 320kbps (highest quality possible from the --preset switches):\n" +
			"\n" +
			"     --preset insane      This preset will usually be overkill for most people\n" +
			"                          and most situations, but if you must have the\n" +
			"                          absolute highest quality with no regard to filesize,\n" +
			"                          this is the way to go.\n\n");
		msgfp.printf(
			"   For ABR modes (high quality per given bitrate but not as high as VBR):\n" +
			"\n" +
			"     --preset <kbps>      Using this preset will usually give you good quality\n" +
			"                          at a specified bitrate. Depending on the bitrate\n" +
			"                          entered, this preset will determine the optimal\n" +
			"                          settings for that particular situation. For example:\n" +
			"                          \"--preset 185\" activates this preset and uses 185\n" +
			"                          as an average kbps.\n\n");
		msgfp.printf(
			"   \"cbr\"  - If you use the ABR mode (read above) with a significant\n" +
			"            bitrate such as 80, 96, 112, 128, 160, 192, 224, 256, 320,\n" +
			"            you can use the \"cbr\" option to force CBR mode encoding\n" +
			"            instead of the standard abr mode. ABR does provide higher\n" +
			"            quality but CBR may be useful in situations such as when\n" +
			"            streaming an mp3 over the internet may be important.\n\n");
		msgfp.printf(
			"    For example:\n" +
			"\n" +
			"    --preset standard <input file> <output file>\n" +
			" or --preset cbr 192 <input file> <output file>\n" +
			" or --preset 172 <input file> <output file>\n" +
			" or --preset extreme <input file> <output file>\n\n\n");
		msgfp.printf(
			"A few aliases are also available for ABR mode:\n" +
			"phone => 16kbps/mono        phon+/lw/mw-eu/sw => 24kbps/mono\n" +
			"mw-us => 40kbps/mono        voice => 56kbps/mono\n" +
			"fm/radio/tape => 112kbps    hifi => 160kbps\n" +
			"cd => 192kbps               studio => 256kbps\n");
	}

	private static final int presets_set(final Jlame_global_flags gfp, final boolean fast, final boolean cbr, String preset_name, final String ProgramName ) {

		if( (preset_name.compareTo("help") == 0) && ! fast && ! cbr ) {
			lame_version_print( System.out );
			presets_longinfo_dm( System.out );
			return -1;
		}

		/*aliases for compatibility with old presets */
		boolean mono = false;
		if( preset_name.compareTo("phone") == 0 ) {
			preset_name = "16";
			mono = true;
		}
		if( (preset_name.compareTo("phon+") == 0) ||
				(preset_name.compareTo("lw") == 0) ||
				(preset_name.compareTo("mw-eu") == 0) || (preset_name.compareTo("sw") == 0) ) {
			preset_name = "24";
			mono = true;
		}
		if( preset_name.compareTo("mw-us") == 0 ) {
			preset_name = "40";
			mono = true;
		}
		if( preset_name.compareTo("voice") == 0 ) {
			preset_name = "56";
			mono = true;
		}
		if( preset_name.compareTo("fm") == 0 ) {
			preset_name = "112";
		}
		if( (preset_name.compareTo("radio") == 0) || (preset_name.compareTo("tape") == 0) ) {
			preset_name = "112";
		}
		if( preset_name.compareTo("hifi") == 0 ) {
			preset_name = "160";
		}
		if( preset_name.compareTo("cd") == 0 ) {
			preset_name = "192";
		}
		if( preset_name.compareTo("studio") == 0 ) {
			preset_name = "256";
		}

		if( preset_name.compareTo("medium") == 0 ) {
			gfp.lame_set_VBR_q( 4 );
			gfp.lame_set_VBR( Jlame.vbr_default );
			return 0;
		}

		if( preset_name.compareTo("standard") == 0 ) {
			gfp.lame_set_VBR_q( 2 );
			gfp.lame_set_VBR( Jlame.vbr_default );
			return 0;
		}

		else if( preset_name.compareTo("extreme") == 0 ) {
			gfp.lame_set_VBR_q( 0 );
			gfp.lame_set_VBR( Jlame.vbr_default );
			return 0;
		}

		else if( (preset_name.compareTo("insane") == 0) && ! fast ) {
			gfp.lame_set_preset( Jlame.INSANE );
			return 0;
		}

		/* Generic ABR Preset */
		try {
			final int preset_number = Integer.parseInt( preset_name );
			if( (preset_number > 0) && ! fast ) {
				if( preset_number >= 8 && preset_number <= 320 ) {
					gfp.lame_set_preset( preset_number );

					if( cbr ) {
						gfp.lame_set_VBR( Jlame.vbr_off );
					}

					if( mono ) {
						gfp.lame_set_mode( Jlame.MONO );
					}

					return 0;

				} else {
					lame_version_print( Jconsole.Console_IO.Error_fp );
					System.err.printf("Error: The bitrate specified is out of the valid range for this preset\n" +
							"\n" +
							"When using this mode you must enter a value between \"32\" and \"320\"\n" +
							"\nFor further information try: \"%s --preset help\"\n", ProgramName );
					return -1;
				}
			}
		} catch(final NumberFormatException ne) {
		}

		lame_version_print( Jconsole.Console_IO.Error_fp );
		System.err.printf("Error: You did not enter a valid profile and/or options with --preset\n" +
			"\n" +
			"Available profiles are:\n" +
			"\n" +
			"                 medium\n" +
			"                 standard\n" +
			"                 extreme\n" +
			"                 insane\n" +
			"          <cbr> (ABR Mode) - The ABR Mode is implied. To use it,\n" +
			"                             simply specify a bitrate. For example:\n" +
			"                             \"--preset 185\" activates this\n" +
			"                             preset and uses 185 as an average kbps.\n\n");
		System.err.printf("    Some examples:\n" +
			"\n" +
			" or \"%s --preset standard <input file> <output file>\"\n" +
			" or \"%s --preset cbr 192 <input file> <output file>\"\n" +
			" or \"%s --preset 172 <input file> <output file>\"\n" +
			" or \"%s --preset extreme <input file> <output file>\"\n" +
			"\n" +
			"For further information try: \"%s --preset help\"\n", ProgramName, ProgramName,
			ProgramName, ProgramName, ProgramName);
		return -1;
	}

	// Jid3tag_handler
	// private static final void genre_list_handler(final int num, final String name, final Object cookie)
	@SuppressWarnings("boxing")
	@Override
	public final void handle(final int num, final String name, final Object cookie) {
		System.out.printf("%3d %s\n", num, name );
	}

	/************************************************************************
	*
	* parse_args
	*
	* PURPOSE:  Sets encoding parameters to the specifications of the
	* command line.  Default settings are used for parameters
	* not specified in the command line.
	*
	* If the input file is in WAVE or AIFF format, the sampling frequency is read
	* from the AIFF header.
	*
	* The input and output filenames are read into #inpath# and #outpath#.
	*
	************************************************************************/

	/** LAME is a simple frontend which just uses the file extension
	 * to determine the file type.  Trying to analyze the file
	 * contents is well beyond the scope of LAME and should not be added. */
	private static final int filename_to_type(String FileName) {
		final int len = FileName.length();
		if( len < 4 ) {
			return Jsound_file_format.sf_unknown;
		}

		FileName = FileName.substring( len - 4 );
		if( 0 == FileName.compareToIgnoreCase(".mpg") ) {
			return Jsound_file_format.sf_mp123;
		}
		if( 0 == FileName.compareToIgnoreCase(".mp1") ) {
			return Jsound_file_format.sf_mp123;
		}
		if( 0 == FileName.compareToIgnoreCase(".mp2") ) {
			return Jsound_file_format.sf_mp123;
		}
		if( 0 == FileName.compareToIgnoreCase(".mp3") ) {
			return Jsound_file_format.sf_mp123;
		}
		if( 0 == FileName.compareToIgnoreCase(".wav") ) {
			return Jsound_file_format.sf_wave;
		}
		if( 0 == FileName.compareToIgnoreCase(".aif") ) {
			return Jsound_file_format.sf_aiff;
		}
		if( 0 == FileName.compareToIgnoreCase(".raw") ) {
			return Jsound_file_format.sf_raw;
		}
		if( 0 == FileName.compareToIgnoreCase(".ogg") ) {
			return Jsound_file_format.sf_ogg;
		}
		return Jsound_file_format.sf_unknown;
	}

	@SuppressWarnings("boxing")
	private static final int resample_rate(double freq) {
		if( freq >= 1.e3 ) {
			freq *= 1.e-3;
		}

		switch( (int) freq ) {
		case 8:
			return 8000;
		case 11:
			return 11025;
		case 12:
			return 12000;
		case 16:
			return 16000;
		case 22:
			return 22050;
		case 24:
			return 24000;
		case 32:
			return 32000;
		case 44:
			return 44100;
		case 48:
			return 48000;
		default:
			System.err.printf("Illegal resample frequency: %.3f kHz\n", freq );
			return 0;
		}
	}
	/*
	private static final char SLASH = '\\';
	private static final char COLON = ':';
	private static final String suffixes[] =
		{ ".WAV", ".RAW", ".MP1", ".MP2"
		, ".MP3", ".MPG", ".MPA", ".CDA"
		, ".OGG", ".AIF", ".AIFF", ".AU"
		, ".SND", ".FLAC", ".WV", ".OFR"
		, ".TAK", ".MP4", ".M4A", ".PCM"
		, ".W64"
		};
	private static boolean isCommonSuffix(final String s_ext) {
		for( int i = 0; i < suffixes.length; ++i ) {
			if( s_ext.compareToIgnoreCase( suffixes[i] ) == 0 ) {
				return true;
			}
		}
		return false;
	}
	 */
	/* java: replace by the code:
	final int i = inPath.lastIndexOf(".");
	if( i >= 0 ) {
		outPath.setLength( 0 );
		if( outDir.endsWith("\\") || outDir.endsWith("/") ) {
			outDir = outDir.substring( 0, outDir.length() - 1 );
		}
		outPath.append( outDir ).append( File.pathSeparatorChar ).append( inPath.substring( 0, i ) ).append( s_ext );
	}
	-- end code
	static final boolean generateOutPath(final String inPath, final String outDir, final String s_ext, final StringBuilder outPath)
	{
// #if 1
		boolean out_dir_used = false;

		if( outDir != null && ! outDir.isEmpty() ) {
			out_dir_used = true;
			outPath.append( outDir );
			if( outPath.charAt( outPath.length() - 1 ) != File.separatorChar ) {
				outPath.append( File.separatorChar );
			}
		}
		else {
			final int n = inPath.indexOf( File.separatorChar );
			outPath.append( inPath.substring( 0, n < 0 ? inPath.length() : n ) );
			if( outPath.length() > 0 ) {
				outPath.append( File.separatorChar );
			}
		}
		{
			boolean replace_suffix = false;
			final int n = inPath.indexOf('.');
			outPath.append( n < 0 ? inPath : inPath.substring( 0, n ) );
			if( n < inPath.length() && isCommonSuffix( inPath.substring( n ) ) ) {
				replace_suffix = true;
				if( ! out_dir_used ) {
					if( inPath.substring( n ).compareToIgnoreCase( s_ext ) == 0 ) {
						replace_suffix = false;
					}
				}
			}
			if( ! replace_suffix && n < inPath.length() ) {
				outPath.append( inPath.substring( n ) );
			}
		}
		outPath.append( s_ext );
		return false;
//err_generateOutPath:
	//	error_printf( "error: output file name too long\n" );
	//	return true;
// #else
		// outPath.append( inPath ).append( s_ext );
		// return false;
// #endif
	} */

	private static final int set_id3_albumart(final Jlame_global_flags gfp, final String file_name) {
		int ret = -1;

		if( file_name == null ) {
			return 0;
		}
		RandomAccessFile fpi = null;
		try {
			fpi = new RandomAccessFile( file_name, "r" );
			final long lsize = fpi.length();
			if( lsize > Integer.MAX_VALUE ) {// java added
				ret = 5;
			} else {
				final int size = (int)lsize;
				byte albumart[] = new byte[ size ];
				if( fpi.read( albumart, 0, size ) != size ) {
					ret = 3;
				} else {
					ret = Jid3tag.id3tag_set_albumart( gfp, albumart, size ) != 0 ? 4 : 0;
				}
				albumart = null;
			}
		} catch(final FileNotFoundException fe) {
			ret = 1;
		} catch(final IOException ie) {
			ret = 3;
		} finally {
			if( fpi != null ) {
				try { fpi.close(); } catch( final IOException e ) {}
			}
		}
		switch( ret ) {
		case 1: System.err.printf("Could not find: '%s'.\n", file_name); break;
		case 2: System.err.printf("Insufficient memory for reading the albumart.\n"); break;
		case 3: System.err.printf("Read error: '%s'.\n", file_name); break;
		case 4: System.err.printf("Unsupported image: '%s'.\nSpecify JPEG/PNG/GIF image\n", file_name); break;
		case 5: System.err.printf("Image file size too large\n", file_name); break;
		default: break;
		}
		return ret;
	}

	// enum ID3TAG_MODE{
	private static final int ID3TAG_MODE_DEFAULT = 0;
	private static final int ID3TAG_MODE_V1_ONLY = 1;
	private static final int ID3TAG_MODE_V2_ONLY = 2;
	//};

	private static boolean dev_only_with_arg(final String str, final String token, final String nextArg, final boolean[] argIgnored, final boolean[] argUsed)
	{
		if( 0 != token.compareToIgnoreCase( str ) ) {
			return false;
		}
		argUsed[0] = true;
		if (internal_opts_enabled) {
			return true;
		}
		argIgnored[0] = true;
		Jconsole.error_printf("WARNING: ignoring developer-only switch --%s %s\n", token, nextArg);
		return false;
	}

	private static boolean dev_only_without_arg(final String str, final String token, final boolean[] argIgnored)
	{
		if( 0 != token.compareToIgnoreCase( str ) ) {
			return false;
		}
		if( internal_opts_enabled ) {
			return true;
		}
		argIgnored[0] = true;
		Jconsole.error_printf("WARNING: ignoring developer-only switch --%s\n", token);
		return false;
	}

/* Ugly, NOT final version */

	@SuppressWarnings("boxing")
	static final int parse_args(final Jlame_global_flags gfp, final String ProgramName, final String[] args,
			final StringBuilder inPath, final StringBuilder outPath, final String[] nogap_inPath, final int[] num_nogap)
	{
		String outDir = "";
		boolean input_file = false;  /* set to 1 if we parse an input file name  */
		boolean autoconvert = false;
		boolean nogap = false;
		boolean nogap_tags = false;  /* set to 1 to use VBR tags in NOGAP mode */
		int     count_nogap = 0;
		boolean noreplaygain = false; /* is RG explicitly disabled by the user */
		int     id3tag_mode = ID3TAG_MODE_DEFAULT;
		boolean ignore_tag_errors = false;  /* Ignore errors in values passed for tags */

		int /* enum TextEncoding */ id3_tenc = TENC_UTF16;

/* #ifdef HAVE_ICONV
		setlocale( LC_CTYPE, "" );
#endif */

		inPath.setLength( 0 );
		outPath.setLength( 0 );
		/* turn on display options. user settings may turn them off below */
		global_ui_config.silent = 0; /* default */
		global_ui_config.brhist = true;
		global_decoder.mp3_delay = 0;
		global_decoder.mp3_delay_set = false;
		global_decoder.disable_wav_header = false;
		global_ui_config.print_clipping_info = false;
		Jid3tag.id3tag_init( gfp );

		/* process args */
		for( int i = 0; i < args.length; i++ ) {
			boolean argUsed;
			boolean argIgnored = false;// FIXME why it needs?

			String a = args[i];
			int token = 0;
			if( a.charAt( token++ ) == '-' ) {
				argUsed = false;
				String nextArg = i + 1 < args.length ? args[i + 1] : "";

				if( token >= a.length() ) { /* The user wants to use stdin and/or stdout. */
					input_file = true;
					if( inPath.length() == 0 ) {
						inPath.append( args[i] );
					} else if( outPath.length() == 0 ) {
						outPath.append( args[i] );
					}
				}
				if( a.charAt( token ) == '-' ) { /* GNU style */
					double  double_value = 0;
					int     int_value = 0;
					token++;
					a = a.substring( token );

					if( 0 == a.compareToIgnoreCase("resample") ) {
						try {
							double_value = Double.parseDouble( nextArg );
							argUsed = true;
							gfp.lame_set_out_samplerate( resample_rate( double_value ) );
						} catch(final NumberFormatException ne) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a);
						}

					} else if( 0 == a.compareToIgnoreCase("vbr-old") ) {
						gfp.lame_set_VBR( Jlame.vbr_rh );

					} else if( 0 == a.compareToIgnoreCase("vbr-new") ) {
						gfp.lame_set_VBR( Jlame.vbr_mt );

					} else if( 0 == a.compareToIgnoreCase("vbr-mtrh") ) {
						gfp.lame_set_VBR( Jlame.vbr_mtrh );

					} else if( 0 == a.compareToIgnoreCase("cbr") ) {
						gfp.lame_set_VBR( Jlame.vbr_off );

					} else if( 0 == a.compareToIgnoreCase("abr") ) {
						/* values larger than 8000 are bps (like Fraunhofer), so it's strange to get 320000 bps MP3 when specifying 8000 bps MP3 */
						try {
							int_value = Integer.parseInt( nextArg );
							argUsed = true;
							if( int_value >= 8000 ) {
								int_value = (int_value + 500) / 1000;
							}
							if( int_value > 320 ) {
								int_value = 320;
							}
							if( int_value < 8 ) {
								int_value = 8;
							}
							gfp.lame_set_VBR( Jlame.vbr_abr );
							gfp.lame_set_VBR_mean_bitrate_kbps( int_value );
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("r3mix") ) {
						gfp.lame_set_preset( Jlame.R3MIX );

					} else if( 0 == a.compareToIgnoreCase("bitwidth") ) {
						try {
							global_raw_pcm.in_bitwidth = Integer.parseInt( nextArg );
							argUsed = true;
						} catch(final NumberFormatException ne) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("signed") ) {
						global_raw_pcm.in_signed = true;

					} else if( 0 == a.compareToIgnoreCase("unsigned") ) {
						global_raw_pcm.in_signed = false;

					} else if( 0 == a.compareToIgnoreCase("little-endian") ) {
						// global_raw_pcm.in_endian = ByteOrderLittleEndian;
						global_raw_pcm.is_big_endian = false;

					} else if( 0 == a.compareToIgnoreCase("big-endian") ) {
						// global_raw_pcm.in_endian = ByteOrderBigEndian;
						global_raw_pcm.is_big_endian = true;

					} else if( 0 == a.compareToIgnoreCase("mp1input") ) {
						global_reader.input_format = Jsound_file_format.sf_mp1;

					} else if( 0 == a.compareToIgnoreCase("mp2input") ) {
						global_reader.input_format = Jsound_file_format.sf_mp2;

					} else if( 0 == a.compareToIgnoreCase("mp3input") ) {
						global_reader.input_format = Jsound_file_format.sf_mp3;

					} else if( 0 == a.compareToIgnoreCase("ogginput") ) {
						System.err.printf("sorry, vorbis support in LAME is deprecated.\n");
						return -1;

					} else if( 0 == a.compareToIgnoreCase("decode") ) {
						gfp.lame_set_decode_only( true );

					} else if( 0 == a.compareToIgnoreCase("flush") ) {
						global_writer.flush_write = true;

					} else if( 0 == a.compareToIgnoreCase("decode-mp3delay") ) {
						try {
							global_decoder.mp3_delay = Integer.parseInt( nextArg );
							global_decoder.mp3_delay_set = true;
							argUsed = true;
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("nores") ) {
						gfp.lame_set_disable_reservoir( true );

					} else if( 0 == a.compareToIgnoreCase("strictly-enforce-ISO") ) {
						gfp.lame_set_strict_ISO( Jlame.MDB_STRICT_ISO );

					} else if( 0 == a.compareToIgnoreCase("buffer-constraint") ) {
						argUsed = true;
						if( nextArg.compareTo("default") == 0 ) {
							gfp.lame_set_strict_ISO( Jlame.MDB_DEFAULT );
						} else if( nextArg.compareTo("strict") == 0) {
							gfp.lame_set_strict_ISO( Jlame.MDB_STRICT_ISO );
						} else if( nextArg.compareTo("maximum") == 0) {
							gfp.lame_set_strict_ISO( Jlame.MDB_MAXIMUM );
						} else {
							System.err.printf("unknown buffer constraint '%s'\n", nextArg );
							return -1;
						}

					} else if( 0 == a.compareToIgnoreCase("scale") ) {
						try {
							gfp.lame_set_scale( Float.parseFloat( nextArg ) );
							argUsed = true;
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("scale-l") ) {
						try {
							gfp.lame_set_scale_left( Float.parseFloat( nextArg ) );
							argUsed = true;
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("scale-r") ) {
						try {
							gfp.lame_set_scale_right( Float.parseFloat( nextArg ) );
							argUsed = true;
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("noasm") ) {// java: ignoring
						argUsed = true;
						/* if( 0 == nextArg.compareTo("mmx") ) {
							Jset_get.lame_set_asm_optimizations( gfp, MMX, 0 );
						}
						if( 0 == nextArg.compareTo("3dnow") ) {
							Jset_get.lame_set_asm_optimizations( gfp, AMD_3DNOW, 0 );
						}
						if( 0 == nextArg.compareTo("sse") ) {
							Jset_get.lame_set_asm_optimizations( gfp, SSE, 0 );
						}*/
					} else if( 0 == a.compareToIgnoreCase("freeformat") ) {
						gfp.lame_set_free_format( true );

					} else if( 0 == a.compareToIgnoreCase("replaygain-fast") ) {
						gfp.lame_set_findReplayGain( true );

//#ifdef DECODE_ON_THE_FLY
					} else if( 0 == a.compareToIgnoreCase("replaygain-accurate") ) {
						gfp.lame_set_decode_on_the_fly( true );
						gfp.lame_set_findReplayGain( true );
//#endif

					} else if( 0 == a.compareToIgnoreCase("noreplaygain") ) {
						noreplaygain = true;
						gfp.lame_set_findReplayGain( false );

//#ifdef DECODE_ON_THE_FLY
					} else if( 0 == a.compareToIgnoreCase("clipdetect") ) {
						global_ui_config.print_clipping_info = true;
						gfp.lame_set_decode_on_the_fly( true );
//#endif

					} else if( 0 == a.compareToIgnoreCase("nohist") ) {
						global_ui_config.brhist = false;
					} else if( 0 == a.compareToIgnoreCase("priority") ) {
						try {
							final int priority = Integer.parseInt( nextArg );
							argUsed = true;
							Thread.currentThread().setPriority( priority );
						} catch(final NumberFormatException ne) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}
						/* options for ID3 tag */
					} else if( 0 == a.compareToIgnoreCase("id3v2-utf16") || 0 == a.compareToIgnoreCase("id3v2-ucs2") ) { /* id3v2-ucs2 for compatibility only */
						id3_tenc = TENC_UTF16;
						Jid3tag.id3tag_add_v2( gfp );

					} else if( 0 == a.compareToIgnoreCase("id3v2-latin1") ) {
						id3_tenc = TENC_LATIN1;
						Jid3tag.id3tag_add_v2( gfp );

					} else if( 0 == a.compareToIgnoreCase("tt") ) {
						argUsed = true;
						id3_tag( gfp, 't', id3_tenc, nextArg );

					} else if( 0 == a.compareToIgnoreCase("ta") ) {
						argUsed = true;
						id3_tag( gfp, 'a', id3_tenc, nextArg );

					} else if( 0 == a.compareToIgnoreCase("tl") ) {
						argUsed = true;
						id3_tag( gfp, 'l', id3_tenc, nextArg );

					} else if( 0 == a.compareToIgnoreCase("ty") ) {
						argUsed = true;
						id3_tag( gfp, 'y', id3_tenc, nextArg );

					} else if( 0 == a.compareToIgnoreCase("tc") ) {
						argUsed = true;
						id3_tag( gfp, 'c', id3_tenc, nextArg );

					} else if( 0 == a.compareToIgnoreCase("tn") ) {
						final int ret = id3_tag( gfp, 'n', id3_tenc, nextArg );
						argUsed = true;
						if( ret != 0 ) {
							if( ! ignore_tag_errors ) {
								if( id3tag_mode == ID3TAG_MODE_V1_ONLY ) {
									if( global_ui_config.silent < 9 ) {
										System.err.printf("The track number has to be between 1 and 255 for ID3v1.\n");
									}
									return -1;
								} else if( id3tag_mode == ID3TAG_MODE_V2_ONLY ) {
									/* track will be stored as-is in ID3v2 case, so no problem here */
								} else {
									if( global_ui_config.silent < 9 ) {
										System.err.printf("The track number has to be between 1 and 255 for ID3v1, ignored for ID3v1.\n");
									}
								}
							}
						}

					} else if( 0 == a.compareToIgnoreCase("tg") ) {
						int ret = 0;
						argUsed = true;
						if( nextArg != null && nextArg.length() > 0 ) {
							ret = id3_tag( gfp, 'g', id3_tenc, nextArg );
						}
						if( ret != 0 ) {
							if( ! ignore_tag_errors ) {
								if( ret == -1 ) {
									System.err.printf("Unknown ID3v1 genre number: '%s'.\n", nextArg );
									return -1;
								} else if( ret == -2 ) {
									if( id3tag_mode == ID3TAG_MODE_V1_ONLY ) {
										System.err.printf("Unknown ID3v1 genre: '%s'.\n", nextArg );
										return -1;
									} else if( id3tag_mode == ID3TAG_MODE_V2_ONLY ) {
										/* genre will be stored as-is in ID3v2 case, so no problem here */
									} else {
										if( global_ui_config.silent < 9 ) {
											System.err.printf("Unknown ID3v1 genre: '%s'.  Setting ID3v1 genre to 'Other'\n", nextArg);
										}
									}
								} else {
									if( global_ui_config.silent < 10 ) {
										System.err.printf("Internal error.\n");
									}
									return -1;
								}
							}
						}

					} else if( 0 == a.compareToIgnoreCase("tv") ) {
						argUsed = true;
						if( id3_tag( gfp, 'v', id3_tenc, nextArg ) != 0 ) {
							if( global_ui_config.silent < 9 ) {
								System.err.printf("Invalid field value: '%s'. Ignored\n", nextArg);
							}
						}

					} else if( 0 == a.compareToIgnoreCase("ti") ) {
						argUsed = true;
						if( set_id3_albumart( gfp, nextArg ) != 0 ) {
							if( ! ignore_tag_errors ) {
								return -1;
							}
						}

					} else if( 0 == a.compareToIgnoreCase("ignore-tag-errors") ) {
						ignore_tag_errors = true;

					} else if( 0 == a.compareToIgnoreCase("add-id3v2") ) {
						Jid3tag.id3tag_add_v2( gfp );

					} else if( 0 == a.compareToIgnoreCase("id3v1-only") ) {
						Jid3tag.id3tag_v1_only( gfp );
						id3tag_mode = ID3TAG_MODE_V1_ONLY;

					} else if( 0 == a.compareToIgnoreCase("id3v2-only") ) {
						Jid3tag.id3tag_v2_only( gfp );
						id3tag_mode = ID3TAG_MODE_V2_ONLY;

					} else if( 0 == a.compareToIgnoreCase("space-id3v1") ) {
						Jid3tag.id3tag_space_v1( gfp );

					} else if( 0 == a.compareToIgnoreCase("pad-id3v2") ) {
						Jid3tag.id3tag_pad_v2( gfp );

					} else if( 0 == a.compareToIgnoreCase("pad-id3v2-size") ) {
						try {
							int_value = Integer.parseInt( nextArg );
							int_value = int_value <= 128000 ? int_value : 128000;
							int_value = int_value >= 0      ? int_value : 0;
							Jid3tag.id3tag_set_pad( gfp, int_value );
							argUsed = true;
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("genre-list") ) {
						Jid3tag.id3tag_genre_list( new Jparse() /* genre_list_handler */, null );
						return -2;

					} else if( 0 == a.compareToIgnoreCase("lowpass") ) {
						try {
							double_value = Double.parseDouble( nextArg );
							argUsed = true;
							if( double_value < 0 ) {
								gfp.lame_set_lowpassfreq( -1 );
							} else {
								/* useful are 0.001 kHz...50 kHz, 50 Hz...50000 Hz */
								if( double_value < 0.001 || double_value > 50000. ) {
									System.err.printf("Must specify lowpass with --lowpass freq, freq >= 0.001 kHz\n");
									return -1;
								}
								gfp.lame_set_lowpassfreq( (int) (double_value * (double_value < 50. ? 1.e3 : 1.e0) + 0.5) );
							}
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("lowpass-width") ) {
						try {
							double_value = Double.parseDouble( nextArg );
							argUsed = true;
							/* useful are 0.001 kHz...16 kHz, 16 Hz...50000 Hz */
							if( double_value < 0.001 || double_value > 50000. ) {
								System.err.printf
								("Must specify lowpass width with --lowpass-width freq, freq >= 0.001 kHz\n");
								return -1;
							}
							gfp.lame_set_lowpasswidth( (int) (double_value * (double_value < 16. ? 1.e3 : 1.e0) + 0.5) );
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("highpass") ) {
						try {
							double_value = Double.parseDouble( nextArg );
							argUsed = true;
							if( double_value < 0.0f ) {
								gfp.lame_set_highpassfreq( -1 );
							} else {
								/* useful are 0.001 kHz...16 kHz, 16 Hz...50000 Hz */
								if( double_value < 0.001 || double_value > 50000. ) {
									System.err.printf("Must specify highpass with --highpass freq, freq >= 0.001 kHz\n");
									return -1;
								}
								gfp.lame_set_highpassfreq( (int) (double_value * (double_value < 16. ? 1.e3 : 1.e0) + 0.5) );
							}
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("highpass-width") ) {
						try {
							double_value = Double.parseDouble( nextArg );
							argUsed = true;
							/* useful are 0.001 kHz...16 kHz, 16 Hz...50000 Hz */
							if( double_value < 0.001 || double_value > 50000. ) {
								System.err.printf
								("Must specify highpass width with --highpass-width freq, freq >= 0.001 kHz\n");
								return -1;
							}
							gfp.lame_set_highpasswidth( (int) double_value );
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("comp") ) {
						try {
							double_value = Double.parseDouble( nextArg );
							argUsed = true;
							if( double_value < 1.0 ) {
								System.err.printf("Must specify compression ratio >= 1.0\n");
								return -1;
							}
							gfp.lame_set_compression_ratio( (float)double_value );
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}
					/* some more GNU-ish options could be added
					 * brief         => few messages on screen (name, status report)
					 * o/output file => specifies output filename
					 * O             => stdout
					 * i/input file  => specifies input filename
					 * I             => stdin
					 */
					} else if( 0 == a.compareToIgnoreCase("quiet") ) {
						global_ui_config.silent = 10; /* on a scale from 1 to 10 be very silent */

					} else if( 0 == a.compareToIgnoreCase("silent") ) {
						global_ui_config.silent = 9;

					} else if( 0 == a.compareToIgnoreCase("brief") ) {
						global_ui_config.silent = -5; /* print few info on screen */

					} else if( 0 == a.compareToIgnoreCase("verbose") ) {
						global_ui_config.silent = -10; /* print a lot on screen */

					} else if( 0 == a.compareToIgnoreCase("version") || 0 == a.compareToIgnoreCase("license") ) {
						print_license( System.out );
						return -2;

					} else if( 0 == a.compareToIgnoreCase("help") || 0 == a.compareToIgnoreCase("usage") ) {
						if( 0 == "id3".compareToIgnoreCase( nextArg ) ) {
							help_id3tag( System.out );
						} else if( 0 == "dev".compareToIgnoreCase( nextArg ) ) {
							help_developer_switches( System.out );
						} else {
							short_help( gfp, System.out, ProgramName );
						}
						return -2;

					} else if( 0 == a.compareToIgnoreCase("longhelp") ) {
						long_help( gfp, System.out, ProgramName, false /* lessmode=NO */ );
						return -2;

					} else if( 0 == a.compareToIgnoreCase("?") ) {
						long_help( gfp, System.out, ProgramName, true /* lessmode=YES */ );
						return -2;

					} else if( 0 == a.compareToIgnoreCase("preset") || 0 == a.compareToIgnoreCase("alt-preset") ) {
						argUsed = true;
						{
							boolean fast = false, cbr = false;

							int used_args = 1;
							while( ("fast".compareTo( nextArg ) == 0) || ("cbr".compareTo( nextArg ) == 0) ) {

								if( ("fast".compareTo( nextArg ) == 0) && ! fast ) {
									fast = true;
								}
								if( ("cbr".compareTo( nextArg ) == 0) && ! cbr ) {
									cbr = true;
								}

								used_args++;
								nextArg = i + used_args < args.length ? args[i + used_args] : "";
							}

							if( presets_set( gfp, fast, cbr, nextArg, ProgramName) < 0 ) {
								return -1;
							}
						}

					} else if( 0 == a.compareToIgnoreCase("disptime") ) {
						try {
							global_ui_config.update_interval = Float.parseFloat( nextArg );
							argUsed = true;
						} catch (final NumberFormatException e) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("nogaptags") ) {
						nogap_tags = true;

					} else if( 0 == a.compareToIgnoreCase("nogapout") ) {
						outPath.setLength( 0 );
						outPath.append( nextArg );
						argUsed = true;

					} else if( 0 == a.compareToIgnoreCase("out-dir") ) {
						outDir = nextArg;
						argUsed = true;

					} else if( 0 == a.compareToIgnoreCase("nogap") ) {
						nogap = true;

					} else if( 0 == a.compareToIgnoreCase("swap-channel") ) {
						global_reader.swap_channel = true;
					} else if( 0 == a.compareToIgnoreCase("ignorelength") ) {
						global_reader.ignorewavlength = true;
					} else if( 0 == a.compareToIgnoreCase("athaa-sensitivity") ) {
						try {
							gfp.lame_set_athaa_sensitivity( Float.parseFloat( nextArg ) );
							argUsed = true;
						} catch(final NumberFormatException ne) {
							Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
						}

					} else if( 0 == a.compareToIgnoreCase("debug-file") ) { /* switch for developing, no DOCU */
						argUsed = true; /* file name to print debug info into */
						{
							Jconsole.set_debug_file( nextArg );
						}

					} else {
						if( ! argIgnored ) {
							System.err.printf("%s: unrecognized option --%s\n", ProgramName, a/* token */ );
							return -1;
						}
						argIgnored = false;
					}
					if( argUsed ) {
						i++;
					}

				} else {
					while( token < a.length() ) {
						double double_value = 0;
						final char c = a.charAt( token++ );

						String arg = token < a.length() ? a.substring( token ) : nextArg;
						switch( c ) {
						case 'm':
							argUsed = true;

							switch( arg.charAt( 0 ) ) {
							case 's':
								gfp.lame_set_mode( Jlame.STEREO );
								break;
							case 'd':
								gfp.lame_set_mode( Jlame.DUAL_CHANNEL );
								break;
							case 'f':
								gfp.lame_set_force_ms( true );
								gfp.lame_set_mode( Jlame.JOINT_STEREO );
								break;
							case 'j':
								gfp.lame_set_force_ms( false );
								gfp.lame_set_mode( Jlame.JOINT_STEREO );
								break;
							case 'm':
								gfp.lame_set_mode( Jlame.MONO );
								break;
							case 'l':
								gfp.lame_set_mode( Jlame.MONO );
								gfp.lame_set_scale_left( 2 );
								gfp.lame_set_scale_right( 0 );
								break;
							case 'r':
								gfp.lame_set_mode( Jlame.MONO );
								gfp.lame_set_scale_left( 0 );
								gfp.lame_set_scale_right( 2 );
								break;
							case 'a': /* same as 'j' ??? */
								gfp.lame_set_force_ms( false );
								gfp.lame_set_mode( Jlame.JOINT_STEREO );
								break;
							default:
								System.err.printf("%s: -m mode must be s/d/f/j/m/l/r not %s\n", ProgramName, arg );
								return -1;
							}
							break;

						case 'V':
							try {
								double_value = Double.parseDouble( arg );
								argUsed = true;
								/* to change VBR default look in lame.h */
								if( gfp.lame_get_VBR() == Jlame.vbr_off ) {
									gfp.lame_set_VBR( Jlame.vbr_default );
								}
								gfp.lame_set_VBR_quality( (float)double_value );
							} catch( final NumberFormatException e ) {
								Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
							}
							break;
						case 'v':
							/* to change VBR default look in lame.h */
							if( gfp.lame_get_VBR() == Jlame.vbr_off ) {
								gfp.lame_set_VBR( Jlame.vbr_default );
							}
							break;

						case 'q':
							try {
								gfp.lame_set_quality( Integer.parseInt( arg ) );
								argUsed = true;
							} catch(final NumberFormatException e) {
								Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
							}
							break;
						case 'f':
							gfp.lame_set_quality( 7 );
							break;
						case 'h':
							gfp.lame_set_quality( 2 );
							break;

						case 's':
							try {
								double_value = Double.parseDouble( arg );
								argUsed = true;
								double_value = (int) (double_value * (double_value <= 192 ? 1.e3f : 1.e0) + 0.5);
								global_reader.input_samplerate = (int)double_value;
								gfp.lame_set_in_samplerate( (int)double_value );
							} catch(final NumberFormatException ne) {
								Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
							}
							break;
						case 'b':
							try {
								gfp.lame_set_brate( Integer.parseInt( arg ) );
								argUsed = true;
								gfp.lame_set_VBR_min_bitrate_kbps( gfp.lame_get_brate() );
							} catch(final NumberFormatException ne) {
								Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
							}
							break;
						case 'B':
							try {
								gfp.lame_set_VBR_max_bitrate_kbps( Integer.parseInt( arg ) );
								argUsed = true;
							} catch(final NumberFormatException ne) {
								Jconsole.error_printf("WARNING: argument missing for '%s'\n", a );
							}
							break;
						case 'F':
							gfp.lame_set_VBR_hard_min( true );
							break;
						case 't': /* dont write VBR tag */
							gfp.lame_set_bWriteVbrTag( false );
							global_decoder.disable_wav_header = true;
							break;
						case 'T': /* do write VBR tag */
							gfp.lame_set_bWriteVbrTag( true );
							nogap_tags = true;
							global_decoder.disable_wav_header = false;
							break;
						case 'r': /* force raw pcm input file */
							global_reader.input_format = Jsound_file_format.sf_raw;
							break;
						case 'x': /* force byte swapping */
							global_reader.swapbytes = true;
							break;
						case 'p': /* (jo) error_protection: add crc16 information to stream */
							gfp.lame_set_error_protection( true );
							break;
						case 'a': /* autoconvert input file from stereo to mono - for mono mp3 encoding */
							autoconvert = true;
							gfp.lame_set_mode( Jlame.MONO );
							break;
						case 'd':   /*(void) lame_set_allow_diff_short( gfp, 1 ); */
						case 'k':   /*lame_set_lowpassfreq(gfp, -1);
						  lame_set_highpassfreq(gfp, -1); */
							System.err.printf("WARNING: -%c is obsolete.\n", c );
							break;
						case 'S':
							global_ui_config.silent = 5;
							break;
						case 'X':
							/*  experimental switch -X:
								the differnt types of quant compare are tough
								to communicate to endusers, so they shouldn't
								bother to toy around with them
							 */
						{
							int x = 0, y = 0;
							final String[] ds = arg.split(",");
							int n = 0;
							if( ds.length > 0 ) {
								try {
									x = Integer.parseInt( ds[0] );
									n++;
									if( ds.length > 1 ) {
										y = Integer.parseInt( ds[1] );
										n++;
									}
								} catch(final NumberFormatException ne) {
								}
							}
							if( n == 1 ) {
								y = x;
							}
							argUsed = true;
							if( internal_opts_enabled ) {
								gfp.lame_set_quant_comp( x );
								gfp.lame_set_quant_comp_short( y );
							}
						}
						break;
						case 'Y':
							gfp.lame_set_experimentalY( true );
							break;
						case 'Z':
							/*  experimental switch -Z: */
						{
							int n = 1;
							try {
								n = Integer.parseInt( arg );
								argUsed = true;
							} catch (final NumberFormatException e) {
							}
							/*if( internal_opts_enabled)*/
							{
								gfp.lame_set_experimentalZ( n != 0 );
							}
						}
						break;
						case 'e':
							argUsed = true;

							switch( arg.charAt( 0 ) ) {
							case 'n':
								gfp.lame_set_emphasis( 0 );
								break;
							case '5':
								gfp.lame_set_emphasis( 1 );
								break;
							case 'c':
								gfp.lame_set_emphasis( 3 );
								break;
							default:
								System.err.printf("%s: -e emp must be n/5/c not %s\n", ProgramName, arg );
								return -1;
							}
							break;
						case 'c':
							gfp.lame_set_copyright( true );
							break;
						case 'o':
							gfp.lame_set_original( false );
							break;

						case '?':
							long_help( gfp, System.out, ProgramName, false /* LESSMODE=NO */ );
							return -1;

						default:
							System.err.printf("%s: unrecognized option -%c\n", ProgramName, c );
							return -1;
						}
						if( argUsed ) {
							if( token < a.length() && arg.charAt( 0 ) == a.charAt( token ) ) {
								token = a.length();
							} else {
								++i;
							} /* skip arg we used */
							arg = "";
							argUsed = false;
						}
					}
				}
			} else {
				if( nogap ) {
					if( (num_nogap != null) && (count_nogap < num_nogap[0]) ) {
						nogap_inPath[count_nogap++] = args[i];
						input_file = true;
					} else {
						/* sorry, calling program did not allocate enough space */
						System.err.printf("Error: 'nogap option'.  Calling program does not allow nogap option, or\n" +
								"you have exceeded maximum number of input files for the nogap option\n");
						if( num_nogap != null ) {
							num_nogap[0] = -1;// FIXME potential null access
						}
						return -1;
					}
				} else {
					/* normal options:   inputfile  [outputfile], and
					   either one can be a '-' for stdin/stdout */
					if( inPath.length() == 0 ) {
						inPath.append( args[i] );
						input_file = true;
					}
					else {
						if( outPath.length() == 0 ) {
							outPath.append( args[i] );
						} else {
							System.err.printf("%s: excess arg %s\n", ProgramName, args[i] );
							return -1;
						}
					}
				}
			}
		}                   /* loop over args */

		if( ! input_file ) {
			usage( System.out, ProgramName  );
			return -1;
		}

		if( inPath.charAt( 0 ) == '-' ) {
			global_ui_config.silent = (global_ui_config.silent <= 1 ? 1 : global_ui_config.silent );
		}

		if( gfp.lame_get_decode_only() && count_nogap > 0 ) {
			Jconsole.error_printf("combination of nogap and decode not supported!\n");
			return -1;
		}

		if( inPath.charAt( 0 ) == '-' ) {
			if( global_ui_config.silent == 0 ) { /* user didn't overrule default behaviour */
				global_ui_config.silent = 1;
			}
		}

		if( outPath.length() == 0 ) { /* no explicit output dir or file */
			if( count_nogap > 0 ) { /* in case of nogap encode */
				outPath.setLength( 0 );
				outPath.append( outDir ); /* whatever someone set via --out-dir <path> argument */
			}
			else if( inPath.charAt( 0 ) == '-' ) {
				/* if input is stdin, default output is stdout */
				outPath.setLength( 0 );
				outPath.append('-');
			} else {
				final String s_ext = gfp.lame_get_decode_only() ? ".wav" : ".mp3";
				final int i = inPath.lastIndexOf(".");
				if( i >= 0 ) {
					outPath.setLength( 0 );
					if( outDir.endsWith("\\") || outDir.endsWith("/") ) {
						outDir = outDir.substring( 0, outDir.length() - 1 );
					}
					outPath.append( outDir ).append( File.pathSeparatorChar ).append( inPath.substring( 0, i ) ).append( s_ext );
				}
			}
		}

		/* RG is enabled by default */
		if( ! noreplaygain ) {
			gfp.lame_set_findReplayGain( true );
		}

		/* disable VBR tags with nogap unless the VBR tags are forced */
		if( nogap && gfp.lame_get_bWriteVbrTag() && ! nogap_tags ) {
			Jconsole.console_printf("Note: Disabling VBR Xing/Info tag since it interferes with --nogap\n");
			gfp.lame_set_bWriteVbrTag( false );
		}

		/* some file options not allowed with stdout */
		if( outPath.charAt( 0 ) == '-' ) {
			gfp.lame_set_bWriteVbrTag( false ); /* turn off VBR tag */
		}

		/* if user did not explicitly specify input is mp3, check file name */
		if( global_reader.input_format == Jsound_file_format.sf_unknown ) {
			global_reader.input_format = filename_to_type( inPath.toString() );
		}

		/* default guess for number of channels */
		if( autoconvert ) {
			gfp.lame_set_num_channels( 2 );
		} else if( Jlame.MONO == gfp.lame_get_mode() ) {
			gfp.lame_set_num_channels( 1 );
		} else {
			gfp.lame_set_num_channels( 2 );
		}

		if( gfp.lame_get_free_format() ) {
			if( gfp.lame_get_brate() < 8 || gfp.lame_get_brate() > 640 ) {
				System.err.printf("For free format, specify a bitrate between 8 and 640 kbps\n");
				System.err.printf("with the -b <bitrate> option\n");
				return -1;
			}
		}
		if( num_nogap != null ) {
			num_nogap[0] = count_nogap;
		}
		return 0;
	}
}