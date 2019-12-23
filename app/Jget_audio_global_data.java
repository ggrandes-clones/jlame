package app;

import java.io.RandomAccessFile;

import libmp3lame.Jhip;

/** global data for get_audio.c. */
final class Jget_audio_global_data {
	boolean count_samples_carefully;
	int     pcmbitwidth;
	boolean pcmswapbytes;
	boolean pcm_is_unsigned_8bit;
	boolean pcm_is_ieee_float;
	long    num_samples_read;
	RandomAccessFile music_in;
	// SNDFILE *snd_file;// for libsnd
	Jhip    hip = new Jhip();
	JPcmBuffer pcm32 = new JPcmBuffer();
	JPcmBuffer pcm16 = new JPcmBuffer();
	// int     in_id3v2_size;
	byte[]  in_id3v2_tag;// java: in_id3v2_size = in_id3v2_tag.length
}
