package app;

final class JReaderConfig {
	int input_format;
	/** force byte swapping   default=0 */
	boolean swapbytes;
	/** 0: no-op, 1: swaps input channels */
	boolean swap_channel;
	int   input_samplerate;
	boolean ignorewavlength;
	//
	public JReaderConfig(final int format, final boolean isSwapBytes, final boolean isSwapChannels, final int samplerate, final boolean isIgnoreWavLength) {
		input_format = format;
		swapbytes = isSwapBytes;
		swap_channel = isSwapChannels;
		input_samplerate = samplerate;
		ignorewavlength = isIgnoreWavLength;
	}
}
