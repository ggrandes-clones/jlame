package app;

import java.util.Arrays;

import libmp3lame.Jlame;
import libmp3lame.Jlame_global_flags;

/*
 *	Bitrate histogram source file
 *
 *	Copyright (c) 2000 Mark Taylor
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

/* $Id: brhist.c,v 1.58 2013/06/11 08:41:31 rbrito Exp $ */

// brhist.c

final class Jbrhist {

	/* basic #define's */

	private static final int BRHIST_WIDTH = 14;
	// private static final int BRHIST_RES   = 14;

	/* Structure holding all data related to the Console I/O
	 * may be this should be a more global frontend structure. So it
	 * makes sense to print all files instead with
	 * printf ( "blah\n") with printf ( "blah%s\n", Console_IO.str_clreoln );
	 */

	private static final class Jbrhist_struct {
		private int    vbr_bitrate_min_index;
		private int    vbr_bitrate_max_index;
		private final int kbps[] = new int[BRHIST_WIDTH];
		private int    hist_printed_lines;
		private String bar_asterisk;
		private String bar_percent;
		private String bar_coded;
		private String bar_space;
	}

	private static final Jbrhist_struct brhist = new Jbrhist_struct( );

	private static final int calculate_index(final int[] array, final int len, final int value) {
		for( int i = 0; i < len; i++ ) {
			if( array[i] == value ) {
				return i;
			}
		}
		return -1;
	}

	@SuppressWarnings("boxing")
	static final int brhist_init(final Jlame_global_flags gf, final int bitrate_kbps_min, final int bitrate_kbps_max ) {
		brhist.hist_printed_lines = 0;

		/* initialize histogramming data structure */
		Jlame.lame_bitrate_kbps( gf, brhist.kbps );
		brhist.vbr_bitrate_min_index = calculate_index( brhist.kbps, BRHIST_WIDTH, bitrate_kbps_min );
		brhist.vbr_bitrate_max_index = calculate_index( brhist.kbps, BRHIST_WIDTH, bitrate_kbps_max );

		if( brhist.vbr_bitrate_min_index >= BRHIST_WIDTH ||
				brhist.vbr_bitrate_max_index >= BRHIST_WIDTH ) {
			Jconsole.error_printf("lame internal error: VBR min %d kbps or VBR max %d kbps not allowed.\n",
					bitrate_kbps_min, bitrate_kbps_max );
			return -1;
		}

		final char[] tmp = new char[512];
		Arrays.fill( tmp, '*' );
		brhist.bar_asterisk = new String( tmp );//memset(brhist.bar_asterisk, '*', sizeof(brhist.bar_asterisk) - 1 );
		Arrays.fill( tmp, '#' );// java: replced by # to avoid a problem with printf. Arrays.fill( tmp, '%' );
		brhist.bar_percent = new String( tmp );//memset(brhist.bar_percent, '%', sizeof(brhist.bar_percent) - 1 );
		Arrays.fill( tmp, '-' );
		brhist.bar_coded = new String( tmp );//memset(brhist.bar_space, '-', sizeof(brhist.bar_space) - 1 );
		brhist.bar_space = brhist.bar_coded;//memset(brhist.bar_coded, '-', sizeof(brhist.bar_space) - 1 );

		return 0;
	}

	private static final int digits(int number) {
		int ret = 1;

		if( number >= 100000000 ) {
			ret += 8;
			number /= 100000000;
		}
		if( number >= 10000 ) {
			ret += 4;
			number /= 10000;
		}
		if( number >= 100 ) {
			ret += 2;
			number /= 100;
		}
		if( number >= 10 ) {
			ret += 1;
		}

		return ret;
	}

