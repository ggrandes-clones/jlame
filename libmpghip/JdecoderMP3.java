package libmpghip;

public interface JdecoderMP3 {
	int decode(final Jmpstr_tag mp, final byte[] in, final int isize, final Object out, final int osize, final int[] done);
}
