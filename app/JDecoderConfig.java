package app;

import libmp3lame.Jmp3data_struct;

final class JDecoderConfig {
	/** to adjust the number of samples truncated during decode */
	int mp3_delay;
	/** user specified the value of the mp3 encoder delay to assume for decoding */
	boolean mp3_delay_set;
	boolean disable_wav_header;
	final Jmp3data_struct mp3input_data = new Jmp3data_struct();
}