	@SuppressWarnings("boxing")
	private static final void brhist_disp_line(final int i, final int br_hist_TOT, final int br_hist_LR, final int full, final int frames)
	{
		int res = digits( frames ) + 3 + 4 + 1;

		int barlen_TOT, barlen_LR;
		if( full != 0 ) {
			/* some problems when br_hist_TOT \approx br_hist_LR: You can't see that there are still MS frames */
			barlen_TOT = (br_hist_TOT * (Jconsole.Console_IO.disp_width - res) + full - 1) / full; /* round up */
			barlen_LR = (br_hist_LR * (Jconsole.Console_IO.disp_width - res) + full - 1) / full; /* round up */
		} else {
			barlen_TOT = barlen_LR = 0;
		}

		final String format = " [%" + digits( frames ) + "d]";
		final String brppt = String.format( format, br_hist_TOT );/* [%] and max. 10 characters for kbps */

		if( Jconsole.Console_IO.str_clreoln.length() != 0 ) {/* ClearEndOfLine available */
			/* console_printf("\n%3d%s %.*s%.*s%s",
					brhist.kbps[i], brppt,
					barlen_LR, brhist.bar_percent,
					barlen_TOT - barlen_LR, brhist.bar_asterisk, Console_IO.str_clreoln); */
			final StringBuilder sb = new StringBuilder( String.format( "\n%3d%s ", brhist.kbps[i], brppt ) );
			if( barlen_LR != 0 ) {
				sb.append( String.format( "%." + barlen_LR + "s", brhist.bar_percent ) );
			}
			barlen_TOT -= barlen_LR;
			if( barlen_TOT != 0 ) {
				sb.append( String.format( "%." + barlen_TOT + "s", brhist.bar_asterisk ) );
			}
			sb.append( Jconsole.Console_IO.str_clreoln );
			Jconsole.console_printf( sb.toString() );
		} else {
			/* console_printf("\n%3d%s %.*s%.*s%*s",
					brhist.kbps[i], brppt,
					barlen_LR, brhist.bar_percent,
					barlen_TOT - barlen_LR, brhist.bar_asterisk,
					Console_IO.disp_width - res - barlen_TOT, ""); */
			final StringBuilder sb = new StringBuilder( String.format( "\n%3d%s ", brhist.kbps[i], brppt ) );
			if( barlen_LR != 0 ) {
				sb.append( String.format( "%." + barlen_LR + "s", brhist.bar_percent ) );
			}
			barlen_LR = barlen_TOT - barlen_LR;
			if( barlen_LR != 0 ) {
				sb.append( String.format( "%." + barlen_LR + "s", brhist.bar_asterisk ) );
			}
			res = Jconsole.Console_IO.disp_width - res - barlen_TOT;
			if( res != 0 ) {
				sb.append( String.format( "%." + res + "s", "" ) );
			}
			Jconsole.console_printf( sb.toString() );
		}

		brhist.hist_printed_lines++;
	}

