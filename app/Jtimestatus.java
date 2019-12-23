package app;

import libmp3lame.Jlame;
import libmp3lame.Jlame_global_flags;
import libmp3lame.Jmp3data_struct;

/*
 *      time status related function source file
 *
 *      Copyright (c) 1999 Mark Taylor
 *                    2010 Robert Hegemann
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

/* $Id: timestatus.c,v 1.60 2011/05/07 16:05:17 rbrito Exp $ */

// timestatus.c

final class Jtimestatus {

	private static final String SPEED_CHAR = "x"; /* character x */
	private static final double SPEED_MULT = 1.;

	// typedef struct time_status_struct {
	/** result of last call to clock */
	private double last_time;
	/** total time */
	private double elapsed_time;
	/** estimated total duration time [s] */
	private double estimated_time;
	/** speed relative to realtime coding [100%] */
	private double speed_index;
	// } timestatus_t;

	private static final JEncoderProgress global_encoder_progress = new JEncoderProgress();

	/**
	 *  Calculates from the input (see below) the following values:
	 *    - total estimated time
	 *    - a speed index
	 *
	 * @param tstime tstime.elapsed_time: elapsed time
	 * @param sample_freq sample frequency [Hz/kHz]
	 * @param frameNum Number of the current Frame
	 * @param totalframes total number of Frames
	 * @param framesize Size of a frame [bps/kbps]
	 */
	private static final void ts_calc_times(final Jtimestatus tstime, final int sample_freq, final int frameNum, final int totalframes, final int framesize ) {
		if( frameNum > 0 && tstime.elapsed_time > 0 ) {
			tstime.estimated_time = tstime.elapsed_time * totalframes / frameNum;
			tstime.speed_index = framesize * frameNum / (sample_freq * tstime.elapsed_time );
		} else {
			tstime.estimated_time = 0.;
			tstime.speed_index = 0.;
		}
	}

	/* Decomposes a given number of seconds into a easy to read hh:mm:ss format
	 * padded with an additional character
	 */
	@SuppressWarnings("boxing")
	private static final void ts_time_decompose(final double x, final char padded_char ) {
		final long time_in_sec = (long)x;
		final long hour = time_in_sec / 3600;
		final int min = (int)(time_in_sec / 60 % 60);
		final int sec = (int)(time_in_sec % 60);

		if( hour == 0  ) {
			Jconsole.console_printf("   %2d:%02d%c", min, sec, padded_char );
		} else if( hour < 100  ) {
			Jconsole.console_printf("%2d:%02d:%02d%c", hour, min, sec, padded_char );
		} else {
			Jconsole.console_printf("%6d h%c", hour, padded_char );
		}
	}

	@SuppressWarnings("boxing")
	private static final void timestatus(final Jlame_global_flags gfp ) {
		final Jtimestatus real_time = global_encoder_progress.real_time;
		final Jtimestatus proc_time = global_encoder_progress.proc_time;
		final int samp_rate = gfp.lame_get_out_samplerate();
		final int frameNum = gfp.lame_get_frameNum();
		int totalframes = gfp.lame_get_totalframes();
		final int framesize = gfp.lame_get_framesize();

		if( totalframes < frameNum ) {
			totalframes = frameNum;
		}
		if( ! global_encoder_progress.time_status_init ) {
			real_time.last_time = System.currentTimeMillis() / 1000.;// GetRealTime();
			proc_time.last_time = System.currentTimeMillis() / 1000.;// GetCPUTime();
			real_time.elapsed_time = 0;
			proc_time.elapsed_time = 0;
		}

		/* we need rollover protection for GetCPUTime, and maybe GetRealTime(): */
		double tmx = System.currentTimeMillis() / 1000.;// GetRealTime();
		double delta = tmx - real_time.last_time;
		if( delta < 0 ) {
			delta = 0;
		}      /* ignore, clock has rolled over */
		real_time.elapsed_time += delta;
		real_time.last_time = tmx;


		tmx = System.currentTimeMillis() / 1000.;// GetCPUTime();
		delta = tmx - proc_time.last_time;
		if( delta < 0 ) {
			delta = 0;
		}      /* ignore, clock has rolled over */
		proc_time.elapsed_time += delta;
		proc_time.last_time = tmx;

		if( ! global_encoder_progress.time_status_init ) {
			Jconsole.console_printf("\r" +
				"    Frame          |  CPU time/estim | REAL time/estim | play/CPU |    ETA \n" +
				"     0/       ( 0%%)|    0:00/     :  |    0:00/     :  |         " +
				SPEED_CHAR + "|     :  \r"
				/* , Console_IO.str_clreoln, Console_IO.str_clreoln */  );
			global_encoder_progress.time_status_init = true;
			return;
		}

		ts_calc_times( real_time, samp_rate, frameNum, totalframes, framesize );
		ts_calc_times( proc_time, samp_rate, frameNum, totalframes, framesize );

		int percent;
		if( frameNum < totalframes ) {
			percent = (int) (100. * frameNum / totalframes + 0.5 );
		} else {
			percent = 100;
		}

		Jconsole.console_printf("\r%6d/%-6d", frameNum, totalframes );
		//Jconsole.console_printf( percent < 100 ? " (%2d%%)|" : "(%3.3d%%)|", percent );// FIXME using %3.3 for integer
		Jconsole.console_printf( percent < 100 ? " (%2d%%)|" : "(%3d%%)|", percent );
		ts_time_decompose( proc_time.elapsed_time, '/' );
		ts_time_decompose( proc_time.estimated_time, '|' );
		ts_time_decompose( real_time.elapsed_time, '/' );
		ts_time_decompose( real_time.estimated_time, '|' );
		Jconsole.console_printf( proc_time.speed_index <= 1. ?
				"%9.4f" + SPEED_CHAR + "|" : "%9.5g" + SPEED_CHAR + "|",
				SPEED_MULT * proc_time.speed_index );
		ts_time_decompose( (real_time.estimated_time - real_time.elapsed_time), ' ' );
	}

