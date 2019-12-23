package libmp3lame;

public final class Jmp3data_struct {
	/** 1 if header was parsed and following data was computed */
	public boolean header_parsed;
	/** number of channels */
	public int stereo;
	/** sample rate */
	public int samplerate;
	/** bitrate */
	public int bitrate;
	/** mp3 frame type */
	public int mode;
	/** mp3 frame type */
	public int mode_ext;
	/** number of samples per mp3 frame */
	public int framesize;

	/* this data is only computed if mpglib detects a Xing VBR header */
	/** number of samples in mp3 file. */
	public long nsamp;
	/** total number of frames in mp3 file */
	public int totalframes;

	/* this data is not currently computed by the mpglib routines */
	/** frames decoded counter */
	int framenum;
}