	@SuppressWarnings("boxing")
	private static final void progress_line(final Jlame_global_flags gf, int full, final int frames) {
		String rst = "";
		int barlen_TOT = 0, barlen_COD = 0, barlen_RST = 0;
		int res = 1;
		float time_in_sec = 0;
		final int fsize = gf.lame_get_framesize();
		final int srate = gf.lame_get_out_samplerate();

		if( full < frames ) {
			full = frames;
		}
		if( srate > 0 ) {
			time_in_sec = (float)(full - frames);
			time_in_sec *= fsize;
			time_in_sec /= srate;
		}
		final int hour = (int)(time_in_sec / 3600);
		time_in_sec -= hour * 3600;
		final int min = (int)(time_in_sec / 60);
		time_in_sec -= min * 60;
		final int sec = (int)time_in_sec;
		if( full != 0 ) {
			if( hour > 0 ) {
				final String format = "%" + digits( hour ) + "d:%02d:%02d";
				rst = String.format( format, hour, min, sec );
				res += digits( hour ) + 1 + 5;
			} else {
				rst = String.format( "%02d:%02d", min, sec );
				res += 5;
			}
			/* some problems when br_hist_TOT \approx br_hist_LR: You can't see that there are still MS frames */
			barlen_TOT = (full * (Jconsole.Console_IO.disp_width - res) + full - 1) / full; /* round up */
			barlen_COD = (frames * (Jconsole.Console_IO.disp_width - res) + full - 1) / full; /* round up */
			barlen_RST = barlen_TOT - barlen_COD;
			if( barlen_RST == 0 ) {
				final String format = "%." + ( res - 1 ) + "s";
				rst = String.format( format, brhist.bar_coded );
			}
		} else {
			barlen_TOT = barlen_COD = barlen_RST = 0;
		}
		// java prints don't supports %.0s
		if( Jconsole.Console_IO.str_clreoln.length() != 0 ) { /* ClearEndOfLine available */
			/* console_printf("\n%.*s%s%.*s%s",
					barlen_COD, brhist.bar_coded,
					rst, barlen_RST, brhist.bar_space, Console_IO.str_clreoln); */
			final StringBuilder sb = new StringBuilder("\n");
			if( barlen_COD != 0 ) {
				sb.append( String.format( "%." + barlen_COD + "s", brhist.bar_coded ) );
			}
			sb.append( rst );
			if( barlen_RST != 0 ) {
				sb.append( String.format( "%." + barlen_RST + "s", brhist.bar_space ) );
			}
			sb.append( Jconsole.Console_IO.str_clreoln );
			Jconsole.console_printf( sb.toString() );
		} else {
			/* console_printf("\n%.*s%s%.*s%*s",
					barlen_COD, brhist.bar_coded,
					rst, barlen_RST, brhist.bar_space, Console_IO.disp_width - res - barlen_TOT,
					""); */
			final StringBuilder sb = new StringBuilder("\n");
			if( barlen_COD != 0 ) {
				sb.append( String.format( "%." + barlen_COD + "s", brhist.bar_coded ) );
			}
			sb.append( rst );
			if( barlen_RST != 0 ) {
				sb.append( String.format( "%." + barlen_RST + "s", brhist.bar_space ) );
			}
			res = (Jconsole.Console_IO.disp_width - res - barlen_TOT);
			if( res != 0 ) {
				sb.append( String.format( "%." + res + "s", "" ) );
			}
			Jconsole.console_printf( sb.toString() );
		}
		brhist.hist_printed_lines++;
	}


	@SuppressWarnings("boxing")
	private static final int stats_value(final double x) {
		if( x > 0.0 ) {
			Jconsole.console_printf(" %5.1f", x );
			return 6;
		}
		return 0;
	}

	private static final int stats_head(final double x, final String txt) {
		if( x > 0.0 ) {
			Jconsole.console_printf( txt );
			return 6;
		}
		return 0;
	}


	@SuppressWarnings("boxing")
	private static final void stats_line(final double[] stat) {
		int n = 1;
		Jconsole.console_printf("\n   kbps     " );
		n += 12;
		n += stats_head( stat[1], "  mono" );
		n += stats_head( stat[2], "   IS " );
		n += stats_head( stat[3], "   LR " );
		n += stats_head( stat[4], "   MS " );
		Jconsole.console_printf(" %%    " );
		n += 6;
		n += stats_head( stat[5], " long " );
		n += stats_head( stat[6], "switch" );
		n += stats_head( stat[7], " short" );
		n += stats_head( stat[8], " mixed" );
		Jconsole.console_printf(" %%" );
		n += 2;
		if( Jconsole.Console_IO.str_clreoln.length() != 0 ) { /* ClearEndOfLine available */
			Jconsole.console_printf("%s", Jconsole.Console_IO.str_clreoln );
		} else {
			final String format = "%" + (Jconsole.Console_IO.disp_width - n) + "s";
			Jconsole.console_printf( format, "" );
		}
		brhist.hist_printed_lines++;

		n = 1;
		Jconsole.console_printf("\n  %5.1f     ", stat[0] );
		n += 12;
		n += stats_value( stat[1] );
		n += stats_value( stat[2] );
		n += stats_value( stat[3] );
		n += stats_value( stat[4] );
		Jconsole.console_printf("      ");
		n += 6;
		n += stats_value( stat[5] );
		n += stats_value( stat[6] );
		n += stats_value( stat[7] );
		n += stats_value( stat[8] );
		if( Jconsole.Console_IO.str_clreoln.length() != 0 ) { /* ClearEndOfLine available */
			Jconsole.console_printf("%s", Jconsole.Console_IO.str_clreoln );
		} else {
			final String format = "%" + (Jconsole.Console_IO.disp_width - n) + "s";
			Jconsole.console_printf( format, "" );
		}
		brhist.hist_printed_lines++;
	}


