package libmp3lame;

final class JPsyConst {
	final float window[] = new float[Jencoder.BLKSIZE];
	final float window_s[] = new float[Jencoder.BLKSIZE_s / 2];
	final JPsyConst_CB2SB l = new JPsyConst_CB2SB();
	final JPsyConst_CB2SB s = new JPsyConst_CB2SB();
	final JPsyConst_CB2SB l_to_s = new JPsyConst_CB2SB();
	final float attack_threshold[] = new float[4];
	float   decay;
	boolean force_short_block_calc;
}
