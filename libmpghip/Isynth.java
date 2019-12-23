package libmpghip;

interface Isynth {
	/** java: pnt is sample counter, not byte counter */
	int synth_1to1_mono(Jmpstr_tag mp, float[] bandPtr, int boffset, Object out, int[] pnt);
	/** java: pnt is sample counter, not byte counter */
	int synth_1to1(Jmpstr_tag mp, float[] bandPtr, int boffset, int channel, Object out, int[] pnt);
}