	/* Yes, not very good */
	private static final int LR = 0;
	private static final int MS = 2;

	static final void brhist_disp(final Jlame_global_flags gf) {
		int lines_used = 0;
		final int br_hist[] = new int[BRHIST_WIDTH]; /* how often a frame size was used */
		final int br_sm_hist[][] = new int[BRHIST_WIDTH][4]; /* how often a special frame size/stereo mode commbination was used */
		final int st_mode[] = new int[4];
		final int bl_type[] = new int[6];
		double  sum = 0.;

		final double  stat[] = new double[9];// = { 0 };// java: already zeroed
		int st_frames = 0;

		brhist.hist_printed_lines = 0; /* printed number of lines for the brhist functionality, used to skip back the right number of lines */

		Jlame.lame_bitrate_stereo_mode_hist( gf, br_sm_hist );
		Jlame.lame_bitrate_hist( gf, br_hist );
		Jlame.lame_stereo_mode_hist( gf, st_mode );
		Jlame.lame_block_type_hist( gf, bl_type );
		int frames = 0;/* total number of encoded frames */
		int most_often = 0;/* usage count of the most often used frame size, but not smaller than Console_IO.disp_width-BRHIST_RES (makes this sense?) and 1 */
		for( int i = 0; i < BRHIST_WIDTH; i++ ) {
			frames += br_hist[i];
			sum += br_hist[i] * brhist.kbps[i];
			if( most_often < br_hist[i]) {
				most_often = br_hist[i];
			}
			if( br_hist[i] != 0 ) {
				++lines_used;
			}
		}

		for( int i = 0; i < BRHIST_WIDTH; i++ ) {
			boolean show = br_hist[i] != 0;
			show = show && (lines_used > 1 );
			if( show || (i >= brhist.vbr_bitrate_min_index && i <= brhist.vbr_bitrate_max_index) ) {
				brhist_disp_line( i, br_hist[i], br_sm_hist[i][LR], most_often, frames );
			}
		}
		for( int i = 0; i < 4; i++ ) {
			st_frames += st_mode[i];
		}
		if( frames > 0 ) {
			stat[0] = sum / frames;
			stat[1] = 100. * (frames - st_frames) / frames;
		}
		if( st_frames > 0 ) {
			stat[2] = 0.0;
			stat[3] = 100. * st_mode[LR] / st_frames;
			stat[4] = 100. * st_mode[MS] / st_frames;
		}
		if( bl_type[5] > 0 ) {
			stat[5] = 100. * bl_type[0] / bl_type[5];
			stat[6] = 100. * (bl_type[1] + bl_type[3]) / bl_type[5];
			stat[7] = 100. * bl_type[2] / bl_type[5];
			stat[8] = 100. * bl_type[4] / bl_type[5];
		}
		progress_line( gf, gf.lame_get_totalframes(), frames );
		stats_line( stat );
	}

	static final void brhist_jump_back() {
		Jconsole.console_up( brhist.hist_printed_lines );
		brhist.hist_printed_lines = 0;
	}

/*
 * 1)
 *
 * Taken from Termcap_Manual.html:
 *
 * With the Unix version of termcap, you must allocate space for the description yourself and pass
 * the address of the space as the argument buffer. There is no way you can tell how much space is
 * needed, so the convention is to allocate a buffer 2048 characters long and assume that is
 * enough.  (Formerly the convention was to allocate 1024 characters and assume that was enough.
 * But one day, for one kind of terminal, that was not enough.)
 */


}