	private static final void timestatus_finish() {
		Jconsole.console_printf("\n");
	}

	private static final void brhist_init_package(final Jlame_global_flags gf) {
		if( Jparse.global_ui_config.brhist ) {
			if( Jbrhist.brhist_init( gf, gf.lame_get_VBR_min_bitrate_kbps(), gf.lame_get_VBR_max_bitrate_kbps() ) != 0 ) {
				/* fail to initialize */
				Jparse.global_ui_config.brhist = false;
			}
		} else {
			Jbrhist.brhist_init( gf, 128, 128 ); /* Dirty hack */
		}
	}

	@SuppressWarnings("boxing")
	static final void encoder_progress_begin(final Jlame_global_flags gf, final String inPath, final String outPath ) {
		brhist_init_package( gf );
		global_encoder_progress.time_status_init = false;
		global_encoder_progress.last_time = 0;
		global_encoder_progress.last_frame_num = 0;
		if( Jparse.global_ui_config.silent < 9 ) {
			Jlame.lame_print_config( gf ); /* print useful information about options being used */

			Jconsole.console_printf("Encoding %s%s to %s\n",
						! inPath.equals("-") ? inPath : "<stdin>",
						(inPath.length() + outPath.length()) < 66 ? "" : "\n     ",
						! outPath.equals("-") ? outPath : "<stdout>");

			Jconsole.console_printf("Encoding as %g kHz ", 1.e-3 * gf.lame_get_out_samplerate() );

			{
				final String mode_names[][] = {// [2][4] = {
						{"stereo", "j-stereo", "dual-ch", "single-ch"},
						{"stereo", "force-ms", "dual-ch", "single-ch"}
					};
				switch( gf.lame_get_VBR() ) {
				case Jlame.vbr_rh:
					Jconsole.console_printf("%s MPEG-%d%s Layer III VBR(q=%g) qval=%d\n",
								mode_names[ gf.lame_get_force_ms() ? 1 : 0 ][ gf.lame_get_mode() ],
								2 - gf.lame_get_version(),
								gf.lame_get_out_samplerate() < 16000 ? ".5" : "",
								gf.lame_get_VBR_quality(),
								gf.lame_get_quality() );
					break;
				case Jlame.vbr_mt:
				case Jlame.vbr_mtrh:
					Jconsole.console_printf("%s MPEG-%d%s Layer III VBR(q=%g)\n",
								mode_names[ gf.lame_get_force_ms() ? 1 : 0 ][ gf.lame_get_mode() ],
								2 - gf.lame_get_version(),
								gf.lame_get_out_samplerate() < 16000 ? ".5" : "",
								gf.lame_get_VBR_quality() );
					break;
				case Jlame.vbr_abr:
					Jconsole.console_printf("%s MPEG-%d%s Layer III (%gx) average %d kbps qval=%d\n",
								mode_names[ gf.lame_get_force_ms() ? 1 : 0 ][ gf.lame_get_mode() ],
								2 - gf.lame_get_version(),
								gf.lame_get_out_samplerate() < 16000 ? ".5" : "",
								0.1 * (int) (10. * gf.lame_get_compression_ratio() + 0.5),
								gf.lame_get_VBR_mean_bitrate_kbps(),
								gf.lame_get_quality() );
					break;
				default:
					Jconsole.console_printf("%s MPEG-%d%s Layer III (%gx) %3d kbps qval=%d\n",
								mode_names[ gf.lame_get_force_ms() ? 1 : 0 ][ gf.lame_get_mode() ],
								2 - gf.lame_get_version(),
								gf.lame_get_out_samplerate() < 16000 ? ".5" : "",
								0.1 * (int) (10. * gf.lame_get_compression_ratio() + 0.5),
								gf.lame_get_brate(),
								gf.lame_get_quality() );
					break;
				}
			}

			if( Jparse.global_ui_config.silent <= -10 ) {
				Jlame.lame_print_internals( gf );
			}
		}
	}

