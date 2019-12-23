package libmp3lame;

final class JPsyStateVar {
	final float nb_l1[][] = new float[4][Jencoder.CBANDS];
	final float nb_l2[][] = new float[4][Jencoder.CBANDS];
	final float nb_s1[][] = new float[4][Jencoder.CBANDS];
	final float nb_s2[][] = new float[4][Jencoder.CBANDS];

	final JIII_psy_xmin thm[] = new JIII_psy_xmin[4];
	final JIII_psy_xmin en[] = new JIII_psy_xmin[4];

	/* loudness calculation (for adaptive threshold of hearing) */
	final float loudness_sq_save[] = new float[2]; /* account for granule delay of L3psycho_anal */

	final float tot_ener[] = new float[4];

	final float last_en_subshort[][] = new float[4][9];
	final int last_attacks[] = new int[4];

	final int blocktype_old[] = new int[2];
	//
	JPsyStateVar() {
		thm[0] = new JIII_psy_xmin();
		thm[1] = new JIII_psy_xmin();
		thm[2] = new JIII_psy_xmin();
		thm[3] = new JIII_psy_xmin();
		en[0] = new JIII_psy_xmin();
		en[1] = new JIII_psy_xmin();
		en[2] = new JIII_psy_xmin();
		en[3] = new JIII_psy_xmin();
	}
}