	static final void encoder_progress(final Jlame_global_flags gf ) {
		if( Jparse.global_ui_config.silent <= 0  ) {
			final int frames = gf.lame_get_frameNum();
			final int frames_diff = frames - global_encoder_progress.last_frame_num;
			if( Jparse.global_ui_config.update_interval <= 0  ) {     /*  most likely --disptime x not used */
				if( frames_diff < 100 && frames_diff != 0  ) {  /*  true, most of the time */
					return;
				}
				global_encoder_progress.last_frame_num = (frames / 100) * 100;
			} else {
				if( frames != 0 && frames != 9 ) {
					final double act = System.currentTimeMillis() / 1000.;// GetRealTime();
					final double dif = act - global_encoder_progress.last_time;
					if( dif >= 0 && dif < Jparse.global_ui_config.update_interval  ) {
						return;
					}
				}
				global_encoder_progress.last_time = System.currentTimeMillis() / 1000.;// GetRealTime(); /* from now! disp_time seconds */
			}
			if( Jparse.global_ui_config.brhist ) {
				Jbrhist.brhist_jump_back();
			}
			timestatus( gf );
			if( Jparse.global_ui_config.brhist ) {
				Jbrhist.brhist_disp( gf );
			}
			Jconsole.console_flush();
		}
	}

	static final void encoder_progress_end(final Jlame_global_flags gf ) {
		if( Jparse.global_ui_config.silent <= 0 ) {
			if( Jparse.global_ui_config.brhist ) {
				Jbrhist.brhist_jump_back();
			}
			Jtimestatus.timestatus( gf );
			if( Jparse.global_ui_config.brhist ) {
				Jbrhist.brhist_disp( gf );
			}
			Jtimestatus.timestatus_finish();
		}
	}

	/* these functions are used in get_audio.c */
	private static final JDecoderProgress global_decoder_progress = new JDecoderProgress();

	private static final int calcEndPadding(long samples, final int pcm_samples_per_frame) {
		samples += 576;
		long end_padding = pcm_samples_per_frame - (samples % pcm_samples_per_frame );
		if( end_padding < 576 ) {
			end_padding += pcm_samples_per_frame;
		}
		return (int)end_padding;
	}

	private static final int calcNumBlocks(long samples, final int pcm_samples_per_frame) {
		samples += 576;
		long end_padding = pcm_samples_per_frame - (samples % pcm_samples_per_frame );
		if( end_padding < 576 ) {
			end_padding += pcm_samples_per_frame;
		}
		return (int)((samples + end_padding) / pcm_samples_per_frame);
	}

	static final JDecoderProgress decoder_progress_init(final long n, final int framesize) {
		final JDecoderProgress dp = global_decoder_progress;
		dp.last_mode_ext = 0;
		dp.frames_total = 0;
		dp.frame_ctr = 0;
		dp.framesize = framesize;
		dp.samples = 0;
		if( n < 0xffffffffL ) {
			if( framesize == 576 || framesize == 1152 ) {
				dp.frames_total = calcNumBlocks( n, framesize );
				dp.samples = 576 + calcEndPadding( n, framesize );
			} else if( framesize > 0 ) {
				dp.frames_total = (int)(n / framesize);
			} else {
				dp.frames_total = (int)n;
			}
		}
		return dp;
	}

	private static final void addSamples(final JDecoderProgress dp, final int iread) {
		dp.samples += iread;
		dp.frame_ctr += dp.samples / dp.framesize;
		dp.samples %= dp.framesize;
		if( dp.frames_total < dp.frame_ctr ) {
			dp.frames_total = dp.frame_ctr;
		}
	}

	@SuppressWarnings("boxing")
	static final void decoder_progress(final JDecoderProgress dp, final Jmp3data_struct mp3data, final int iread) {
		addSamples( dp, iread );

		Jconsole.console_printf("\rFrame#%6d/%-6d %3d kbps", dp.frame_ctr, dp.frames_total, mp3data.bitrate );

		/* Programmed with a single frame hold delay */
		/* Attention: static data */

		/* MP2 Playback is still buggy. */
		/* "'00' subbands 4-31 in intensity_stereo, bound==4" */
		/* is this really intensity_stereo or is it MS stereo? */

		if( mp3data.mode == Jlame.JOINT_STEREO ) {
			final int curr = mp3data.mode_ext;
			final int last = dp.last_mode_ext;
			Jconsole.console_printf("  %s  %c",
						(curr & 2) != 0 ? (last & 2) != 0 ? " MS " : "LMSR" : (last & 2) != 0 ? "LMSR" : "L  R",
						(curr & 1) != 0 ? (last & 1) != 0 ? 'I' : 'i' : (last & 1) != 0 ? 'i' : ' ' );
			dp.last_mode_ext = curr;
		} else {
			Jconsole.console_printf("         ");
			dp.last_mode_ext = 0;
		}
		/*    console_printf ("%s", Console_IO.str_clreoln  ); */
		Jconsole.console_printf("        \b\b\b\b\b\b\b\b");
		Jconsole.console_flush();
	}

	static final void decoder_progress_finish(final JDecoderProgress dp ) {
		Jconsole.console_printf("\n");
	}
}